package com.bookingplatform.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
// Nếu import dưới đây lỗi (Testcontainers 1.x), đổi thành: org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Chạy cả ứng dụng thật với Postgres và Redis thật (Testcontainers), rồi gọi
 * HTTP đúng như client.
 * OutboxPublisher bị tắt ở đây (không có Kafka); việc gửi lên Kafka có bài test
 * riêng: OutboxPublisherIntegrationTest.
 * Cần Docker đang chạy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
                "app.outbox.enabled=true",
                "app.outbox.publisher.enabled=false",
                "app.auth.login-limit.max-per-ip=1000"
})
@Testcontainers
class AuthApiIntegrationTest {

        @Container
        static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

        @Container
        static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7"))
                        .withExposedPorts(6379);

        @DynamicPropertySource
        static void containers(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", POSTGRES::getUsername);
                registry.add("spring.datasource.password", POSTGRES::getPassword);
                registry.add("spring.data.redis.host", REDIS::getHost);
                registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        }

        @Value("${local.server.port}")
        int port;

        @Autowired
        JdbcTemplate jdbc;

        private final HttpClient http = HttpClient.newHttpClient();

        private HttpResponse<String> post(String path, String json) throws Exception {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                                .header("Content-Type", "application/json")
                                .POST(BodyPublishers.ofString(json))
                                .build();
                return http.send(request, BodyHandlers.ofString());
        }

        private HttpResponse<String> get(String path) throws Exception {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET()
                                .build();
                return http.send(request, BodyHandlers.ofString());
        }

        private static String registerBody(String email, String password, String accountType) {
                return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"accountType\":\"" + accountType
                                + "\"}";
        }

        private static String loginBody(String email, String password) {
                return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        }

        private static String refreshBody(String refreshToken) {
                return "{\"refreshToken\":\"" + refreshToken + "\"}";
        }

        private static String extract(String json, String field) {
                Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                assertThat(m.find()).as("phản hồi phải có trường " + field + ": " + json).isTrue();
                return m.group(1);
        }

        private Jwt decodeViaJwks(String token) {
                return NimbusJwtDecoder
                                .withJwkSetUri("http://localhost:" + port + "/.well-known/jwks.json")
                                .build()
                                .decode(token);
        }

        private String registerAndLogin(String email) throws Exception {
                post("/api/v1/auth/register", registerBody(email, "Passw0rd!2026", "GUEST"));
                HttpResponse<String> loggedIn = post("/api/v1/auth/login", loginBody(email, "Passw0rd!2026"));
                assertThat(loggedIn.statusCode()).isEqualTo(200);
                return loggedIn.body();
        }

        // ----------------------------------------------------------------- tuần 2

        @Test
        void registerThenLoginIssuesTokenVerifiableThroughJwks() throws Exception {
                HttpResponse<String> registered = post("/api/v1/auth/register",
                                registerBody("Alice.Guest@Example.com", "Passw0rd!2026", "GUEST"));
                assertThat(registered.statusCode()).isEqualTo(201);
                assertThat(registered.body()).contains("alice.guest@example.com").contains("GUEST");
                assertThat(registered.body()).doesNotContain("Passw0rd").doesNotContain("password");

                HttpResponse<String> loggedIn = post("/api/v1/auth/login",
                                loginBody("alice.guest@example.com", "Passw0rd!2026"));
                assertThat(loggedIn.statusCode()).isEqualTo(200);

                Jwt jwt = decodeViaJwks(extract(loggedIn.body(), "accessToken"));
                assertThat(jwt.getClaimAsStringList("roles")).containsExactly("GUEST");
                assertThat(jwt.getSubject()).isNotBlank();
        }

        @Test
        void hostRegistrationGetsHostRole() throws Exception {
                post("/api/v1/auth/register", registerBody("host@example.com", "Passw0rd!2026", "HOST"));
                HttpResponse<String> loggedIn = post("/api/v1/auth/login",
                                loginBody("host@example.com", "Passw0rd!2026"));
                Jwt jwt = decodeViaJwks(extract(loggedIn.body(), "accessToken"));
                assertThat(jwt.getClaimAsStringList("roles")).containsExactly("HOST");
        }

