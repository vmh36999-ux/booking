package com.bookingplatform.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingplatform.inventory.application.usecase.HoldRooms;
import com.bookingplatform.inventory.application.usecase.InitializeInventory;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * Gọi API tìm phòng trống hàng loạt qua HTTP thật với Postgres thật. Cần Docker
 * đang chạy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.listener.auto-startup=false", "app.inventory.expiry.enabled=false" })
@Import(TestJwtConfig.class)
@Testcontainers
class AvailabilityApiIntegrationTest {

    private static final String SEARCH = "/api/v1/inventory/availability/search";

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
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final LocalDate checkIn = LocalDate.now(ZoneOffset.UTC).plusDays(10);
    private final LocalDate checkOut = checkIn.plusDays(3);

    // ----------------------------------------------------------------- tiện ích

    private HttpResponse<String> post(String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + SEARCH))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.POST(HttpRequest.BodyPublishers.ofString(body));
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

    private void hold(UUID roomType, int fromOffset, int toOffset, int qty) {
        holdRooms.execute(new HoldCommand(UUID.randomUUID(), roomType, checkIn.plusDays(fromOffset),
                checkIn.plusDays(toOffset), qty));
    }

    private String body(List<UUID> ids, LocalDate in, LocalDate out, Integer qty) {
        String idList = ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        return "{\"roomTypeIds\":[" + idList + "],\"checkIn\":\"" + in + "\",\"checkOut\":\"" + out + "\""
                + (qty == null ? "" : ",\"qty\":" + qty) + "}";
    }

    // ----------------------------------------------------------------- bài test

    @Test
    void reportsFreeRoomsAndWhetherTheRequestedQuantityFits() throws Exception {
        UUID roomA = roomTypeWith(3);
        hold(roomA, 0, 3, 2); // còn 1 phòng mỗi đêm
        UUID roomB = roomTypeWith(1);
        hold(roomB, 0, 3, 1); // hết sạch
        UUID roomC = UUID.randomUUID(); // chưa có tồn kho

        HttpResponse<String> oneRoom = post(admin(), body(List.of(roomA), checkIn, checkOut, 1));
        assertThat(oneRoom.statusCode()).isEqualTo(200);
        assertThat(oneRoom.body()).contains("\"available\":true").contains("\"availableRooms\":1")
                .contains("\"hasInventory\":true");

        HttpResponse<String> twoRooms = post(admin(), body(List.of(roomA), checkIn, checkOut, 2));
        assertThat(twoRooms.body()).contains("\"available\":false").contains("\"availableRooms\":1");

        HttpResponse<String> soldOut = post(admin(), body(List.of(roomB), checkIn, checkOut, 1));
        assertThat(soldOut.body()).contains("\"available\":false").contains("\"availableRooms\":0");

        HttpResponse<String> noInventory = post(admin(), body(List.of(roomC), checkIn, checkOut, 1));
        assertThat(noInventory.body()).contains("\"hasInventory\":false").contains("\"available\":false");
    }

    @Test
    void oneCallAnswersForManyRoomTypesInTheRequestOrder() throws Exception {
        UUID roomA = roomTypeWith(3);
        UUID roomB = roomTypeWith(3);
        UUID roomC = UUID.randomUUID();

        HttpResponse<String> response = post(admin(), body(List.of(roomB, roomA, roomC), checkIn, checkOut, null));

        assertThat(response.statusCode()).isEqualTo(200);
        String text = response.body();
        assertThat(text).contains(roomA.toString()).contains(roomB.toString()).contains(roomC.toString());
        assertThat(text.indexOf(roomB.toString())).isLessThan(text.indexOf(roomA.toString()));
        assertThat(text.indexOf(roomA.toString())).isLessThan(text.indexOf(roomC.toString()));
        assertThat(text).contains("\"nights\":3").contains("\"qty\":1");
    }

    @Test
    void theTightestNightDecidesTheAnswer() throws Exception {
        UUID room = roomTypeWith(3);
        hold(room, 1, 2, 3); // chỉ đêm giữa hết phòng

        HttpResponse<String> wholeStay = post(admin(), body(List.of(room), checkIn, checkOut, 1));
        assertThat(wholeStay.body()).contains("\"available\":false").contains("\"availableRooms\":0");

        HttpResponse<String> firstNightOnly = post(admin(), body(List.of(room), checkIn, checkIn.plusDays(1), 1));
        assertThat(firstNightOnly.body()).contains("\"available\":true").contains("\"availableRooms\":3");
    }

    @Test
    void searchingNeverChangesAnything() throws Exception {
        UUID room = roomTypeWith(3);
        hold(room, 0, 3, 1);
        Integer heldBefore = jdbc.queryForObject("SELECT sum(held) FROM room_night WHERE room_type_id = ?",
                Integer.class, room);
        Integer holdsBefore = jdbc.queryForObject("SELECT count(*) FROM room_hold", Integer.class);

        post(admin(), body(List.of(room), checkIn, checkOut, 2));
        post(admin(), body(List.of(room), checkIn, checkOut, 2));

        assertThat(jdbc.queryForObject("SELECT sum(held) FROM room_night WHERE room_type_id = ?", Integer.class, room))
                .isEqualTo(heldBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_hold", Integer.class)).isEqualTo(holdsBefore);
    }

    @Test
    void onlyServiceOrAdminRolesMaySearch() throws Exception {
        String request = body(List.of(roomTypeWith(2)), checkIn, checkOut, 1);

        assertThat(post(null, request).statusCode()).isEqualTo(401);
        assertThat(post(TestJwtSupport.token(UUID.randomUUID(), "GUEST"), request).statusCode()).isEqualTo(403);
        assertThat(post(TestJwtSupport.token(UUID.randomUUID(), "HOST"), request).statusCode()).isEqualTo(403);
        assertThat(post(TestJwtSupport.token(UUID.randomUUID(), "SERVICE"), request).statusCode()).isEqualTo(200);
    }

    @Test
    void invalidRequestsReturn400() throws Exception {
        UUID room = roomTypeWith(2);
        HttpResponse<String> emptyList = post(admin(), body(List.of(), checkIn, checkOut, 1));
        HttpResponse<String> missingDates = post(admin(), "{\"roomTypeIds\":[\"" + room + "\"]}");
        HttpResponse<String> badQty = post(admin(), body(List.of(room), checkIn, checkOut, 0));
        HttpResponse<String> reversed = post(admin(), body(List.of(room), checkOut, checkIn, 1));
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        HttpResponse<String> past = post(admin(), body(List.of(room), yesterday, yesterday.plusDays(2), 1));

        assertThat(emptyList.statusCode()).isEqualTo(400);
        assertThat(emptyList.body()).contains("VALIDATION_ERROR");
        assertThat(missingDates.statusCode()).isEqualTo(400);
        assertThat(badQty.statusCode()).isEqualTo(400);
        assertThat(reversed.statusCode()).isEqualTo(400);
        assertThat(reversed.body()).contains("INVALID_STAY");
        assertThat(past.statusCode()).isEqualTo(400);
        assertThat(past.body()).contains("INVALID_STAY");
    }
}
