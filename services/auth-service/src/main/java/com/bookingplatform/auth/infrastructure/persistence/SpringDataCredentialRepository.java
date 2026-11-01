package com.bookingplatform.auth.infrastructure.persistence;

import com.bookingplatform.auth.domain.model.Credential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCredentialRepository extends JpaRepository<Credential, UUID> {

    Optional<Credential> findByEmail(String email);

    boolean existsByEmail(String email);
}