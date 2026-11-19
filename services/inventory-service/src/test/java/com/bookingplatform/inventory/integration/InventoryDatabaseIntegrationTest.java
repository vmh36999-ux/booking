package com.bookingplatform.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookingplatform.inventory.application.usecase.ConfirmHold;
import com.bookingplatform.inventory.application.usecase.HoldRooms;
import com.bookingplatform.inventory.application.usecase.InitializeInventory;
import com.bookingplatform.inventory.application.usecase.ReleaseHold;
import com.bookingplatform.inventory.domain.exception.HoldConflictException;
import com.bookingplatform.inventory.domain.exception.NoInventoryException;
import com.bookingplatform.inventory.domain.exception.SoldOutException;
import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldCommand;
import com.bookingplatform.inventory.domain.model.HoldResult;
import com.bookingplatform.inventory.domain.model.HoldStatus;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// Nếu import dưới đây lỗi (Testcontainers 1.x), đổi thành: org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Kiểm tra câu UPDATE giữ chỗ với PostgreSQL THẬT: không mock gì cả.
 * Đây là bài test quan trọng nhất của tuần: chứng minh không bán quá số phòng.
 * Cần Docker đang chạy. Kafka bị tắt (auto-startup=false) vì bài này không cần.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class InventoryDatabaseIntegrationTest {

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

    private final LocalDate checkIn = LocalDate.now(ZoneOffset.UTC).plusDays(10);

    // ----------------------------------------------------------------- tiện ích

    private UUID roomTypeWith(int totalRooms) {
        UUID id = UUID.randomUUID();
        initializeInventory.handle(UUID.randomUUID(), id, totalRooms);
        return id;
    }

    private HoldCommand command(UUID roomType, int nights, int qty) {
        return new HoldCommand(UUID.randomUUID(), roomType, checkIn, checkIn.plusDays(nights), qty);
    }

    /** Giá trị của một cột ở đêm thứ "dayOffset" kể từ ngày nhận phòng. */
    private int value(String column, UUID roomType, int dayOffset) {
        Integer v = jdbc.queryForObject("SELECT " + column + " FROM room_night WHERE room_type_id = ? AND night = ?",
                Integer.class, roomType, checkIn.plusDays(dayOffset));
        return v == null ? 0 : v;
    }

    private int held(UUID roomType, int dayOffset) {
        return value("held", roomType, dayOffset);
    }

    private int booked(UUID roomType, int dayOffset) {
        return value("booked", roomType, dayOffset);
    }

    /** Số dòng vi phạm quy tắc: số âm hoặc bán quá số phòng. Luôn phải là 0. */
    private int violations() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM room_night WHERE held < 0 OR booked < 0 OR held + booked > total", Integer.class);
        return n == null ? 0 : n;
    }

    private String status(UUID holdId) {
        return jdbc.queryForObject("SELECT status FROM room_hold WHERE id = ?", String.class, holdId);
    }

    // ----------------------------------------------------------------- tạo tồn kho

    @Test
    void initializeCreatesTheWholeHorizonAndNeverOverwrites() {
        UUID roomType = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        assertThat(initializeInventory.handle(eventId, roomType, 3)).isTrue();
        assertThat(initializeInventory.handle(eventId, roomType, 3)).as("cùng eventId: bị bỏ qua").isFalse();
        assertThat(initializeInventory.handle(UUID.randomUUID(), roomType, 99)).as("sự kiện khác nhưng đêm đã có")
                .isTrue();

        Integer nights = jdbc.queryForObject("SELECT count(*) FROM room_night WHERE room_type_id = ?", Integer.class,
                roomType);
        Integer distinctTotals = jdbc.queryForObject(
                "SELECT count(DISTINCT total) FROM room_night WHERE room_type_id = ?", Integer.class, roomType);
        Integer total = jdbc.queryForObject("SELECT max(total) FROM room_night WHERE room_type_id = ?", Integer.class,
                roomType);
        assertThat(nights).isEqualTo(365);
        assertThat(distinctTotals).isEqualTo(1);
        assertThat(total).as("không ghi đè số phòng đã có").isEqualTo(3);
    }

    // ----------------------------------------------------------------- giữ chỗ

    @Test
    void holdIncreasesHeldOnEveryRequestedNightAndOnlyThose() {
        UUID roomType = roomTypeWith(5);

        HoldResult result = holdRooms.execute(command(roomType, 3, 2));

        assertThat(result.created()).isTrue();
        assertThat(status(result.hold().id())).isEqualTo("HELD");
        assertThat(held(roomType, 0)).isEqualTo(2);
        assertThat(held(roomType, 1)).isEqualTo(2);
        assertThat(held(roomType, 2)).isEqualTo(2);
        assertThat(held(roomType, 3)).as("đêm trả phòng không bị giữ").isZero();
        assertThat(held(roomType, -1)).isZero();
    }

    @Test
    void lastRoomsCanBeHeldButNotOneMoreAndNothingGoesNegative() {
        UUID roomType = roomTypeWith(2);

        holdRooms.execute(command(roomType, 1, 2));

        assertThatThrownBy(() -> holdRooms.execute(command(roomType, 1, 1))).isInstanceOf(SoldOutException.class);
        assertThat(held(roomType, 0)).isEqualTo(2);
        assertThat(violations()).isZero();
    }

    @Test
    void soldOutOnOneNightLeavesEveryNightUntouchedAndStoresNoHold() {
        UUID roomType = roomTypeWith(2);
        // Đêm số 1 đã hết phòng
        holdRooms.execute(new HoldCommand(UUID.randomUUID(), roomType, checkIn.plusDays(1), checkIn.plusDays(2), 2));
        HoldCommand threeNights = command(roomType, 3, 1);

        assertThatThrownBy(() -> holdRooms.execute(threeNights)).isInstanceOf(SoldOutException.class);

        assertThat(held(roomType, 0)).as("đêm 0 không bị đụng tới").isZero();
        assertThat(held(roomType, 1)).isEqualTo(2);
        assertThat(held(roomType, 2)).as("đêm 2 không bị đụng tới").isZero();
        Integer holdRows = jdbc.queryForObject(
                "SELECT count(*) FROM room_hold WHERE booking_id = ?", Integer.class, threeNights.bookingId());
        assertThat(holdRows).as("đơn bị từ chối không để lại lần giữ chỗ nào").isZero();
    }

    @Test
    void sameBookingNeverHoldsTwice() {
        UUID roomType = roomTypeWith(5);
        HoldCommand command = command(roomType, 2, 2);

        HoldResult first = holdRooms.execute(command);
        HoldResult second = holdRooms.execute(command);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.hold().id()).isEqualTo(first.hold().id());
        assertThat(held(roomType, 0)).as("chỉ giữ một lần").isEqualTo(2);
    }

    @Test
    void sameBookingWithADifferentRequestIsRejected() {
        UUID roomType = roomTypeWith(5);
        HoldCommand original = command(roomType, 2, 2);
        holdRooms.execute(original);

        HoldCommand changed = new HoldCommand(original.bookingId(), roomType, original.checkIn(), original.checkOut(),
                4);

        assertThatThrownBy(() -> holdRooms.execute(changed))
                .isInstanceOfSatisfying(HoldConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HOLD_MISMATCH"));
        assertThat(held(roomType, 0)).isEqualTo(2);
    }

    @Test
    void datesWithoutInventoryAreReportedAsNoInventoryNotSoldOut() {
        UUID roomType = roomTypeWith(5);
        LocalDate farAway = LocalDate.now(ZoneOffset.UTC).plusDays(400);

        assertThatThrownBy(
                () -> holdRooms.execute(new HoldCommand(UUID.randomUUID(), roomType, farAway, farAway.plusDays(2), 1)))
                .isInstanceOf(NoInventoryException.class);
    }

    // ----------------------------------------------------------------- xác nhận và
    // nhả

    @Test
    void confirmMovesRoomsFromHeldToBookedAndIsIdempotent() {
        UUID roomType = roomTypeWith(3);
        Hold hold = holdRooms.execute(command(roomType, 2, 2)).hold();

        assertThat(confirmHold.execute(hold.id()).status()).isEqualTo(HoldStatus.CONFIRMED);
        assertThat(confirmHold.execute(hold.id()).status()).as("gọi lần hai: cùng kết quả")
                .isEqualTo(HoldStatus.CONFIRMED);

        assertThat(held(roomType, 0)).isZero();
        assertThat(booked(roomType, 0)).as("chỉ chuyển một lần, không cộng đôi").isEqualTo(2);
        assertThat(booked(roomType, 1)).isEqualTo(2);
        assertThat(status(hold.id())).isEqualTo("CONFIRMED");
        assertThat(violations()).isZero();
    }

    @Test
    void releaseGivesRoomsBackIsIdempotentAndTheRoomsCanBeHeldAgain() {
        UUID roomType = roomTypeWith(2);
        Hold hold = holdRooms.execute(command(roomType, 1, 2)).hold();

        assertThat(releaseHold.execute(hold.id()).status()).isEqualTo(HoldStatus.RELEASED);
        assertThat(releaseHold.execute(hold.id()).status()).as("gọi lần hai: cùng kết quả")
                .isEqualTo(HoldStatus.RELEASED);

        assertThat(held(roomType, 0)).as("không trừ hai lần, không âm").isZero();
        assertThat(holdRooms.execute(command(roomType, 1, 2)).created()).as("phòng đã trả nên giữ lại được").isTrue();
        assertThat(violations()).isZero();
    }

    @Test
    void aConfirmedHoldCannotBeReleasedAndAReleasedHoldCannotBeConfirmed() {
        UUID roomType = roomTypeWith(3);
        Hold confirmed = holdRooms.execute(command(roomType, 1, 1)).hold();
        confirmHold.execute(confirmed.id());
        Hold released = holdRooms.execute(command(roomType, 1, 1)).hold();
        releaseHold.execute(released.id());

        assertThatThrownBy(() -> releaseHold.execute(confirmed.id()))
                .isInstanceOfSatisfying(HoldConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HOLD_ALREADY_CONFIRMED"));
        assertThatThrownBy(() -> confirmHold.execute(released.id()))
                .isInstanceOfSatisfying(HoldConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HOLD_NOT_ACTIVE"));

        assertThat(booked(roomType, 0)).isEqualTo(1);
        assertThat(held(roomType, 0)).isZero();
    }

    @Test
    void confirmingAnExpiredHoldReturnsItsRoomsAndMarksItExpired() {
        UUID roomType = roomTypeWith(3);
        Hold hold = holdRooms.execute(command(roomType, 2, 2)).hold();
        jdbc.update("UPDATE room_hold SET expires_at = now() - interval '1 minute' WHERE id = ?", hold.id());

        assertThatThrownBy(() -> confirmHold.execute(hold.id()))
                .isInstanceOfSatisfying(HoldConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HOLD_EXPIRED"));

        assertThat(status(hold.id())).as("việc trả phòng được giữ lại dù có lỗi (noRollbackFor)").isEqualTo("EXPIRED");
        assertThat(held(roomType, 0)).isZero();
        assertThat(held(roomType, 1)).isZero();
        assertThat(booked(roomType, 0)).isZero();
    }

    // ----------------------------------------------------------------- lưới an
    // toàn và song song

    @Test
    void theDatabaseConstraintIsTheLastSafetyNet() {
        UUID roomType = roomTypeWith(2);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE room_night SET held = total + 1 WHERE room_type_id = ? AND night = ?", roomType, checkIn))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE room_night SET held = -1 WHERE room_type_id = ? AND night = ?", roomType, checkIn))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(violations()).isZero();
    }

    @Test
    void twentyConcurrentHoldsForFiveRoomsNeverOversell() throws Exception {
        UUID roomType = roomTypeWith(5);
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await(); // tất cả cùng xuất phát một lúc
                try {
                    holdRooms.execute(command(roomType, 1, 1));
                    return true;
                } catch (SoldOutException e) {
                    return false;
                }
            }));
        }
        ready.await();
        go.countDown();

        int success = 0;
        for (Future<Boolean> result : results) {
            if (result.get(60, TimeUnit.SECONDS)) {
                success++;
            }
        }
        pool.shutdown();

        assertThat(success).as("đúng 5 người giữ được 5 phòng").isEqualTo(5);
        assertThat(held(roomType, 0)).isEqualTo(5);
        assertThat(violations()).isZero();
    }
}
