package com.bookingplatform.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingplatform.inventory.application.usecase.HoldRooms;
import com.bookingplatform.inventory.application.usecase.InitializeInventory;
import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldCommand;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.awaitility.Awaitility;
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
 * Kiểm tra JOB ĐỊNH KỲ thật sự tự chạy và dùng ShedLock. Ở đây job BẬT, chạy
 * mỗi 1 giây
 * (thay vì 1 phút) và không giữ khóa thừa (lock-at-least-for = 0) để test không
 * phải chờ lâu.
 * Cần Docker đang chạy.
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "app.inventory.expiry.enabled=true",
        "app.inventory.expiry.interval=PT1S",
        "app.inventory.expiry.lock-at-least-for=PT0S" })
@Testcontainers
class HoldExpiryJobIntegrationTest {

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
    InitializeInventory initializeInventory;

    @Autowired
    LockProvider lockProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void theScheduledJobExpiresOverdueHoldsByItselfAndRecordsItsLock() {
        UUID roomType = UUID.randomUUID();
        initializeInventory.handle(UUID.randomUUID(), roomType, 3);
        LocalDate checkIn = LocalDate.now(ZoneOffset.UTC).plusDays(10);
        Hold hold = holdRooms.execute(new HoldCommand(UUID.randomUUID(), roomType, checkIn, checkIn.plusDays(2), 2))
                .hold();

        jdbc.update("UPDATE room_hold SET expires_at = now() - interval '1 minute' WHERE id = ?", hold.id());

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            String status = jdbc.queryForObject("SELECT status FROM room_hold WHERE id = ?", String.class, hold.id());
            assertThat(status).as("job tự cho hold hết hạn, không ai gọi tay").isEqualTo("EXPIRED");
        });

        Integer held = jdbc.queryForObject(
                "SELECT held FROM room_night WHERE room_type_id = ? AND night = ?", Integer.class, roomType, checkIn);
        assertThat(held).as("phòng đã được trả về").isZero();

        Integer locks = jdbc.queryForObject(
                "SELECT count(*) FROM shedlock WHERE name = 'inventory-hold-expiry'", Integer.class);
        assertThat(locks).as("ShedLock đã ghi dòng khóa của job").isEqualTo(1);
    }

    @Test
    void onlyOneNodeCanHoldTheLockAtATime() {
        String name = "test-lock-" + UUID.randomUUID();
        LockConfiguration config = new LockConfiguration(Instant.now(), name, Duration.ofMinutes(1), Duration.ZERO);

        Optional<SimpleLock> first = lockProvider.lock(config);
        assertThat(first).as("bản đầu tiên lấy được khóa").isPresent();

        assertThat(lockProvider.lock(config)).as("bản thứ hai (cùng tên khóa) không lấy được").isEmpty();

        first.get().unlock();
        Optional<SimpleLock> third = lockProvider.lock(config);
        assertThat(third).as("nhả khóa rồi thì bản khác lấy được").isPresent();
        third.get().unlock();
    }
}
