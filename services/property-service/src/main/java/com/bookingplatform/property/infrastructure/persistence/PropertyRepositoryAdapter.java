package com.bookingplatform.property.infrastructure.persistence;

import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.port.PropertyRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class PropertyRepositoryAdapter implements PropertyRepository {

    private final SpringDataPropertyRepository jpa;
    private final EntityManager entityManager;

    public PropertyRepositoryAdapter(SpringDataPropertyRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    /** persist (không phải save) vì id đã gán sẵn: tránh một câu SELECT thừa. */
    @Override
    public Property insert(Property property) {
        entityManager.persist(property);
        entityManager.flush();
        return property;
    }

    @Override
    public Optional<Property> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<Property> findByHost(UUID hostId, UUID after, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        return after == null
                ? jpa.findByHostIdOrderById(hostId, page)
                : jpa.findByHostIdAndIdGreaterThanOrderById(hostId, after, page);
    }

    @Override
    public void delete(Property property) {
        entityManager.remove(property);
        entityManager.flush();
    }
}
