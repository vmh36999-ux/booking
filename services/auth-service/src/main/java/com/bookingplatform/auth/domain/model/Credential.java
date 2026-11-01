package com.bookingplatform.auth.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Credential {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CredentialStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Credential register(
            String normalizedEmail,
            String passwordHash,
            Role role,
            Instant now) {
        Credential credential = new Credential();
        credential.userId = UUID.randomUUID();
        credential.email = normalizedEmail;
        credential.passwordHash = passwordHash;
        credential.status = CredentialStatus.ACTIVE;
        credential.roles.add(role);
        credential.createdAt = now;

        return credential;
    }

    public boolean isActive() {
        return status == CredentialStatus.ACTIVE;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}