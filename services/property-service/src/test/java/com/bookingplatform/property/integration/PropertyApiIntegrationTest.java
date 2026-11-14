package com.bookingplatform.property.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingplatform.property.support.TestJwtConfig;
import com.bookingplatform.property.support.TestJwtSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// Nếu import dưới đây lỗi (Testcontainers 1.x), đổi thành: org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Chạy property-service thật với Postgres thật. Kafka KHÔNG cần:
 * OutboxPublisher bị tắt,
 * nên sự kiện nằm trong bảng outbox và test kiểm tra thẳng bảng đó.
 * Token do test tự ký (TestJwtSupport), mang vai trò HOST, GUEST hoặc ADMIN như
 * token thật của auth-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.outbox.publisher.enabled=false")
@Import(TestJwtConfig.class)
@Testcontainers
class PropertyApiIntegrationTest {

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
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

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

    private static String hostToken(UUID host) {
        return TestJwtSupport.token(host, "HOST");
    }

    private static String propertyJson(String name) {
        return "{\"name\":\"" + name + "\",\"description\":\"Mo ta\",\"city\":\"Da Nang\",\"country\":\"VN\","
                + "\"address\":\"1 Vo Nguyen Giap\",\"latitude\":16.0544,\"longitude\":108.2022,"
                + "\"currency\":\"VND\",\"amenities\":[\"WIFI\",\"POOL\"]}";
    }

    private static String roomJson(String name, long price) {
        return "{\"name\":\"" + name + "\",\"maxGuests\":2,\"bedConfig\":\"1 giuong doi\","
                + "\"totalRooms\":10,\"basePriceMinor\":" + price + "}";
    }

    private static String extract(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(m.find()).as("phản hồi phải có trường " + field + ": " + json).isTrue();
        return m.group(1);
    }

    private String createProperty(String token, String name) throws Exception {
        HttpResponse<String> response = call("POST", "/api/v1/host/properties", token, propertyJson(name));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return extract(response.body(), "id");
    }

    private String addRoom(String token, String propertyId) throws Exception {
        HttpResponse<String> response = call("POST", "/api/v1/host/properties/" + propertyId + "/room-types",
                token, roomJson("Deluxe", 1_200_000));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return extract(response.body(), "id");
    }

    private String createPublished(String token, String name) throws Exception {
        String id = createProperty(token, name);
        addRoom(token, id);
        assertThat(call("POST", "/api/v1/host/properties/" + id + "/publish", token, null).statusCode()).isEqualTo(200);
        return id;
    }

