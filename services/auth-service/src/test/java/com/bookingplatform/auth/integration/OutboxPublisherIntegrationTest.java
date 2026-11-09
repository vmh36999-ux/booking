package com.bookingplatform.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// Nếu hai import dưới đây lỗi (Testcontainers 1.x), xem hướng dẫn: đổi sang org.testcontainers.containers.PostgreSQLContainer
// và org.testcontainers.containers.KafkaContainer (hoặc org.testcontainers.kafka.ConfluentKafkaContainer)
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Kiểm tra chặng cuối của outbox: đăng ký xong thì sự kiện THẬT SỰ xuất hiện
 * trên Kafka,
 * và dòng outbox được đánh dấu đã gửi. Cần Docker đang chạy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OutboxPublisherIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void registeredUserEventReachesKafkaAndIsMarkedPublished() throws Exception {
        String email = "outbox.kafka@example.com";
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"Passw0rd!2026\",\"accountType\":\"GUEST\"}"))
                .build();
        HttpResponse<String> registered = http.send(request, BodyHandlers.ofString());
        assertThat(registered.statusCode()).isEqualTo(201);

        Matcher m = Pattern.compile("\"userId\"\\s*:\\s*\"([^\"]+)\"").matcher(registered.body());
        assertThat(m.find()).isTrue();
        String userId = m.group(1);

        // Đợi OutboxPublisher gửi xong: published_at được đặt SAU KHI Kafka xác nhận đã
        // nhận
        Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            Boolean published = jdbc.queryForObject(
                    "SELECT published_at IS NOT NULL FROM outbox WHERE aggregate_id = ?",
                    Boolean.class, UUID.fromString(userId));
            assertThat(published).isTrue();
        });

        // Rồi đọc thật từ Kafka (topic chắc chắn đã tồn tại, đọc từ đầu)
        ConsumerRecord<String, String> record = findRecord(userId, Duration.ofSeconds(60));
        assertThat(record).as("không thấy sự kiện trên topic user.events").isNotNull();
        assertThat(record.key()).isEqualTo(userId);
        assertThat(record.value()).contains("UserRegistered").contains(email).contains(userId);
    }

    private ConsumerRecord<String, String> findRecord(String key, Duration timeout) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        long deadline = System.nanoTime() + timeout.toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("user.events"));
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        return null;
    }
}