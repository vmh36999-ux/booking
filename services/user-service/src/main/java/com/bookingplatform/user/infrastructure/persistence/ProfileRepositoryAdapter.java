package com.bookingplatform.user.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.bookingplatform.user.domain.model.Profile;
import com.bookingplatform.user.domain.port.ProfileRepository;

import jakarta.persistence.EntityManager;

@Repository
public class ProfileRepositoryAdapter implements ProfileRepository {
    private final SpringDataProfileRepository jpa;
    private final EntityManager entityManager;

    public ProfileRepositoryAdapter(SpringDataProfileRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public boolean exists(UUID userId) {
        return jpa.existsById(userId);
    }

    @Override
    public Optional<Profile> findById(UUID userId) {
        return jpa.findById(userId);
    }

    /** persist (không phải save) vì id đã gán sẵn: tránh một câu SELECT thừa. */
    @Override
    public Profile insert(Profile profile) {
        entityManager.persist(profile);
        entityManager.flush();
        return profile;
    }
}