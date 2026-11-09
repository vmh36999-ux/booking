package com.bookingplatform.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// Nếu hai import dưới đây lỗi (Testcontainers 1.x), xem hướng dẫn: đổi sang org.testcontainers.containers.PostgreSQLContainer
// và org.testcontainers.containers.KafkaContainer (hoặc org.testcontainers.kafka.ConfluentKafkaContainer)
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Chạy user-service thật với Postgres và Kafka thật.
 * Không có auth-service ở đây: test tự ký JWT bằng một khóa riêng của test và
 * cho JwtDecoder tin khóa đó.
 * Cần Docker đang chạy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserServiceIntegrationTest {

    private static final String SAME_KEY = "cung-partition"; // cùng key thì cùng partition, giữ đúng thứ tự

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static final RSAKey KEY = newKey();

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /** Thay JwtDecoder thật (trỏ về auth-service) bằng một bộ tin khóa của test. */
    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        JwtDecoder jwtDecoder() throws JOSEException {
            return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    private final HttpClient http = HttpClient.newHttpClient();

    // ----------------------------------------------------------------- tiện ích

    private static String token(UUID userId) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("roles", List.of("GUEST"))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String eventJson(UUID eventId, UUID userId, String email) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"UserRegistered\",\"version\":1,"
                + "\"occurredAt\":\"" + Instant.now() + "\",\"aggregateId\":\"" + userId + "\","
                + "\"correlationId\":null,\"data\":{\"userId\":\"" + userId + "\",\"email\":\"" + email
                + "\",\"roles\":[\"GUEST\"]}}";
    }

    private void sendEvent(UUID eventId, UUID userId) throws Exception {
        kafka.send("user.events", SAME_KEY, eventJson(eventId, userId, userId + "@example.com")).get();
    }

    private int count(String table, String column, UUID value) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class,
                value);
        return n == null ? 0 : n;
    }

    private void awaitProfile(UUID userId) {
        Awaitility.await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(count("profile", "user_id", userId)).isEqualTo(1));
    }

    private HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), BodyHandlers.ofString());
    }

    // ----------------------------------------------------------------- consumer
    // idempotent

    @Test
    void registrationEventCreatesProfileExactlyOnceEvenWhenDeliveredTwice() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sentinelEvent = UUID.randomUUID();
        UUID sentinelUser = UUID.randomUUID();

        sendEvent(eventId, userId);
        awaitProfile(userId);

        sendEvent(eventId, userId); // cùng eventId: gửi trùng
        sendEvent(sentinelEvent, sentinelUser); // "cột mốc": cột mốc xử lý xong nghĩa là bản trùng chắc chắn đã được xử
                                                // lý
        awaitProfile(sentinelUser);

        assertThat(count("profile", "user_id", userId)).isEqualTo(1);
        assertThat(count("processed_event", "event_id", eventId)).isEqualTo(1);
    }

    // ----------------------------------------------------------------- API hồ sơ

    @Test
    void getMeWithoutTokenReturns401() throws Exception {
        assertThat(call("GET", "/api/v1/users/me", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void getMeReturnsTheProfileOfTheTokenOwner() throws Exception {
        UUID userId = UUID.randomUUID();
        sendEvent(UUID.randomUUID(), userId);
        awaitProfile(userId);

        HttpResponse<String> response = call("GET", "/api/v1/users/me", token(userId), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(userId.toString());
    }

    @Test
    void putUpdatesTheProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        sendEvent(UUID.randomUUID(), userId);
        awaitProfile(userId);
        String body = "{\"fullName\":\"Nguyen Van A\",\"phone\":\"0900000000\","
                + "\"country\":\"VN\",\"language\":\"vi\",\"currency\":\"VND\"}";

        HttpResponse<String> updated = call("PUT", "/api/v1/users/me", token(userId), body);
        assertThat(updated.statusCode()).isEqualTo(200);

        HttpResponse<String> fetched = call("GET", "/api/v1/users/me", token(userId), null);
        assertThat(fetched.body()).contains("Nguyen Van A").contains("VND").contains("VN");
    }

    @Test
    void getMeWithoutProfileReturns404() throws Exception {
        HttpResponse<String> response = call("GET", "/api/v1/users/me", token(UUID.randomUUID()), null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("PROFILE_NOT_FOUND");
    }

    @Test
    void invalidCountryReturns400() throws Exception {
        HttpResponse<String> response = call("PUT", "/api/v1/users/me", token(UUID.randomUUID()),
                "{\"country\":\"vietnam\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("VALIDATION_ERROR");
    }
}