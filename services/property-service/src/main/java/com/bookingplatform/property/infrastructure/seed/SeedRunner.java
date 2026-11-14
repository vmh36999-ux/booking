package com.bookingplatform.property.infrastructure.seed;

import com.bookingplatform.common.outbox.OutboxWriter;
import com.bookingplatform.events.EventEnvelope;
import com.bookingplatform.events.EventTypes;
import com.bookingplatform.events.Topics;
import com.bookingplatform.events.property.PropertyPublished;
import com.bookingplatform.events.property.PropertySnapshot;
import com.bookingplatform.events.property.RoomTypeData;
import com.bookingplatform.property.infrastructure.seed.SeedDataGenerator.SeedProperty;
import com.bookingplatform.property.infrastructure.seed.SeedDataGenerator.SeedRoomType;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Nạp dữ liệu mẫu (profile "seed"). Chạy lại nhiều lần an toàn: id cố định,
 * INSERT ... ON CONFLICT DO NOTHING.
 * Ghi theo lô 500 khách sạn mỗi transaction để nhanh và không giữ transaction
 * quá lâu.
 */
@Component
@Profile("seed")
public class SeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private static final int CHUNK = 500;

    private static final String PROPERTY_SQL = """
            INSERT INTO property (id, host_id, name, description, city, country, address, latitude, longitude,
                                  currency, status, created_at, updated_at, published_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """;
    private static final String ROOM_SQL = """
            INSERT INTO room_type (id, property_id, name, max_guests, bed_config, total_rooms, base_price_minor, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """;
    private static final String AMENITY_SQL = "INSERT INTO property_amenity (property_id, amenity_code) VALUES (?, ?) ON CONFLICT DO NOTHING";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectProvider<OutboxWriter> outbox;
    private final ConfigurableApplicationContext context;
    private final SeedDataGenerator generator = new SeedDataGenerator();
    private final int count;
    private final boolean emitEvents;
    private final boolean exitWhenDone;

    public SeedRunner(JdbcTemplate jdbc, TransactionTemplate transaction, ObjectProvider<OutboxWriter> outbox,
            ConfigurableApplicationContext context,
            @Value("${app.seed.count:10000}") int count,
            @Value("${app.seed.emit-events:false}") boolean emitEvents,
            @Value("${app.seed.exit-when-done:true}") boolean exitWhenDone) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.outbox = outbox;
        this.context = context;
        this.count = count;
        this.emitEvents = emitEvents;
        this.exitWhenDone = exitWhenDone;
    }

    @Override
    public void run(String... args) {
        int inserted = seed();
        log.info("Seed xong: thêm mới {} khách sạn (tổng yêu cầu {}).", inserted, count);
        if (exitWhenDone) {
            System.exit(SpringApplication.exit(context));
        }
    }

    /** Trả về số khách sạn MỚI được thêm (0 nếu đã có đủ từ lần chạy trước). */
    public int seed() {
        if (emitEvents && outbox.getIfAvailable() == null) {
            throw new IllegalStateException("app.seed.emit-events=true cần app.outbox.enabled=true");
        }
        int total = 0;
        for (int from = 0; from < count; from += CHUNK) {
            List<SeedProperty> chunk = new ArrayList<>();
            for (int i = from; i < Math.min(from + CHUNK, count); i++) {
                chunk.add(generator.generate(i));
            }
            Integer added = transaction.execute(status -> seedChunk(chunk));
            total += added == null ? 0 : added;
            log.info("Seed: đã xử lý {}/{} khách sạn", Math.min(from + CHUNK, count), count);
        }
        return total;
    }

    private int seedChunk(List<SeedProperty> chunk) {
        List<Object[]> propertyArgs = new ArrayList<>();
        for (SeedProperty p : chunk) {
            Timestamp created = Timestamp.from(p.createdAt());
            propertyArgs.add(new Object[] { p.id(), p.hostId(), p.name(), p.description(), p.city(), p.country(),
                    p.address(), p.latitude(), p.longitude(), p.currency(), created, created, created });
        }
        // Với ON CONFLICT DO NOTHING, mỗi dòng trả về 1 nếu thêm mới, 0 nếu đã có
        int[] inserted = jdbc.batchUpdate(PROPERTY_SQL, propertyArgs);

        List<SeedProperty> fresh = new ArrayList<>();
        for (int i = 0; i < chunk.size(); i++) {
            if (inserted[i] > 0) {
                fresh.add(chunk.get(i));
            }
        }
        if (fresh.isEmpty()) {
            return 0;
        }

        List<Object[]> roomArgs = new ArrayList<>();
        List<Object[]> amenityArgs = new ArrayList<>();
        for (SeedProperty p : fresh) {
            Timestamp created = Timestamp.from(p.createdAt());
            for (SeedRoomType r : p.roomTypes()) {
                roomArgs.add(new Object[] { r.id(), p.id(), r.name(), r.maxGuests(), r.bedConfig(), r.totalRooms(),
                        r.basePriceMinor(), created });
            }
            for (String code : p.amenities()) {
                amenityArgs.add(new Object[] { p.id(), code });
            }
        }
        jdbc.batchUpdate(ROOM_SQL, roomArgs);
        jdbc.batchUpdate(AMENITY_SQL, amenityArgs);

        if (emitEvents) {
            OutboxWriter writer = outbox.getObject();
            for (SeedProperty p : fresh) {
                writer.write(Topics.PROPERTY_EVENTS,
                        EventEnvelope.of(EventTypes.PROPERTY_PUBLISHED, p.id().toString(), null,
                                new PropertyPublished(snapshot(p))));
            }
        }
        return fresh.size();
    }

    private static PropertySnapshot snapshot(SeedProperty p) {
        List<RoomTypeData> rooms = p.roomTypes().stream()
                .map(r -> new RoomTypeData(r.id(), r.name(), r.maxGuests(), r.bedConfig(), r.totalRooms(),
                        r.basePriceMinor()))
                .toList();
        int maxGuests = p.roomTypes().stream().mapToInt(SeedRoomType::maxGuests).max().orElse(0);
        long priceFrom = p.roomTypes().stream().mapToLong(SeedRoomType::basePriceMinor).min().orElse(0L);
        return new PropertySnapshot(p.id(), p.hostId(), p.name(), p.description(), p.city(), p.country(), p.address(),
                p.latitude(), p.longitude(), p.amenities(), maxGuests, priceFrom, p.currency(), rooms);
    }
}
