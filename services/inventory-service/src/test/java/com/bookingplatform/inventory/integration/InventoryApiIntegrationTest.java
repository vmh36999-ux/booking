package com.bookingplatform.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingplatform.inventory.application.usecase.InitializeInventory;
import com.bookingplatform.inventory.support.TestJwtConfig;
import com.bookingplatform.inventory.support.TestJwtSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// Nếu import dưới đây lỗi (Testcontainers 1.x), đổi thành: org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Gọi API giữ chỗ qua HTTP thật với Postgres thật. Token do test tự ký với vai
 * trò ADMIN, GUEST hoặc HOST.
 * Kafka bị tắt (auto-startup=false). Cần Docker đang chạy.
 */
/**
 * ọi API qua HTTP thật: luồng giữ, xác nhận, nhả; mã 409 cho hết phòng và xung
 * đột trạng thái; chỉ vai trò SERVICE hoặc ADMIN được gọi (GUEST, HOST bị 403,
 * không token bị 401); dữ liệu sai bị 400; hold lạ bị 404 (9 kịch bản).
 * InventoryApiIntegrationTest
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.kafka.listener.auto-startup=false")
@Import(TestJwtConfig.class)
@Testcontainers
class InventoryApiIntegrationTest {

    private static final String HOLDS = "/api/v1/inventory/holds";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    InitializeInventory initializeInventory;

    private final HttpClient http = HttpClient.newHttpClient();
    private final LocalDate checkIn = LocalDate.now(ZoneOffset.UTC).plusDays(10);

    // ----------------------------------------------------------------- tiện ích

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

    private static String admin() {
        return TestJwtSupport.token(UUID.randomUUID(), "ADMIN");
    }

    private UUID roomTypeWith(int totalRooms) {
        UUID id = UUID.randomUUID();
        initializeInventory.handle(UUID.randomUUID(), id, totalRooms);
        return id;
    }

    private String holdBody(UUID booking, UUID roomType, LocalDate in, LocalDate out, int qty) {
        return "{\"bookingId\":\"" + booking + "\",\"roomTypeId\":\"" + roomType + "\",\"checkIn\":\"" + in
                + "\",\"checkOut\":\"" + out + "\",\"qty\":" + qty + "}";
    }

    private static String extract(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(m.find()).as("phản hồi phải có trường " + field + ": " + json).isTrue();
        return m.group(1);
    }

    // ----------------------------------------------------------------- luồng chính

    @Test
    void holdThenConfirmThenTryToRelease() throws Exception {
        UUID roomType = roomTypeWith(3);
        String body = holdBody(UUID.randomUUID(), roomType, checkIn, checkIn.plusDays(2), 2);

        HttpResponse<String> created = call("POST", HOLDS, admin(), body);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"status\":\"HELD\"").contains("\"nights\":2").contains("\"qty\":2");
        String id = extract(created.body(), "id");

        HttpResponse<String> replay = call("POST", HOLDS, admin(), body);
        assertThat(replay.statusCode()).as("gọi lại cùng bookingId").isEqualTo(200);
        assertThat(extract(replay.body(), "id")).isEqualTo(id);

        HttpResponse<String> confirmed = call("POST", HOLDS + "/" + id + "/confirm", admin(), null);
        assertThat(confirmed.statusCode()).isEqualTo(200);
        assertThat(confirmed.body()).contains("\"status\":\"CONFIRMED\"");
        assertThat(call("POST", HOLDS + "/" + id + "/confirm", admin(), null).body())
                .as("xác nhận lần hai: cùng kết quả").contains("\"status\":\"CONFIRMED\"");

        HttpResponse<String> release = call("POST", HOLDS + "/" + id + "/release", admin(), null);
        assertThat(release.statusCode()).isEqualTo(409);
        assertThat(release.body()).contains("HOLD_ALREADY_CONFIRMED");

        assertThat(call("GET", HOLDS + "/" + id, admin(), null).body()).contains("\"status\":\"CONFIRMED\"");
    }

    @Test
    void holdThenReleaseThenTryToConfirm() throws Exception {
        UUID roomType = roomTypeWith(3);
        HttpResponse<String> created = call("POST", HOLDS, admin(),
                holdBody(UUID.randomUUID(), roomType, checkIn, checkIn.plusDays(1), 1));
        String id = extract(created.body(), "id");

        HttpResponse<String> released = call("POST", HOLDS + "/" + id + "/release", admin(), null);
        assertThat(released.statusCode()).isEqualTo(200);
        assertThat(released.body()).contains("\"status\":\"RELEASED\"");
        assertThat(call("POST", HOLDS + "/" + id + "/release", admin(), null).statusCode())
                .as("nhả lần hai: cùng kết quả").isEqualTo(200);

        HttpResponse<String> confirm = call("POST", HOLDS + "/" + id + "/confirm", admin(), null);
        assertThat(confirm.statusCode()).isEqualTo(409);
        assertThat(confirm.body()).contains("HOLD_NOT_ACTIVE");
    }

    @Test
    void askingForMoreRoomsThanAvailableReturns409SoldOut() throws Exception {
        UUID roomType = roomTypeWith(2);
        assertThat(call("POST", HOLDS, admin(),
                holdBody(UUID.randomUUID(), roomType, checkIn, checkIn.plusDays(1), 2)).statusCode()).isEqualTo(201);

        HttpResponse<String> response = call("POST", HOLDS, admin(),
                holdBody(UUID.randomUUID(), roomType, checkIn, checkIn.plusDays(1), 1));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(response.body()).contains("SOLD_OUT");
    }

    @Test
    void roomTypeWithoutInventoryReturns409NoInventory() throws Exception {
        HttpResponse<String> response = call("POST", HOLDS, admin(),
                holdBody(UUID.randomUUID(), UUID.randomUUID(), checkIn, checkIn.plusDays(1), 1));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("NO_INVENTORY");
    }

    @Test
    void sameBookingWithADifferentRequestReturns409Mismatch() throws Exception {
        UUID roomType = roomTypeWith(5);
        UUID booking = UUID.randomUUID();
        call("POST", HOLDS, admin(), holdBody(booking, roomType, checkIn, checkIn.plusDays(1), 1));

        HttpResponse<String> response = call("POST", HOLDS, admin(),
                holdBody(booking, roomType, checkIn, checkIn.plusDays(1), 3));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("HOLD_MISMATCH");
    }

    // ----------------------------------------------------------------- bảo mật và
    // dữ liệu vào

    @Test
    void onlyServiceOrAdminRolesMayUseTheInternalApi() throws Exception {
        UUID roomType = roomTypeWith(2);
        String body = holdBody(UUID.randomUUID(), roomType, checkIn, checkIn.plusDays(1), 1);

        assertThat(call("POST", HOLDS, null, body).statusCode()).isEqualTo(401);
        assertThat(call("POST", HOLDS, TestJwtSupport.token(UUID.randomUUID(), "GUEST"), body).statusCode())
                .isEqualTo(403);
        assertThat(call("POST", HOLDS, TestJwtSupport.token(UUID.randomUUID(), "HOST"), body).statusCode())
                .isEqualTo(403);
        assertThat(call("POST", HOLDS, TestJwtSupport.token(UUID.randomUUID(), "SERVICE"), body).statusCode())
                .isEqualTo(201);
    }

    @Test
    void invalidRequestReturns400WithFieldErrors() throws Exception {
        UUID roomType = roomTypeWith(2);
        String zeroQty = holdBody(UUID.randomUUID(), roomType, checkIn, checkIn.plusDays(1), 0);
        String missingRoomType = "{\"bookingId\":\"" + UUID.randomUUID() + "\",\"checkIn\":\"" + checkIn
                + "\",\"checkOut\":\"" + checkIn.plusDays(1) + "\",\"qty\":1}";

        HttpResponse<String> first = call("POST", HOLDS, admin(), zeroQty);
        HttpResponse<String> second = call("POST", HOLDS, admin(), missingRoomType);

        assertThat(first.statusCode()).isEqualTo(400);
        assertThat(first.body()).contains("VALIDATION_ERROR").contains("\"errors\"");
        assertThat(second.statusCode()).isEqualTo(400);
    }

    @Test
    void pastCheckInReturns400InvalidStay() throws Exception {
        UUID roomType = roomTypeWith(2);
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);

        HttpResponse<String> response = call("POST", HOLDS, admin(),
                holdBody(UUID.randomUUID(), roomType, yesterday, yesterday.plusDays(2), 1));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("INVALID_STAY");
    }

    @Test
    void unknownHoldReturns404() throws Exception {
        UUID unknown = UUID.randomUUID();

        assertThat(call("GET", HOLDS + "/" + unknown, admin(), null).statusCode()).isEqualTo(404);
        HttpResponse<String> confirm = call("POST", HOLDS + "/" + unknown + "/confirm", admin(), null);
        assertThat(confirm.statusCode()).isEqualTo(404);
        assertThat(confirm.body()).contains("HOLD_NOT_FOUND");
    }
}
