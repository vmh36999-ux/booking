package com.bookingplatform.inventory.infrastructure.persistence;

import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldStatus;
import com.bookingplatform.inventory.domain.port.HoldRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHoldRepository implements HoldRepository {

    private static final String INSERT_SQL = """
            INSERT INTO room_hold (id, booking_id, room_type_id, check_in, check_out, qty, status,
                                   expires_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (booking_id) DO NOTHING
            """;

    private static final String SELECT = """
            SELECT id, booking_id, room_type_id, check_in, check_out, qty, status, expires_at, created_at, updated_at
              FROM room_hold
            """;

    private static final RowMapper<Hold> MAPPER = (rs, i) -> new Hold(
            rs.getObject("id", UUID.class),
            rs.getObject("booking_id", UUID.class),
            rs.getObject("room_type_id", UUID.class),
            rs.getObject("check_in", LocalDate.class),
            rs.getObject("check_out", LocalDate.class),
            rs.getInt("qty"),
            HoldStatus.valueOf(rs.getString("status")),
            rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbc;

    public JdbcHoldRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * ON CONFLICT (booking_id) DO NOTHING: nếu bookingId đã có thì không chèn và
     * trả 0 dòng.
     * Hai request cùng bookingId đua nhau: bên đến sau CHỜ bên trước kết thúc, rồi
     * thấy đã có và trả về rỗng.
     */
    @Override
    public Optional<Hold> insertIfAbsent(Hold hold) {
        int inserted = jdbc.update(INSERT_SQL, hold.id(), hold.bookingId(), hold.roomTypeId(), hold.checkIn(),
                hold.checkOut(), hold.qty(), hold.status().name(), ts(hold.expiresAt()), ts(hold.createdAt()),
                ts(hold.updatedAt()));
        return inserted == 1 ? Optional.of(hold) : Optional.empty();
    }

    @Override
    public Optional<Hold> findById(UUID id) {
        return one(SELECT + " WHERE id = ?", id);
    }

    @Override
    public Optional<Hold> findByBookingId(UUID bookingId) {
        return one(SELECT + " WHERE booking_id = ?", bookingId);
    }

    @Override
    public int transition(UUID id, HoldStatus from, HoldStatus to, Instant now) {
        return jdbc.update("UPDATE room_hold SET status = ?, updated_at = ? WHERE id = ? AND status = ?",
                to.name(), ts(now), id, from.name());
    }

    private Optional<Hold> one(String sql, Object arg) {
        List<Hold> rows = jdbc.query(sql, MAPPER, arg);
        return rows.stream().findFirst();
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
