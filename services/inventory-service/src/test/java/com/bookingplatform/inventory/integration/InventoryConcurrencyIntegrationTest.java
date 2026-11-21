package com.bookingplatform.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingplatform.inventory.application.usecase.ConfirmHold;
import com.bookingplatform.inventory.application.usecase.HoldRooms;
import com.bookingplatform.inventory.application.usecase.InitializeInventory;
import com.bookingplatform.inventory.application.usecase.ReleaseHold;
import com.bookingplatform.inventory.domain.exception.HoldConflictException;
import com.bookingplatform.inventory.domain.exception.SoldOutException;
import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldCommand;
import com.bookingplatform.inventory.domain.model.HoldResult;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// Nếu import dưới đây lỗi (Testcontainers 1.x), đổi thành: org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * BỘ TEST SONG SONG: chứng minh với PostgreSQL THẬT rằng không bao giờ bán quá
 * số phòng, kể cả khi 100 luồng
 * cùng tranh nhau, kể cả khi các khoảng ngày chồng lấn (nơi deadlock có thể xảy
 * ra nếu khóa sai thứ tự).
 *
 * Mỗi bài được lặp nhiều lần (@RepeatedTest) vì lỗi tranh chấp thường CHỈ xuất
 * hiện thỉnh thoảng.
 * Cần Docker đang chạy. Job hết hạn và Kafka bị tắt.
 */
