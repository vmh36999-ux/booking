package com.bookingplatform.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingplatform.auth.domain.model.Role;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration test chạy toàn bộ Spring Boot application với PostgreSQL thật
 * thông qua Testcontainers.
 *
 * Quá trình test:
 * 1. Khởi động PostgreSQL container.
 * 2. Flyway chạy database migrations.
 * 3. Hibernate kiểm tra database schema.
 * 4. Khởi động Spring Boot application trên một port ngẫu nhiên.
 * 5. Gửi HTTP request giống như một client thực tế.
 *
 * Yêu cầu Docker phải đang chạy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthApiIntegrationTest {

    // Khởi tạo PostgreSQL 16 trong Docker để phục vụ integration test.
    // Container được chia sẻ cho toàn bộ test trong class này.
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    // Ghi đè cấu hình datasource của Spring Boot bằng thông tin PostgreSQL
    // container.
    // Nhờ vậy test không phụ thuộc vào PostgreSQL đang cài trên máy local.
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // Lấy port ngẫu nhiên mà Spring Boot sử dụng khi khởi động ứng dụng.
    @Value("${local.server.port}")
    int port;

    // HttpClient dùng để gửi HTTP request trực tiếp đến ứng dụng đang chạy.
    private final HttpClient http = HttpClient.newHttpClient();

    // Helper method gửi POST request với JSON body.
    // Dùng chung cho các API register và login.
    private HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json))
                .build();

        return http.send(request, BodyHandlers.ofString());
    }

    // Helper method gửi GET request.
    // Dùng để kiểm tra JWKS và health endpoint.
    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();

        return http.send(request, BodyHandlers.ofString());
    }

    // Tạo JSON body cho API register.
    private static String registerBody(String email, String password, String accountType) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"accountType\":\"" + accountType + "\"}";
    }

    // Tạo JSON body cho API login.
    private static String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    // Trích xuất accessToken từ JSON response.
    // Nếu response không chứa accessToken thì test sẽ thất bại ngay tại đây.
    private static String extractAccessToken(String json) {
        Matcher m = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(json);

        assertThat(m.find())
                .as("Response must contain accessToken: " + json)
                .isTrue();

        return m.group(1);
    }

    @Test
    void registerThenLoginIssuesTokenVerifiableThroughJwks() throws Exception {
        // Arrange & Act: Đăng ký tài khoản GUEST mới.
        HttpResponse<String> registered = post(
                "/api/v1/auth/register",
                registerBody("Alice.Guest@Example.com", "Passw0rd!2026", "GUEST"));

        // API register phải trả về HTTP 201 Created.
        assertThat(registered.statusCode()).isEqualTo(201);

        // Email phải được chuẩn hóa thành chữ thường và role phải là GUEST.
        assertThat(registered.body())
                .contains("alice.guest@example.com")
                .contains("GUEST");

        // API không được trả password hoặc password plaintext trong response.
        assertThat(registered.body())
                .doesNotContain("Passw0rd")
                .doesNotContain("password");

        // Act: Đăng nhập bằng tài khoản vừa đăng ký.
        HttpResponse<String> loggedIn = post(
                "/api/v1/auth/login",
                loginBody("alice.guest@example.com", "Passw0rd!2026"));

        // Login thành công phải trả về HTTP 200 OK.
        assertThat(loggedIn.statusCode()).isEqualTo(200);

        // Lấy JWT access token từ response.
        String token = extractAccessToken(loggedIn.body());

        // Kiểm tra JWT giống cách một service khác xác thực token:
        // Lấy public key từ JWKS endpoint, sau đó verify chữ ký và decode JWT.
        Jwt jwt = NimbusJwtDecoder
                .withJwkSetUri("http://localhost:" + port + "/.well-known/jwks.json")
                .build()
                .decode(token);

        // JWT phải chứa đúng role GUEST.
        assertThat(jwt.getClaimAsStringList("roles"))
                .containsExactly(Role.GUEST.name());

        // Subject phải có giá trị, thường là user ID.
        assertThat(jwt.getSubject()).isNotBlank();
    }

    @Test
    void hostRegistrationGetsHostRole() throws Exception {
        // Đăng ký tài khoản với accountType là HOST.
        post(
                "/api/v1/auth/register",
                registerBody("host@example.com", "Passw0rd!2026", "HOST"));

        // Đăng nhập bằng tài khoản HOST vừa tạo.
        HttpResponse<String> loggedIn = post(
                "/api/v1/auth/login",
                loginBody("host@example.com", "Passw0rd!2026"));

        // Lấy JWT, verify chữ ký thông qua public key trong JWKS.
        Jwt jwt = NimbusJwtDecoder
                .withJwkSetUri("http://localhost:" + port + "/.well-known/jwks.json")
                .build()
                .decode(extractAccessToken(loggedIn.body()));

        // Xác nhận JWT chứa role HOST thay vì GUEST.
        assertThat(jwt.getClaimAsStringList("roles"))
                .containsExactly("HOST");
    }

    @Test
    void duplicateEmailIgnoringCaseReturns409() throws Exception {
        // Đăng ký tài khoản lần đầu với email viết thường.
        post(
                "/api/v1/auth/register",
                registerBody("dup@example.com", "Passw0rd!2026", "GUEST"));

        // Thử đăng ký lần hai với cùng email nhưng viết hoa.
        HttpResponse<String> second = post(
                "/api/v1/auth/register",
                registerBody("DUP@EXAMPLE.COM", "Passw0rd!2026", "GUEST"));

        // Email bị trùng phải trả HTTP 409 Conflict.
        assertThat(second.statusCode()).isEqualTo(409);

        // Response phải sử dụng định dạng RFC 9457 Problem Details.
        assertThat(second.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/problem+json");

        // Error code phải xác định đây là lỗi email đã được sử dụng.
        assertThat(second.body()).contains("EMAIL_ALREADY_USED");
    }

    @Test
    void invalidInputReturns400WithFieldErrors() throws Exception {
        // Gửi dữ liệu không hợp lệ: email sai format và password quá ngắn.
        HttpResponse<String> response = post(
                "/api/v1/auth/register",
                registerBody("khong-phai-email", "123", "GUEST"));

        // Validation thất bại phải trả HTTP 400 Bad Request.
        assertThat(response.statusCode()).isEqualTo(400);

        // Response phải có error code và danh sách lỗi theo từng field.
        assertThat(response.body())
                .contains("VALIDATION_ERROR")
                .contains("\"errors\"");
    }

    @Test
    void adminCannotSelfRegister() throws Exception {
        // Người dùng thông thường không được tự đăng ký tài khoản ADMIN.
        HttpResponse<String> response = post(
                "/api/v1/auth/register",
                registerBody("evil@example.com", "Passw0rd!2026", "ADMIN"));

        // Yêu cầu đăng ký ADMIN phải bị từ chối.
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnSame401() throws Exception {
        // Tạo trước một tài khoản hợp lệ để kiểm tra đăng nhập.
        post(
                "/api/v1/auth/register",
                registerBody("bob@example.com", "Passw0rd!2026", "GUEST"));

        // Trường hợp 1: Email tồn tại nhưng password không đúng.
        HttpResponse<String> wrongPassword = post(
                "/api/v1/auth/login",
                loginBody("bob@example.com", "sai-mat-khau"));

        // Trường hợp 2: Email không tồn tại trong hệ thống.
        HttpResponse<String> unknownEmail = post(
                "/api/v1/auth/login",
                loginBody("ghost@example.com", "Passw0rd!2026"));

        // Cả hai trường hợp đều phải trả HTTP 401 Unauthorized.
        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.statusCode()).isEqualTo(401);

        // Cả hai phải trả cùng error code để tránh tiết lộ email có tồn tại hay không.
        assertThat(wrongPassword.body()).contains("INVALID_CREDENTIALS");
        assertThat(unknownEmail.body()).contains("INVALID_CREDENTIALS");
    }

    @Test
    void jwksExposesOnlyPublicKey() throws Exception {
        // Gọi endpoint cung cấp JSON Web Key Set.
        HttpResponse<String> response = get("/.well-known/jwks.json");

        // JWKS endpoint phải truy cập được và trả HTTP 200.
        assertThat(response.statusCode()).isEqualTo(200);

        // Public JWK phải có thông tin loại khóa RSA và Key ID.
        assertThat(response.body())
                .contains("\"kty\":\"RSA\"")
                .contains("\"kid\"");

        // Không được expose các thành phần private key ra bên ngoài.
        assertThat(response.body())
                .doesNotContain("\"d\":")
                .doesNotContain("\"p\":")
                .doesNotContain("\"q\":");
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        // Liveness endpoint phải truy cập được mà không cần authentication.
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
    }
}