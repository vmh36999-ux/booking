package com.bookingplatform.inventory.infrastructure.persistence;

import com.bookingplatform.inventory.domain.model.NightSummary;
import com.bookingplatform.inventory.domain.port.NightInventory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/**
 * Tồn kho theo đêm, viết thẳng bằng SQL.
 *
 * Trái tim là câu UPDATE trong hold(): MỘT câu lệnh vừa KIỂM TRA còn đủ phòng
 * (total - held - booked >= qty) vừa TĂNG held.
 *
 * TUẦN 6 - KHÓA THEO THỨ TỰ: trước mỗi câu UPDATE nhiều đêm, ta khóa các dòng
 * theo thứ tự ngày tăng dần
 * (SELECT ... ORDER BY night FOR UPDATE). Hai giao dịch giữ các khoảng ngày
 * chồng lấn (1-3 và 2-4) đều khóa
 * theo cùng một chiều nên không thể chờ nhau theo vòng tròn (deadlock). Nếu
 * không có bước này, thứ tự khóa phụ
 * thuộc vào cách PostgreSQL quét bảng và có thể khác nhau giữa hai giao dịch.
 */
@Repository
public class JdbcNightInventory implements NightInventory {

  private static final String LOCK_SQL = """
      SELECT night FROM room_night
       WHERE room_type_id = ? AND night >= ? AND night < ?
       ORDER BY night
         FOR UPDATE
      """;

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

  /** Chỉ đổi đêm nào số giữ + số đặt không vượt mức mới. */
  private static final String SET_TOTAL_SQL = """
      UPDATE room_night
         SET total = ?
       WHERE room_type_id = ?
         AND night >= ? AND night < ?
         AND held + booked <= ?
      """;

  private static final String ABOVE_TOTAL_SQL = """
      SELECT night FROM room_night
       WHERE room_type_id = ? AND night >= ? AND night < ?
         AND held + booked > ?
       ORDER BY night
       LIMIT 10
      """;

  private final JdbcTemplate jdbc;

  public JdbcNightInventory(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public int hold(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int qty) {
    lockInOrder(roomTypeId, checkIn, checkOut);
    return jdbc.update(HOLD_SQL, qty, roomTypeId, checkIn, checkOut, qty);
  }

  @Override
  public int confirm(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int qty) {
    lockInOrder(roomTypeId, checkIn, checkOut);
    return jdbc.update(CONFIRM_SQL, qty, qty, roomTypeId, checkIn, checkOut);
  }

  @Override
  public int release(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int qty) {
    lockInOrder(roomTypeId, checkIn, checkOut);
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

  @Override
  public Map<UUID, NightSummary> summarize(Collection<UUID> roomTypeIds, LocalDate checkIn, LocalDate checkOut) {
    if (roomTypeIds.isEmpty()) {
      return Map.of();
    }
    String placeholders = String.join(", ", Collections.nCopies(roomTypeIds.size(), "?"));
    String sql = "SELECT room_type_id, count(*) AS nights, min(total - held - booked) AS min_free "
        + "FROM room_night WHERE room_type_id IN (" + placeholders + ") AND night >= ? AND night < ? "
        + "GROUP BY room_type_id";
    List<Object> args = new ArrayList<>(roomTypeIds);
    args.add(checkIn);
    args.add(checkOut);

    Map<UUID, NightSummary> result = new HashMap<>();
    jdbc.query(sql, (RowCallbackHandler) rs -> result.put(
        rs.getObject("room_type_id", UUID.class),
        new NightSummary(rs.getInt("nights"), rs.getInt("min_free"))), args.toArray());
    return result;
  }

  @Override
  public int setTotal(UUID roomTypeId, LocalDate from, LocalDate to, int totalRooms) {
    lockInOrder(roomTypeId, from, to);
    return jdbc.update(SET_TOTAL_SQL, totalRooms, roomTypeId, from, to, totalRooms);
  }

  @Override
  public List<LocalDate> findNightsAboveTotal(UUID roomTypeId, LocalDate from, LocalDate to, int totalRooms) {
    return jdbc.query(ABOVE_TOTAL_SQL, (rs, i) -> rs.getObject("night", LocalDate.class),
        roomTypeId, from, to, totalRooms);
  }

  /**
   * Khóa các đêm theo thứ tự ngày tăng dần. Phải gọi trong transaction (khóa được
   * nhả khi transaction kết thúc).
   */
  private void lockInOrder(UUID roomTypeId, LocalDate from, LocalDate to) {
    jdbc.query(LOCK_SQL, (rs, i) -> rs.getObject("night", LocalDate.class), roomTypeId, from, to);
  }
}
