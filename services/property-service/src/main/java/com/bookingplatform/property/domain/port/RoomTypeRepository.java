package com.bookingplatform.property.domain.port;

import com.bookingplatform.property.domain.model.RoomType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomTypeRepository {

    RoomType insert(RoomType roomType);

    Optional<RoomType> findById(UUID id);

    /** Theo thứ tự tạo, ổn định giữa các lần gọi. */
    List<RoomType> findByProperty(UUID propertyId);

    long countByProperty(UUID propertyId);

    void delete(RoomType roomType);
}
