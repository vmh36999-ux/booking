package com.bookingplatform.property.infrastructure.persistence;

import com.bookingplatform.property.domain.model.RoomType;
import com.bookingplatform.property.domain.port.RoomTypeRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RoomTypeRepositoryAdapter implements RoomTypeRepository {

    private final SpringDataRoomTypeRepository jpa;
    private final EntityManager entityManager;

    public RoomTypeRepositoryAdapter(SpringDataRoomTypeRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public RoomType insert(RoomType roomType) {
        entityManager.persist(roomType);
        entityManager.flush();
        return roomType;
    }

    @Override
    public Optional<RoomType> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<RoomType> findByProperty(UUID propertyId) {
        return jpa.findByPropertyIdOrderByCreatedAtAscIdAsc(propertyId);
    }

    @Override
    public long countByProperty(UUID propertyId) {
        return jpa.countByPropertyId(propertyId);
    }

    @Override
    public void delete(RoomType roomType) {
        entityManager.remove(roomType);
        entityManager.flush();
    }
}
