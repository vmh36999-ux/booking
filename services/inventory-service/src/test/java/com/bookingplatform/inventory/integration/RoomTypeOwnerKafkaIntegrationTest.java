package com.bookingplatform.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// Nếu import dưới đây lỗi (Testcontainers 1.x), đổi thành: org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Dùng Kafka thật: RoomTypeCreated có hostId thì inventory ghi nhớ chủ loại
 * phòng; sự kiện CŨ không có hostId
 * vẫn tạo được tồn kho nhưng chưa có chủ. Cần Docker đang chạy.
 */
@SpringBootTest(properties = "app.inventory.expiry.enabled=false")
@Testcontainers
class RoomTypeOwnerKafkaIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.0");

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    private static String event(UUID roomTypeId, UUID propertyId, UUID hostId) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"RoomTypeCreated\",\"version\":1,"
                + "\"occurredAt\":\"" + Instant.now() + "\",\"aggregateId\":\"" + propertyId + "\","
                + "\"correlationId\":null,\"data\":{\"propertyId\":\"" + propertyId + "\","
                + (hostId == null ? "" : "\"hostId\":\"" + hostId + "\",")
                + "\"roomTypeId\":\"" + roomTypeId + "\",\"name\":\"Deluxe\",\"maxGuests\":2,"
                + "\"bedConfig\":\"1 giuong doi\",\"totalRooms\":6,\"basePriceMinor\":1200000,\"currency\":\"VND\"}}";
    }

    private int nights(UUID roomTypeId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM room_night WHERE room_type_id = ?", Integer.class,
                roomTypeId);
        return n == null ? 0 : n;
    }

    @Test
    void theOwnerIsRecordedWhenTheEventHasAHostIdAndSkippedWhenItDoesNot() throws Exception {
        UUID withHost = UUID.randomUUID();
        UUID host = UUID.randomUUID();
        UUID property = UUID.randomUUID();
        UUID withoutHost = UUID.randomUUID();

        kafka.send("property.events", "cung-partition", event(withHost, property, host)).get();
        kafka.send("property.events", "cung-partition", event(withoutHost, property, null)).get();

        Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertThat(nights(withHost)).isEqualTo(365);
            assertThat(nights(withoutHost)).isEqualTo(365);
        });

        UUID recorded = jdbc.queryForObject(
                "SELECT host_id FROM room_type_owner WHERE room_type_id = ?", UUID.class, withHost);
        Integer ownersForOldEvent = jdbc.queryForObject(
                "SELECT count(*) FROM room_type_owner WHERE room_type_id = ?", Integer.class, withoutHost);
        assertThat(recorded).isEqualTo(host);
        assertThat(ownersForOldEvent).as("sự kiện cũ không có hostId: chưa ghi chủ").isZero();
    }
}