@SpringBootTest(properties = { "spring.kafka.listener.auto-startup=false", "app.inventory.expiry.enabled=false" })
@Testcontainers
class InventoryConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    HoldRooms holdRooms;

    @Autowired
    ConfirmHold confirmHold;

    @Autowired
    ReleaseHold releaseHold;

    @Autowired
    InitializeInventory initializeInventory;

    @Autowired
    JdbcTemplate jdbc;

    /**
     * Kết quả của một luồng: loại (SUCCESS, SOLD_OUT, ERROR...), khoảng đêm đã thử,
     * chi tiết lỗi nếu có.
     */
    private record Outcome(String kind, int start, int end, String detail) {
    }

    private final LocalDate day0 = LocalDate.now(ZoneOffset.UTC).plusDays(10);

    // ----------------------------------------------------------------- tiện ích

    private UUID roomTypeWith(int totalRooms) {
        UUID id = UUID.randomUUID();
        initializeInventory.handle(UUID.randomUUID(), id, totalRooms);
        return id;
    }

    /**
     * Chạy "threads" công việc ĐỒNG THỜI: mọi luồng được tạo sẵn, đứng chờ ở vạch
     * xuất phát, rồi cùng chạy một lúc.
     * Nếu không có vạch xuất phát, các luồng chạy nối đuôi nhau và không thật sự
     * tranh chấp.
     */
    private <T> List<T> runTogether(int threads, IntFunction<Callable<T>> job) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<T> work = job.apply(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return work.call();
            }));
        }
        ready.await();
        go.countDown();

        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(120, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    private Outcome attemptHold(UUID roomType, int start, int end, int qty) {
        try {
            holdRooms.execute(
                    new HoldCommand(UUID.randomUUID(), roomType, day0.plusDays(start), day0.plusDays(end), qty));
            return new Outcome("SUCCESS", start, end, "");
        } catch (SoldOutException e) {
            return new Outcome("SOLD_OUT", start, end, "");
        } catch (Exception e) {
            return new Outcome("ERROR", start, end, e.toString());
        }
    }

    private static long count(List<Outcome> outcomes, String kind) {
        return outcomes.stream().filter(o -> o.kind().equals(kind)).count();
    }

    private static List<String> errors(List<Outcome> outcomes) {
        return outcomes.stream().filter(o -> o.kind().equals("ERROR")).map(Outcome::detail).toList();
    }

    private int value(String column, UUID roomType, int dayOffset) {
        Integer v = jdbc.queryForObject("SELECT " + column + " FROM room_night WHERE room_type_id = ? AND night = ?",
                Integer.class, roomType, day0.plusDays(dayOffset));
        return v == null ? 0 : v;
    }

    private int held(UUID roomType, int dayOffset) {
        return value("held", roomType, dayOffset);
    }

    private int booked(UUID roomType, int dayOffset) {
        return value("booked", roomType, dayOffset);
    }

    private int violations() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM room_night WHERE held < 0 OR booked < 0 OR held + booked > total", Integer.class);
        return n == null ? 0 : n;
    }

    // ----------------------------------------------------------------- các bài
    // test

    @RepeatedTest(value = 10, name = "{displayName} {currentRepetition}/{totalRepetitions}")
    void hundredThreadsFightForTheLastRoom() throws Exception {
        UUID roomType = roomTypeWith(1);

        List<Outcome> outcomes = runTogether(100, i -> () -> attemptHold(roomType, 0, 1, 1));

        assertThat(errors(outcomes)).as("không có lỗi bất ngờ (deadlock, timeout...)").isEmpty();
        assertThat(count(outcomes, "SUCCESS")).as("ĐÚNG 1 luồng giữ được phòng cuối").isEqualTo(1);
        assertThat(count(outcomes, "SOLD_OUT")).as("99 luồng còn lại nhận hết phòng").isEqualTo(99);
        assertThat(held(roomType, 0)).isEqualTo(1);
        assertThat(violations()).isZero();
    }

    @RepeatedTest(value = 5, name = "{displayName} {currentRepetition}/{totalRepetitions}")
    void hundredThreadsOnOverlappingStaysNeverOversellAndNeverDeadlock() throws Exception {
        UUID roomType = roomTypeWith(3);
        int nightsInPlay = 7;

        List<Outcome> outcomes = runTogether(100, i -> () -> {
            Random random = new Random(1_000L + i); // mỗi luồng một khoảng ngày riêng nhưng cố định
            int start = random.nextInt(nightsInPlay);
            int end = Math.min(start + 1 + random.nextInt(4), nightsInPlay); // ở 1 đến 4 đêm, chồng lấn lẫn nhau
            return attemptHold(roomType, start, end, 1);
        });

        assertThat(errors(outcomes)).as("không có lỗi bất ngờ (deadlock...)").isEmpty();

        // Số phòng đang giữ ở mỗi đêm PHẢI bằng đúng số lần giữ thành công có phủ đêm
        // đó: không mất, không thừa
        int[] expected = new int[nightsInPlay];
        for (Outcome outcome : outcomes) {
            if (outcome.kind().equals("SUCCESS")) {
                for (int night = outcome.start(); night < outcome.end(); night++) {
                    expected[night]++;
                }
            }
        }
        for (int night = 0; night < nightsInPlay; night++) {
            assertThat(expected[night]).as("không đêm nào bán quá 3 phòng: đêm " + night).isLessThanOrEqualTo(3);
            assertThat(held(roomType, night)).as("held thực tế của đêm " + night).isEqualTo(expected[night]);
        }
        assertThat(count(outcomes, "SUCCESS")).as("có tranh chấp thật: một số luồng phải bị từ chối").isLessThan(100);
        assertThat(violations()).isZero();
    }

    @RepeatedTest(value = 5, name = "{displayName} {currentRepetition}/{totalRepetitions}")
    void concurrentConfirmAndReleaseOfOneHoldEndInExactlyOneFinalState() throws Exception {
        UUID roomType = roomTypeWith(2);
        Hold hold = holdRooms.execute(new HoldCommand(UUID.randomUUID(), roomType, day0, day0.plusDays(2), 1)).hold();

        List<Outcome> outcomes = runTogether(20, i -> () -> {
            try {
                if (i % 2 == 0) {
                    confirmHold.execute(hold.id());
                } else {
                    releaseHold.execute(hold.id());
                }
                return new Outcome("OK", 0, 0, "");
            } catch (HoldConflictException e) {
                return new Outcome("CONFLICT", 0, 0, e.getCode());
            } catch (Exception e) {
                return new Outcome("ERROR", 0, 0, e.toString());
            }
        });

        assertThat(errors(outcomes)).isEmpty();
        String status = jdbc.queryForObject("SELECT status FROM room_hold WHERE id = ?", String.class, hold.id());
        assertThat(status).as("hold kết thúc ở ĐÚNG MỘT trạng thái cuối").isIn("CONFIRMED", "RELEASED");
        for (int night = 0; night < 2; night++) {
            assertThat(held(roomType, night)).as("không còn phòng nào kẹt ở 'đang giữ'").isZero();
            assertThat(booked(roomType, night)).isEqualTo("CONFIRMED".equals(status) ? 1 : 0);
        }
        assertThat(violations()).isZero();
    }

    @RepeatedTest(value = 5, name = "{displayName} {currentRepetition}/{totalRepetitions}")
    void manyThreadsWithTheSameBookingIdHoldOnlyOnce() throws Exception {
        UUID roomType = roomTypeWith(5);
        HoldCommand command = new HoldCommand(UUID.randomUUID(), roomType, day0, day0.plusDays(2), 2);

        List<HoldResult> results = runTogether(20, i -> () -> holdRooms.execute(command));

        assertThat(results.stream().filter(HoldResult::created).count()).as("chỉ MỘT luồng tạo mới").isEqualTo(1);
        assertThat(results.stream().map(r -> r.hold().id()).distinct().count()).as("tất cả nhận đúng một hold")
                .isEqualTo(1);
        assertThat(held(roomType, 0)).as("chỉ giữ một lần").isEqualTo(2);
        assertThat(held(roomType, 1)).isEqualTo(2);
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM room_hold WHERE booking_id = ?", Integer.class,
                command.bookingId());
        assertThat(rows).isEqualTo(1);
    }
}