    private int outboxCount(String propertyId, String type) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE aggregate_id = ? AND type = ?",
                Integer.class, UUID.fromString(propertyId), type);
        return n == null ? 0 : n;
    }

    private String outboxPayload(String propertyId, String type) {
        return jdbc.queryForObject("SELECT payload::text FROM outbox WHERE aggregate_id = ? AND type = ?",
                String.class, UUID.fromString(propertyId), type);
    }

    // ----------------------------------------------------------------- luồng chính

    @Test
    void hostCreatesDraftAddsRoomTypeAndPublishes() throws Exception {
        UUID host = UUID.randomUUID();
        String token = hostToken(host);

        HttpResponse<String> created = call("POST", "/api/v1/host/properties", token, propertyJson("Sunrise Da Nang"));
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"status\":\"DRAFT\"").contains(host.toString());
        String id = extract(created.body(), "id");

        addRoom(token, id);

        HttpResponse<String> published = call("POST", "/api/v1/host/properties/" + id + "/publish", token, null);
        assertThat(published.statusCode()).isEqualTo(200);
        assertThat(published.body()).contains("\"status\":\"PUBLISHED\"");

        // Khách (không token) thấy khách sạn đã đăng, nhưng không thấy hostId
        HttpResponse<String> guestView = call("GET", "/api/v1/properties/" + id, null, null);
        assertThat(guestView.statusCode()).isEqualTo(200);
        assertThat(guestView.body()).contains("Sunrise Da Nang").contains("Deluxe");
        assertThat(guestView.body()).doesNotContain(host.toString()).doesNotContain("hostId");

        // Sự kiện đã nằm trong outbox, đúng topic, đủ nội dung
        assertThat(outboxCount(id, "RoomTypeCreated")).isEqualTo(1);
        assertThat(outboxCount(id, "PropertyPublished")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT DISTINCT topic FROM outbox WHERE aggregate_id = ?",
                String.class, UUID.fromString(id))).isEqualTo("property.events");
        assertThat(outboxPayload(id, "PropertyPublished"))
                .contains("Sunrise Da Nang").contains("Deluxe").contains(host.toString());
    }

    @Test
    void draftPropertyIsHiddenFromGuests() throws Exception {
        String id = createProperty(hostToken(UUID.randomUUID()), "Ban nhap");

        HttpResponse<String> response = call("GET", "/api/v1/properties/" + id, null, null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("PROPERTY_NOT_FOUND");
    }

    // ----------------------------------------------------------------- phân quyền

    @Test
    void anotherHostGets403OnEveryWriteOperation() throws Exception {
        String ownerToken = hostToken(UUID.randomUUID());
        String id = createProperty(ownerToken, "Cua chu A");
        String roomId = addRoom(ownerToken, id);
        String intruder = hostToken(UUID.randomUUID());

        String base = "/api/v1/host/properties/" + id;
        assertThat(call("GET", base, intruder, null).statusCode()).isEqualTo(403);
        assertThat(call("PUT", base, intruder, propertyJson("Chiem quyen")).statusCode()).isEqualTo(403);
        assertThat(call("DELETE", base, intruder, null).statusCode()).isEqualTo(403);
        assertThat(call("POST", base + "/publish", intruder, null).statusCode()).isEqualTo(403);
        assertThat(call("POST", base + "/room-types", intruder, roomJson("Fake", 1)).statusCode()).isEqualTo(403);
        assertThat(call("PUT", base + "/room-types/" + roomId, intruder, roomJson("Fake", 1)).statusCode())
                .isEqualTo(403);
        assertThat(call("DELETE", base + "/room-types/" + roomId, intruder, null).statusCode()).isEqualTo(403);

        HttpResponse<String> forbidden = call("PUT", base, intruder, propertyJson("Chiem quyen"));
        assertThat(forbidden.body()).contains("NOT_PROPERTY_OWNER");

        // Dữ liệu của chủ thật không bị đổi
        assertThat(call("GET", base, ownerToken, null).body()).contains("Cua chu A").doesNotContain("Chiem quyen");

        // Khách sạn không tồn tại thì 404 chứ không phải 403
        assertThat(call("GET", "/api/v1/host/properties/" + UUID.randomUUID(), intruder, null).statusCode())
                .isEqualTo(404);
    }

    @Test
    void guestRoleCannotCallHostOrAdminApi() throws Exception {
        String guest = TestJwtSupport.token(UUID.randomUUID(), "GUEST");
        String host = hostToken(UUID.randomUUID());

        assertThat(call("POST", "/api/v1/host/properties", guest, propertyJson("X")).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/api/v1/host/properties", guest, null).statusCode()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/admin/properties/" + UUID.randomUUID() + "/suspend", host, null).statusCode())
                .isEqualTo(403);
    }

    @Test
    void hostApiWithoutTokenReturns401() throws Exception {
        assertThat(call("POST", "/api/v1/host/properties", null, propertyJson("X")).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/v1/host/properties", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void roomTypeMustBelongToTheGivenProperty() throws Exception {
        String token = hostToken(UUID.randomUUID());
        String property1 = createProperty(token, "Khach san 1");
        String room1 = addRoom(token, property1);
        String property2 = createProperty(token, "Khach san 2");

        HttpResponse<String> response = call("PUT", "/api/v1/host/properties/" + property2 + "/room-types/" + room1,
                token, roomJson("Doi cho", 5));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("ROOM_TYPE_NOT_FOUND");
    }

    // ----------------------------------------------------------------- vòng đời và
    // sự kiện

    @Test
    void updatingPublishedPropertyWritesPropertyUpdatedButUpdatingDraftDoesNot() throws Exception {
        String token = hostToken(UUID.randomUUID());
        String live = createPublished(token, "Dang ban");
        String draft = createProperty(token, "Ban nhap");

        assertThat(call("PUT", "/api/v1/host/properties/" + live, token, propertyJson("Doi ten")).statusCode())
                .isEqualTo(200);
        assertThat(call("PUT", "/api/v1/host/properties/" + draft, token, propertyJson("Doi ten")).statusCode())
                .isEqualTo(200);

        assertThat(outboxCount(live, "PropertyUpdated")).isEqualTo(1);
        assertThat(outboxPayload(live, "PropertyUpdated")).contains("Doi ten");
        assertThat(outboxCount(draft, "PropertyUpdated")).isZero();
    }

    @Test
    void unpublishHidesThePropertyAndWritesPropertyUnpublished() throws Exception {
        String token = hostToken(UUID.randomUUID());
        String id = createPublished(token, "Se go dang");

        HttpResponse<String> response = call("POST", "/api/v1/host/properties/" + id + "/unpublish", token, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"DRAFT\"");
        assertThat(call("GET", "/api/v1/properties/" + id, null, null).statusCode()).isEqualTo(404);
        assertThat(outboxCount(id, "PropertyUnpublished")).isEqualTo(1);
        assertThat(outboxPayload(id, "PropertyUnpublished")).contains("HOST_UNPUBLISHED");
    }

    @Test
    void publishingWithoutRoomTypeReturns409() throws Exception {
        String token = hostToken(UUID.randomUUID());
        String id = createProperty(token, "Chua co phong");

        HttpResponse<String> response = call("POST", "/api/v1/host/properties/" + id + "/publish", token, null);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("INVALID_PROPERTY_STATE");
        assertThat(outboxCount(id, "PropertyPublished")).isZero();
    }

    @Test
    void adminSuspendRemovesFromGuestsAndBlocksRepublishUntilReinstated() throws Exception {
        String hostToken = hostToken(UUID.randomUUID());
        String admin = TestJwtSupport.token(UUID.randomUUID(), "ADMIN");
        String id = createPublished(hostToken, "Vi pham");

        HttpResponse<String> suspended = call("POST", "/api/v1/admin/properties/" + id + "/suspend", admin, null);
        assertThat(suspended.statusCode()).isEqualTo(200);
        assertThat(suspended.body()).contains("\"status\":\"SUSPENDED\"");
        assertThat(call("GET", "/api/v1/properties/" + id, null, null).statusCode()).isEqualTo(404);
        assertThat(outboxPayload(id, "PropertyUnpublished")).contains("ADMIN_SUSPENDED");

        // Host không tự đăng lại được
        assertThat(call("POST", "/api/v1/host/properties/" + id + "/publish", hostToken, null).statusCode())
                .isEqualTo(409);

        // Admin mở khóa: về DRAFT, host đăng lại được
        HttpResponse<String> reinstated = call("POST", "/api/v1/admin/properties/" + id + "/reinstate", admin, null);
        assertThat(reinstated.body()).contains("\"status\":\"DRAFT\"");
        assertThat(call("POST", "/api/v1/host/properties/" + id + "/publish", hostToken, null).statusCode())
                .isEqualTo(200);
    }

    @Test
    void publishedPropertyCannotBeDeletedButDraftCan() throws Exception {
        String token = hostToken(UUID.randomUUID());
        String id = createPublished(token, "Dang dang");
        String base = "/api/v1/host/properties/" + id;

        assertThat(call("DELETE", base, token, null).statusCode()).isEqualTo(409);

        call("POST", base + "/unpublish", token, null);
        assertThat(call("DELETE", base, token, null).statusCode()).isEqualTo(204);
        assertThat(call("GET", base, token, null).statusCode()).isEqualTo(404);
    }

    @Test
    void cannotDeleteTheLastRoomTypeOfAPublishedProperty() throws Exception {
        String token = hostToken(UUID.randomUUID());
        String id = createProperty(token, "Mot phong");
        String roomId = addRoom(token, id);
        call("POST", "/api/v1/host/properties/" + id + "/publish", token, null);

        HttpResponse<String> response = call("DELETE", "/api/v1/host/properties/" + id + "/room-types/" + roomId, token,
                null);

        assertThat(response.statusCode()).isEqualTo(409);
    }

    // ----------------------------------------------------------------- dữ liệu đầu
    // vào và danh sách

    @Test
    void invalidCoordinatesReturn400() throws Exception {
        String body = propertyJson("Sai toa do").replace("\"latitude\":16.0544", "\"latitude\":123.0");

        HttpResponse<String> response = call("POST", "/api/v1/host/properties", hostToken(UUID.randomUUID()), body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("VALIDATION_ERROR").contains("\"errors\"");
    }

    @Test
    void unknownAmenityReturns400() throws Exception {
        String body = propertyJson("Tien nghi la").replace("\"WIFI\",\"POOL\"", "\"KHONG_CO\"");

        HttpResponse<String> response = call("POST", "/api/v1/host/properties", hostToken(UUID.randomUUID()), body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("UNKNOWN_AMENITY").contains("KHONG_CO");
    }

    @Test
    void amenityCatalogIsPublic() throws Exception {
        HttpResponse<String> response = call("GET", "/api/v1/properties/amenities", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("WIFI").contains("POOL").contains("NON_SMOKING");
    }

    @Test
    void hostListContainsOnlyOwnPropertiesAndPagesByCursor() throws Exception {
        String tokenA = hostToken(UUID.randomUUID());
        String tokenB = hostToken(UUID.randomUUID());
        createProperty(tokenA, "A1");
        createProperty(tokenA, "A2");
        createProperty(tokenA, "A3");
        createProperty(tokenB, "B1");

        HttpResponse<String> firstPage = call("GET", "/api/v1/host/properties?limit=2", tokenA, null);
        assertThat(firstPage.statusCode()).isEqualTo(200);
        assertThat(countOccurrences(firstPage.body(), "\"status\"")).isEqualTo(2);
        String cursor = extract(firstPage.body(), "nextCursor");

        HttpResponse<String> secondPage = call("GET", "/api/v1/host/properties?limit=2&after=" + cursor, tokenA, null);
        assertThat(countOccurrences(secondPage.body(), "\"status\"")).isEqualTo(1);
        assertThat(secondPage.body()).contains("\"nextCursor\":null");
        assertThat(firstPage.body() + secondPage.body()).doesNotContain("B1");

        HttpResponse<String> hostBList = call("GET", "/api/v1/host/properties", tokenB, null);
        assertThat(countOccurrences(hostBList.body(), "\"status\"")).isEqualTo(1);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
