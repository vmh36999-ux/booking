package com.bookingplatform.property.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingplatform.property.infrastructure.seed.SeedRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// Nếu import dưới đây lỗi (Testcontainers 1.x), đổi thành: org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Chạy seed thật (profile "seed") với 60 khách sạn vào Postgres thật.
 * exit-when-done=false để tiến trình test không bị tắt; emit-events=true để
 * kiểm tra cả sự kiện.
 */
@SpringBootTest(properties = {
        "app.seed.count=60",
        "app.seed.exit-when-done=false",
        "app.seed.emit-events=true",
        "app.outbox.publisher.enabled=false"
})
@ActiveProfiles("seed")
@Testcontainers
class SeedRunnerIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    SeedRunner runner;

    @Autowired
    JdbcTemplate jdbc;

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void seedCreatesPublishedPropertiesWithRoomsAmenitiesAndEvents() {
        assertThat(count("SELECT count(*) FROM property")).isEqualTo(60);
        assertThat(count("SELECT count(*) FROM property WHERE status = 'PUBLISHED'")).isEqualTo(60);
        assertThat(count("SELECT count(DISTINCT city) FROM property")).isEqualTo(20);
        assertThat(count("SELECT count(*) FROM room_type")).isBetween(120, 300);
        assertThat(count("SELECT count(*) FROM property_amenity")).isGreaterThanOrEqualTo(180);
        assertThat(count("SELECT count(*) FROM outbox WHERE type = 'PropertyPublished'")).isEqualTo(60);
    }

    @Test
    void runningTheSeedAgainAddsNothing() {
        int propertiesBefore = count("SELECT count(*) FROM property");
        int eventsBefore = count("SELECT count(*) FROM outbox");

        assertThat(runner.seed()).isZero();

        assertThat(count("SELECT count(*) FROM property")).isEqualTo(propertiesBefore);
        assertThat(count("SELECT count(*) FROM outbox")).isEqualTo(eventsBefore);
    }
}
