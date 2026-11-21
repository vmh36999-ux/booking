package com.bookingplatform.inventory.infrastructure.persistence;

import com.bookingplatform.inventory.domain.port.RoomTypeOwners;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRoomTypeOwners implements RoomTypeOwners {

    private final JdbcTemplate jdbc;

    public JdbcRoomTypeOwners(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void register(UUID roomTypeId, UUID propertyId, UUID hostId) {
        jdbc.update("INSERT INTO room_type_owner (room_type_id, property_id, host_id) VALUES (?, ?, ?) "
                + "ON CONFLICT (room_type_id) DO NOTHING", roomTypeId, propertyId, hostId);
    }

    @Override
    public Optional<UUID> findHostId(UUID roomTypeId) {
        List<UUID> rows = jdbc.query("SELECT host_id FROM room_type_owner WHERE room_type_id = ?",
                (rs, i) -> rs.getObject("host_id", UUID.class), roomTypeId);
        return rows.stream().findFirst();
    }
}
