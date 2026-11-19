package com.bookingplatform.inventory.infrastructure.persistence;

import com.bookingplatform.inventory.domain.port.NightInventory;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tồn kho theo đêm, viết thẳng bằng SQL để thấy rõ từng câu lệnh.
 *
 * Trái tim của dịch vụ là câu UPDATE trong hold(): MỘT câu lệnh duy nhất vừa
 * KIỂM TRA còn đủ phòng
 * (điều kiện total - held - booked >= qty) vừa TĂNG held. Postgres khóa từng
 * dòng khi cập nhật, nên hai người
 * cùng giữ phòng cuối cùng thì người đến sau phải chờ, rồi thấy hết phòng và
 * không cập nhật dòng nào.
 */
@Repository
public class JdbcNightInventory implements NightInventory {

    private static final String HOLD_SQL = """
            UPDATE room_night
               SET held = held + ?
             WHERE room_type_id = ?
               AND night >= ? AND night < ?
               AND total - held - booked >= ?
            """;

    private static final String CONFIRM_SQL = """
            UPDATE room_night
               SET held = held - ?, booked = booked + ?
             WHERE room_type_id = ?
               AND night >= ? AND night < ?
            """;

    private static final String RELEASE_SQL = """
            UPDATE room_night
               SET held = held - ?
             WHERE room_type_id = ?
               AND night >= ? AND night < ?
            """;

    private static final String COUNT_SQL = """
            SELECT count(*) FROM room_night
             WHERE room_type_id = ? AND night >= ? AND night < ?
            """;

    /**
     * generate_series(0, days-1) sinh các số 0..days-1; "ngày + số" ra ngày tiếp
     * theo, không phụ thuộc múi giờ.
     */
    private static final String CREATE_SQL = """
            INSERT INTO room_night (room_type_id, night, total)
            SELECT ?::uuid, (?::date + g), ?::int
              FROM generate_series(0, ?::int - 1) AS g
            ON CONFLICT (room_type_id, night) DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public JdbcNightInventory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int hold(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int qty) {
        return jdbc.update(HOLD_SQL, qty, roomTypeId, checkIn, checkOut, qty);
    }

    @Override
    public int confirm(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int qty) {
        return jdbc.update(CONFIRM_SQL, qty, qty, roomTypeId, checkIn, checkOut);
    }

    @Override
    public int release(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int qty) {
        return jdbc.update(RELEASE_SQL, qty, roomTypeId, checkIn, checkOut);
    }

    @Override
    public int countNights(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        Integer count = jdbc.queryForObject(COUNT_SQL, Integer.class, roomTypeId, checkIn, checkOut);
        return count == null ? 0 : count;
    }

    @Override
    public void createNights(UUID roomTypeId, LocalDate from, int days, int totalRooms) {
        jdbc.update(CREATE_SQL, roomTypeId, from, totalRooms, days);
    }
}