        @Test
        void duplicateEmailIgnoringCaseReturns409() throws Exception {
                post("/api/v1/auth/register", registerBody("dup@example.com", "Passw0rd!2026", "GUEST"));

                HttpResponse<String> second = post("/api/v1/auth/register",
                                registerBody("DUP@EXAMPLE.COM", "Passw0rd!2026", "GUEST"));

                assertThat(second.statusCode()).isEqualTo(409);
                assertThat(second.headers().firstValue("Content-Type").orElse(""))
                                .startsWith("application/problem+json");
                assertThat(second.body()).contains("EMAIL_ALREADY_USED");
        }

        @Test
        void invalidInputReturns400WithFieldErrors() throws Exception {
                HttpResponse<String> response = post("/api/v1/auth/register",
                                registerBody("khong-phai-email", "123", "GUEST"));

                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).contains("VALIDATION_ERROR").contains("\"errors\"");
        }

        @Test
        void adminCannotSelfRegister() throws Exception {
                HttpResponse<String> response = post("/api/v1/auth/register",
                                registerBody("evil@example.com", "Passw0rd!2026", "ADMIN"));
                assertThat(response.statusCode()).isEqualTo(400);
        }

        @Test
        void wrongPasswordAndUnknownEmailReturnSame401() throws Exception {
                post("/api/v1/auth/register", registerBody("bob@example.com", "Passw0rd!2026", "GUEST"));

                HttpResponse<String> wrongPassword = post("/api/v1/auth/login",
                                loginBody("bob@example.com", "sai-mat-khau"));
                HttpResponse<String> unknownEmail = post("/api/v1/auth/login",
                                loginBody("ghost@example.com", "Passw0rd!2026"));

                assertThat(wrongPassword.statusCode()).isEqualTo(401);
                assertThat(unknownEmail.statusCode()).isEqualTo(401);
                assertThat(wrongPassword.body()).contains("INVALID_CREDENTIALS");
                assertThat(unknownEmail.body()).contains("INVALID_CREDENTIALS");
        }

        @Test
        void jwksExposesOnlyPublicKey() throws Exception {
                HttpResponse<String> response = get("/.well-known/jwks.json");

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("\"kty\":\"RSA\"").contains("\"kid\"");
                assertThat(response.body()).doesNotContain("\"d\":").doesNotContain("\"p\":").doesNotContain("\"q\":");
        }

        @Test
        void healthEndpointIsPublic() throws Exception {
                assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        }

        // ----------------------------------------------------------------- tuần 3

        @Test
        void registrationWritesUserRegisteredEventToOutbox() throws Exception {
                HttpResponse<String> registered = post("/api/v1/auth/register",
                                registerBody("Outbox.Check@Example.com", "Passw0rd!2026", "GUEST"));
                String userId = extract(registered.body(), "userId");

                Map<String, Object> row = jdbc.queryForMap(
                                "SELECT topic, type, payload::text AS payload, published_at FROM outbox WHERE aggregate_id = ?",
                                UUID.fromString(userId));

                assertThat(row.get("topic")).isEqualTo("user.events");
                assertThat(row.get("type")).isEqualTo("UserRegistered");
                assertThat((String) row.get("payload")).contains("outbox.check@example.com").contains(userId);
                assertThat(row.get("published_at")).as("OutboxPublisher đang tắt nên chưa gửi").isNull();
        }

        @Test
        void failedRegistrationWritesNothingToOutbox() throws Exception {
                post("/api/v1/auth/register", registerBody("once@example.com", "Passw0rd!2026", "GUEST"));
                post("/api/v1/auth/register", registerBody("once@example.com", "Passw0rd!2026", "GUEST")); // trùng, bị
                                                                                                           // 409

                Integer events = jdbc.queryForObject(
                                "SELECT count(*) FROM outbox WHERE payload::text LIKE '%once@example.com%'",
                                Integer.class);
                assertThat(events).isEqualTo(1);
        }

        @Test
        void loginReturnsRefreshTokenAndRefreshRotatesIt() throws Exception {
                String loginBody = registerAndLogin("rotate@example.com");
                String refresh1 = extract(loginBody, "refreshToken");

                HttpResponse<String> refreshed = post("/api/v1/auth/refresh", refreshBody(refresh1));

                assertThat(refreshed.statusCode()).isEqualTo(200);
                String refresh2 = extract(refreshed.body(), "refreshToken");
                assertThat(refresh2).isNotEqualTo(refresh1);
                assertThat(decodeViaJwks(extract(refreshed.body(), "accessToken")).getSubject()).isNotBlank();

                // Trong DB chỉ có BĂM của refresh token, không có token gốc
                Integer rawStored = jdbc.queryForObject(
                                "SELECT count(*) FROM refresh_token WHERE token_hash = ?", Integer.class, refresh1);
                assertThat(rawStored).isZero();
        }

        @Test
        void reusingAnOldRefreshTokenRevokesTheWholeFamily() throws Exception {
                String loginBody = registerAndLogin("theft@example.com");
                String refresh1 = extract(loginBody, "refreshToken");
                String refresh2 = extract(post("/api/v1/auth/refresh", refreshBody(refresh1)).body(), "refreshToken");

                HttpResponse<String> reuse = post("/api/v1/auth/refresh", refreshBody(refresh1));
                assertThat(reuse.statusCode()).isEqualTo(401);
                assertThat(reuse.body()).contains("INVALID_REFRESH_TOKEN");

                // Token mới nhất cũng bị thu hồi vì nghi bị đánh cắp
                HttpResponse<String> newestToken = post("/api/v1/auth/refresh", refreshBody(refresh2));
                assertThat(newestToken.statusCode()).isEqualTo(401);
        }

        @Test
        void logoutRevokesRefreshToken() throws Exception {
                String refresh = extract(registerAndLogin("logout@example.com"), "refreshToken");

                HttpResponse<String> loggedOut = post("/api/v1/auth/logout", refreshBody(refresh));
                assertThat(loggedOut.statusCode()).isEqualTo(204);

                assertThat(post("/api/v1/auth/refresh", refreshBody(refresh)).statusCode()).isEqualTo(401);
                // Đăng xuất hai lần hay token lạ đều không lỗi
                assertThat(post("/api/v1/auth/logout", refreshBody(refresh)).statusCode()).isEqualTo(204);
                assertThat(post("/api/v1/auth/logout", refreshBody("token-la")).statusCode()).isEqualTo(204);
        }

        @Test
        void unknownRefreshTokenReturns401() throws Exception {
                HttpResponse<String> response = post("/api/v1/auth/refresh", refreshBody("khong-ton-tai"));
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.body()).contains("INVALID_REFRESH_TOKEN");
        }

        @Test
        void tooManyWrongPasswordsReturn429EvenForTheCorrectPassword() throws Exception {
                post("/api/v1/auth/register", registerBody("limit@example.com", "Passw0rd!2026", "GUEST"));

                for (int i = 0; i < 5; i++) {
                        assertThat(post("/api/v1/auth/login", loginBody("limit@example.com", "sai-mat-khau"))
                                        .statusCode())
                                        .isEqualTo(401);
                }

                HttpResponse<String> blocked = post("/api/v1/auth/login",
                                loginBody("limit@example.com", "sai-mat-khau"));
                assertThat(blocked.statusCode()).isEqualTo(429);
                assertThat(blocked.body()).contains("TOO_MANY_ATTEMPTS");
                assertThat(Long.parseLong(blocked.headers().firstValue("Retry-After").orElse("0"))).isPositive();

                HttpResponse<String> correctButBlocked = post("/api/v1/auth/login",
                                loginBody("limit@example.com", "Passw0rd!2026"));
                assertThat(correctButBlocked.statusCode()).isEqualTo(429);
        }

        @Test
        void successfulLoginResetsTheFailureCounter() throws Exception {
                post("/api/v1/auth/register", registerBody("reset@example.com", "Passw0rd!2026", "GUEST"));

                for (int i = 0; i < 4; i++) {
                        post("/api/v1/auth/login", loginBody("reset@example.com", "sai-mat-khau"));
                }
                assertThat(post("/api/v1/auth/login", loginBody("reset@example.com", "Passw0rd!2026")).statusCode())
                                .isEqualTo(200);

                // Bộ đếm đã về 0: lại được sai thêm 4 lần mà chưa bị khóa
                for (int i = 0; i < 4; i++) {
                        assertThat(post("/api/v1/auth/login", loginBody("reset@example.com", "sai-mat-khau"))
                                        .statusCode())
                                        .isEqualTo(401);
                }
        }
}