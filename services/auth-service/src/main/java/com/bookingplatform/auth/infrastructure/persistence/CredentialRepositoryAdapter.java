package com.bookingplatform.auth.infrastructure.persistence;

import com.bookingplatform.auth.domain.exception.EmailAlreadyUsedException;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.port.CredentialRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class CredentialRepositoryAdapter implements CredentialRepository {

    private final SpringDataCredentialRepository jpa;
    private final EntityManager entityManager;

    public CredentialRepositoryAdapter(SpringDataCredentialRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    /**
     * Dùng persist thay vì save: id đã gán sẵn nên save() sẽ chạy thêm một SELECT
     * (merge).
     * flush() ngay để lỗi trùng email (hai request đua nhau) lộ ra tại đây.
     */
    @Override
    public Credential save(Credential credential) {
        try {
            entityManager.persist(credential);
            entityManager.flush();
            return credential;
        } catch (PersistenceException e) {
            if (isConstraintViolation(e)) {
                throw new EmailAlreadyUsedException("Email is already in use");
            }
            throw e;
        }
    }

    @Override
    public Optional<Credential> findByEmail(String normalizedEmail) {
        return jpa.findByEmail(normalizedEmail);
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return jpa.existsByEmail(normalizedEmail);
    }

    private static boolean isConstraintViolation(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException) {
                return true;
            }
        }
        return false;
    }
}