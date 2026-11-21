package com.bookingplatform.inventory.domain.port;

import java.util.Optional;
import java.util.UUID;

/** Sổ ghi loại phòng nào thuộc host nào. */
public interface RoomTypeOwners {

    /** Ghi nhớ chủ của loại phòng. Đã có thì bỏ qua (không ghi đè). */
    void register(UUID roomTypeId, UUID propertyId, UUID hostId);

    Optional<UUID> findHostId(UUID roomTypeId);
}
