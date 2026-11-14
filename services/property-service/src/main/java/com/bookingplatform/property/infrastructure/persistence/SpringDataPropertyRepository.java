package com.bookingplatform.property.infrastructure.persistence;

import com.bookingplatform.property.domain.model.Property;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPropertyRepository extends JpaRepository<Property, UUID> {

    List<Property> findByHostIdOrderById(UUID hostId, Pageable pageable);

    List<Property> findByHostIdAndIdGreaterThanOrderById(UUID hostId, UUID id, Pageable pageable);
}
