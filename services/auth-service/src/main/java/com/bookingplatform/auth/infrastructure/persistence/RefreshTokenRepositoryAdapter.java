package com.bookingplatform.auth.infrastructure.persistence;

import com.bookingplatform.auth.domain.model.RefreshToken;
import com.bookingplatform.auth.domain.port.RefreshTokenRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository jpa;
    private final EntityManager entityManager;

    public RefreshTokenRepositoryAdapter(SpringDataRefreshTokenRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        entityManager.persist(token);
        entityManager.flush();
        return token;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash);
    }

    @Override
    public int revokeIfActive(String tokenHash) {
        return jpa.revokeIfActive(tokenHash);
    }

    @Override
    public int revokeAllForUser(UUID userId) {
        return jpa.revokeAllByUserId(userId);
    }
}