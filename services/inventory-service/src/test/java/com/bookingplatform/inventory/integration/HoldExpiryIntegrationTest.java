package com.bookingplatform.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookingplatform.inventory.application.usecase.ConfirmHold;
import com.bookingplatform.inventory.application.usecase.ExpireHolds;
import com.bookingplatform.inventory.application.usecase.HoldRooms;
import com.bookingplatform.inventory.application.usecase.InitializeInventory;
import com.bookingplatform.inventory.domain.exception.SoldOutException;
import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldCommand;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
 * Kiểm tra việc cho hold quá hạn hết hạn, với PostgreSQL THẬT. Gọi thẳng use
 * case ExpireHolds (không chờ job chạy)
 * và "lùi giờ" bằng cách sửa expires_at về quá khứ. Job định kỳ bị tắt để không
 * chen vào giữa test.
 * Cần Docker đang chạy.
 */
@SpringBootTest(properties = { "spring.kafka.listener.auto-startup=false", "app.inventory.expiry.enabled=false" })
@Testcontainers
class HoldExpiryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ExpireHolds expireHolds;

    @Autowired
    HoldRooms holdRooms;

    @Autowired
    ConfirmHold confirmHold;

    @Autowired
    InitializeInventory initializeInventory;

    @Autowired
    JdbcTemplate jdbc;

    private final LocalDate checkIn = LocalDate.now(ZoneOffset.UTC).plusDays(10);

    private UUID roomTypeWith(int totalRooms) {
        UUID id = UUID.randomUUID();
        initializeInventory.handle(UUID.randomUUID(), id, totalRooms);
        return id;
    }

    private Hold hold(UUID roomType, int nights, int qty) {
        return holdRooms.execute(new HoldCommand(UUID.randomUUID(), roomType, checkIn, checkIn.plusDays(nights), qty))
                .hold();
    }

    /** "Lùi giờ": đặt hạn của hold về một phút trước. */
    private void makeOverdue(UUID holdId) {
        jdbc.update("UPDATE room_hold SET expires_at = now() - interval '1 minute' WHERE id = ?", holdId);
    }

    private String status(UUID holdId) {
        return jdbc.queryForObject("SELECT status FROM room_hold WHERE id = ?", String.class, holdId);
    }

    private int value(String column, UUID roomType, int dayOffset) {
        Integer v = jdbc.queryForObject("SELECT " + column + " FROM room_night WHERE room_type_id = ? AND night = ?",
                Integer.class, roomType, checkIn.plusDays(dayOffset));
        return v == null ? 0 : v;
    }

    @Test
    void overdueHoldsAreExpiredAndTheirRoomsReturnedWhileOthersAreLeftAlone() {
        UUID roomType = roomTypeWith(10);
        Hold overdue1 = hold(roomType, 2, 1);
        Hold overdue2 = hold(roomType, 2, 2);
        Hold stillValid = hold(roomType, 2, 3);
        makeOverdue(overdue1.id());
        makeOverdue(overdue2.id());

        int expired = expireHolds.execute();

        assertThat(expired).isEqualTo(2);
        assertThat(status(overdue1.id())).isEqualTo("EXPIRED");
        assertThat(status(overdue2.id())).isEqualTo("EXPIRED");
        assertThat(status(stillValid.id())).as("chưa đến hạn thì không đụng tới").isEqualTo("HELD");
        assertThat(value("held", roomType, 0)).as("chỉ còn phần của hold chưa hết hạn").isEqualTo(3);
        assertThat(value("held", roomType, 1)).isEqualTo(3);
    }

    @Test
    void expiredRoomsCanBeHeldByAnotherGuest() {
        UUID roomType = roomTypeWith(1);
        Hold first = hold(roomType, 1, 1);
        assertThatThrownBy(() -> hold(roomType, 1, 1)).isInstanceOf(SoldOutException.class);

        makeOverdue(first.id());
        expireHolds.execute();

        assertThat(hold(roomType, 1, 1).status().name()).as("phòng đã trả nên giữ lại được").isEqualTo("HELD");
    }

    @Test
    void runningTheJobTwiceIsHarmless() {
        UUID roomType = roomTypeWith(3);
        Hold hold = hold(roomType, 1, 2);
        makeOverdue(hold.id());

        assertThat(expireHolds.execute()).isEqualTo(1);
        assertThat(expireHolds.execute()).as("lần hai không còn gì để làm").isZero();

        assertThat(value("held", roomType, 0)).as("không trừ hai lần, không âm").isZero();
    }

    @Test
    void confirmedHoldsAreNeverExpiredEvenIfTheirOldDeadlinePassed() {
        UUID roomType = roomTypeWith(3);
        Hold hold = hold(roomType, 1, 2);
        confirmHold.execute(hold.id());
        makeOverdue(hold.id());

        assertThat(expireHolds.execute()).isZero();

        assertThat(status(hold.id())).isEqualTo("CONFIRMED");
        assertThat(value("booked", roomType, 0)).isEqualTo(2);
    }

    @Test
    void manyOverdueHoldsAreAllProcessed() {
        UUID roomType = roomTypeWith(30);
        for (int i = 0; i < 25; i++) {
            hold(roomType, 1, 1);
        }
        jdbc.update("UPDATE room_hold SET expires_at = now() - interval '1 minute' WHERE room_type_id = ?", roomType);

        assertThat(expireHolds.execute()).isEqualTo(25);

        assertThat(value("held", roomType, 0)).isZero();
        Integer stillHeld = jdbc.queryForObject(
                "SELECT count(*) FROM room_hold WHERE room_type_id = ? AND status = 'HELD'", Integer.class, roomType);
        assertThat(stillHeld).isZero();
    }
}
