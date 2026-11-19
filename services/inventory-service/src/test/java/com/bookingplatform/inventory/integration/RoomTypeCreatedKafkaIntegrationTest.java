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
// Nếu hai import dưới đây lỗi (Testcontainers 1.x), xem hướng dẫn: đổi sang org.testcontainers.containers.PostgreSQLContainer
// và org.testcontainers.containers.KafkaContainer (hoặc org.testcontainers.kafka.ConfluentKafkaContainer)
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Chạy inventory-service thật với Postgres và Kafka thật: gửi sự kiện
 * RoomTypeCreated lên topic property.events
 * và kiểm tra tồn kho 365 đêm xuất hiện đúng MỘT lần dù sự kiện bị giao hai
 * lần. Cần Docker đang chạy.
 */
@SpringBootTest
@Testcontainers
class RoomTypeCreatedKafkaIntegrationTest {

    /** Cùng key thì cùng partition, nên giữ đúng thứ tự giao. */
    private static final String SAME_KEY = "cung-partition";

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

    private static String envelope(UUID eventId, String type, UUID roomTypeId, int totalRooms) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"" + type + "\",\"version\":1,"
                + "\"occurredAt\":\"" + Instant.now() + "\",\"aggregateId\":\"" + UUID.randomUUID() + "\","
                + "\"correlationId\":null,\"data\":{\"propertyId\":\"" + UUID.randomUUID() + "\","
                + "\"roomTypeId\":\"" + roomTypeId + "\",\"name\":\"Deluxe\",\"maxGuests\":2,"
                + "\"bedConfig\":\"1 giuong doi\",\"totalRooms\":" + totalRooms + ",\"basePriceMinor\":1200000,"
                + "\"currency\":\"VND\"}}";
    }

    private void send(String json) throws Exception {
        kafka.send("property.events", SAME_KEY, json).get();
    }

    private int nightCount(UUID roomTypeId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM room_night WHERE room_type_id = ?", Integer.class,
                roomTypeId);
        return n == null ? 0 : n;
    }

    private void awaitInventory(UUID roomTypeId) {
        Awaitility.await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(nightCount(roomTypeId)).isEqualTo(365));
    }

    @Test
    void roomTypeCreatedBuildsInventoryExactlyOnceEvenWhenDeliveredTwice() throws Exception {
        UUID roomType = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID sentinelRoomType = UUID.randomUUID();

        send(envelope(eventId, "RoomTypeCreated", roomType, 7));
        awaitInventory(roomType);

        send(envelope(eventId, "RoomTypeCreated", roomType, 99)); // trùng eventId, số phòng khác
        send(envelope(UUID.randomUUID(), "RoomTypeCreated", sentinelRoomType, 3)); // "cột mốc"
        awaitInventory(sentinelRoomType); // cột mốc xử lý xong nghĩa là bản trùng chắc chắn đã được xử lý

        Integer totals = jdbc.queryForObject(
                "SELECT count(DISTINCT total) FROM room_night WHERE room_type_id = ?", Integer.class, roomType);
        Integer total = jdbc.queryForObject(
                "SELECT max(total) FROM room_night WHERE room_type_id = ?", Integer.class, roomType);
        Integer processed = jdbc.queryForObject(
                "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, eventId);
        assertThat(nightCount(roomType)).isEqualTo(365);
        assertThat(totals).isEqualTo(1);
        assertThat(total).as("bản trùng không ghi đè số phòng").isEqualTo(7);
        assertThat(processed).isEqualTo(1);
    }

    @Test
    void otherEventTypesOnTheTopicAreIgnoredAndDoNotBlockTheQueue() throws Exception {
        UUID ignoredRoomType = UUID.randomUUID();
        UUID roomType = UUID.randomUUID();

        send(envelope(UUID.randomUUID(), "PropertyPublished", ignoredRoomType, 5));
        send(envelope(UUID.randomUUID(), "RoomTypeCreated", roomType, 4));
        awaitInventory(roomType);

        assertThat(nightCount(ignoredRoomType)).isZero();
    }
}
