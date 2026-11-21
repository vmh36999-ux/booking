package com.bookingplatform.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookingplatform.inventory.application.usecase.ConfirmHold;
import com.bookingplatform.inventory.application.usecase.HoldRooms;
import com.bookingplatform.inventory.application.usecase.InitializeInventory;
import com.bookingplatform.inventory.domain.exception.SoldOutException;
import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldCommand;
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
 * API host chỉnh số phòng: phân quyền theo chủ loại phòng, không giảm dưới số
 * đã giữ và đặt, all-or-nothing.
 * Token do test tự ký với vai trò HOST (sub = id host). Cần Docker đang chạy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.listener.auto-startup=false", "app.inventory.expiry.enabled=false" })
@Import(TestJwtConfig.class)
@Testcontainers
class HostInventoryApiIntegrationTest {

    private static final String PATH = "/api/v1/host/inventory";

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

    @Autowired
    HoldRooms holdRooms;

    @Autowired
    ConfirmHold confirmHold;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final LocalDate day0 = LocalDate.now(ZoneOffset.UTC).plusDays(10);

    // ----------------------------------------------------------------- tiện ích

    private HttpResponse<String> put(String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.PUT(HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), BodyHandlers.ofString());
    }

    private static String hostToken(UUID host) {
        return TestJwtSupport.token(host, "HOST");
    }

    /**
     * Tạo loại phòng có tồn kho và ghi nhận chủ là "host", giống khi nhận
     * RoomTypeCreated có hostId.
     */
    private UUID roomTypeOwnedBy(UUID host, int totalRooms) {
        UUID roomType = UUID.randomUUID();
        initializeInventory.handle(UUID.randomUUID(), roomType, totalRooms, UUID.randomUUID(), host);
        return roomType;
    }

    private String body(UUID roomType, LocalDate from, LocalDate to, int totalRooms) {
        return "{\"roomTypeId\":\"" + roomType + "\",\"from\":\"" + from + "\",\"to\":\"" + to
                + "\",\"totalRooms\":" + totalRooms + "}";
    }

    private int total(UUID roomType, int dayOffset) {
        Integer v = jdbc.queryForObject("SELECT total FROM room_night WHERE room_type_id = ? AND night = ?",
                Integer.class, roomType, day0.plusDays(dayOffset));
        return v == null ? -1 : v;
    }

    private Hold hold(UUID roomType, int fromOffset, int toOffset, int qty) {
        return holdRooms.execute(new HoldCommand(UUID.randomUUID(), roomType,
                day0.plusDays(fromOffset), day0.plusDays(toOffset), qty)).hold();
    }

    // ----------------------------------------------------------------- bài test

    @Test
    void hostRaisesTheTotalForARangeAndOnlyThatRange() throws Exception {
        UUID host = UUID.randomUUID();
        UUID roomType = roomTypeOwnedBy(host, 5);

        HttpResponse<String> response = put(hostToken(host), body(roomType, day0, day0.plusDays(5), 8));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"nights\":5").contains("\"totalRooms\":8");
        for (int night = 0; night < 5; night++) {
            assertThat(total(roomType, night)).as("đêm " + night).isEqualTo(8);
        }
        assertThat(total(roomType, -1)).as("đêm trước khoảng ngày không đổi").isEqualTo(5);
        assertThat(total(roomType, 5)).as("đêm 'to' không tính, không đổi").isEqualTo(5);
    }

    @Test
    void hostLowersTheTotalWhenNothingIsCommitted() throws Exception {
        UUID host = UUID.randomUUID();
        UUID roomType = roomTypeOwnedBy(host, 5);

        HttpResponse<String> response = put(hostToken(host), body(roomType, day0, day0.plusDays(3), 2));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(total(roomType, 0)).isEqualTo(2);
        assertThat(total(roomType, 2)).isEqualTo(2);
    }

    @Test
    void cannotLowerBelowHeldAndBookedAndNothingChangesAtAll() throws Exception {
        UUID host = UUID.randomUUID();
        UUID roomType = roomTypeOwnedBy(host, 5);
        hold(roomType, 2, 3, 2); // đêm số 2: giữ 2
        Hold toConfirm = hold(roomType, 2, 3, 1); // đêm số 2: đặt 1
        confirmHold.execute(toConfirm.id()); // => cam kết 3 phòng ở đêm số 2

        HttpResponse<String> blocked = put(hostToken(host), body(roomType, day0, day0.plusDays(5), 2));

        assertThat(blocked.statusCode()).isEqualTo(409);
        assertThat(blocked.body()).contains("BELOW_COMMITTED").contains(day0.plusDays(2).toString());
        for (int night = 0; night < 5; night++) {
            assertThat(total(roomType, night)).as("ALL-OR-NOTHING: đêm " + night + " không đổi").isEqualTo(5);
        }

        // Giảm đúng bằng số đã cam kết (3) thì được
        HttpResponse<String> exact = put(hostToken(host), body(roomType, day0, day0.plusDays(5), 3));
        assertThat(exact.statusCode()).isEqualTo(200);
        assertThat(total(roomType, 2)).isEqualTo(3);
    }

    @Test
    void raisingTheTotalLetsMoreGuestsHold() throws Exception {
        UUID host = UUID.randomUUID();
        UUID roomType = roomTypeOwnedBy(host, 1);
        hold(roomType, 0, 1, 1);
        assertThatThrownBy(() -> hold(roomType, 0, 1, 1)).isInstanceOf(SoldOutException.class);

        assertThat(put(hostToken(host), body(roomType, day0, day0.plusDays(1), 3)).statusCode()).isEqualTo(200);

        assertThat(hold(roomType, 0, 1, 1).status().name()).isEqualTo("HELD");
    }

    @Test
    void zeroRoomsClosesTheNightsForSale() throws Exception {
        UUID host = UUID.randomUUID();
        UUID roomType = roomTypeOwnedBy(host, 4);

        assertThat(put(hostToken(host), body(roomType, day0, day0.plusDays(2), 0)).statusCode()).isEqualTo(200);

        assertThatThrownBy(() -> hold(roomType, 0, 2, 1)).isInstanceOf(SoldOutException.class);
    }

    @Test
    void anotherHostGets403AndNothingChanges() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID roomType = roomTypeOwnedBy(owner, 5);

        HttpResponse<String> response = put(hostToken(UUID.randomUUID()), body(roomType, day0, day0.plusDays(3), 99));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("NOT_ROOM_TYPE_OWNER");
        assertThat(total(roomType, 0)).isEqualTo(5);
    }

    @Test
    void unknownRoomTypeReturns404() throws Exception {
        HttpResponse<String> response = put(hostToken(UUID.randomUUID()),
                body(UUID.randomUUID(), day0, day0.plusDays(2), 5));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("ROOM_TYPE_NOT_FOUND");
    }

    @Test
    void onlyTheHostRoleMayCallThisApi() throws Exception {
        UUID host = UUID.randomUUID();
        String request = body(roomTypeOwnedBy(host, 5), day0, day0.plusDays(2), 4);

        assertThat(put(null, request).statusCode()).isEqualTo(401);
        assertThat(put(TestJwtSupport.token(host, "GUEST"), request).statusCode()).isEqualTo(403);
        assertThat(put(TestJwtSupport.token(host, "ADMIN"), request).statusCode()).isEqualTo(403);
    }

    @Test
    void aRangeWithoutInventoryReturns409NoInventory() throws Exception {
        UUID host = UUID.randomUUID();
        UUID roomType = roomTypeOwnedBy(host, 5);
        LocalDate farAway = LocalDate.now(ZoneOffset.UTC).plusDays(400);

        HttpResponse<String> response = put(hostToken(host), body(roomType, farAway, farAway.plusDays(2), 4));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("NO_INVENTORY");
    }

    @Test
    void invalidInputReturns400() throws Exception {
        UUID host = UUID.randomUUID();
        UUID roomType = roomTypeOwnedBy(host, 5);
        String token = hostToken(host);
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);

        HttpResponse<String> emptyRange = put(token, body(roomType, day0, day0, 4));
        HttpResponse<String> negative = put(token, body(roomType, day0, day0.plusDays(2), -1));
        HttpResponse<String> past = put(token, body(roomType, yesterday, yesterday.plusDays(3), 4));
        HttpResponse<String> missing = put(token, "{\"roomTypeId\":\"" + roomType + "\"}");

        assertThat(emptyRange.statusCode()).isEqualTo(400);
        assertThat(emptyRange.body()).contains("INVALID_STAY");
        assertThat(negative.statusCode()).isEqualTo(400);
        assertThat(negative.body()).contains("VALIDATION_ERROR");
        assertThat(past.statusCode()).isEqualTo(400);
        assertThat(past.body()).contains("INVALID_STAY");
        assertThat(missing.statusCode()).isEqualTo(400);
    }
}
