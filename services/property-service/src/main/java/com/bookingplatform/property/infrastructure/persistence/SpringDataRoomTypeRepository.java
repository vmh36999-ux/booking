package com.bookingplatform.property.infrastructure.persistence;

import com.bookingplatform.property.domain.model.RoomType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataRoomTypeRepository extends JpaRepository<RoomType, UUID> {

    List<RoomType> findByPropertyIdOrderByCreatedAtAscIdAsc(UUID propertyId);

    long countByPropertyId(UUID propertyId);
}
