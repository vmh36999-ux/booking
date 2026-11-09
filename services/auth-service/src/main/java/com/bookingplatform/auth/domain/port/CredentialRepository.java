package com.bookingplatform.auth.domain.port;

import com.bookingplatform.auth.domain.model.Credential;
import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository {

    /**
     * Throws EmailAlreadyUsedException if the email already exists (even if two
     * requests race).
     */
    Credential save(Credential credential);

    Optional<Credential> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    Optional<Credential> findById(UUID userId);
}