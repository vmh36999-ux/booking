-- Authentication schema for Week 2.
-- V1 already creates outbox and processed_event.

CREATE TABLE credential (
    user_id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_credential_email UNIQUE (email),
    CONSTRAINT ck_credential_email_lower
        CHECK (email = lower(email)),
    CONSTRAINT ck_credential_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- Roles assigned to credentials.
-- GUEST and HOST can be requested during registration.
-- ADMIN must never be self-assigned by a public registration request.
CREATE TABLE user_role (
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,

    CONSTRAINT pk_user_role
        PRIMARY KEY (user_id, role),

    CONSTRAINT fk_user_role_credential
        FOREIGN KEY (user_id)
        REFERENCES credential(user_id)
        ON DELETE CASCADE,

    CONSTRAINT ck_user_role_name
        CHECK (role IN ('GUEST', 'HOST', 'ADMIN'))
);

CREATE INDEX idx_user_role_role
    ON user_role(role);

-- Refresh tokens are implemented in Week 3.
-- Store token hashes, never raw refresh tokens.
CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_refresh_token_credential
        FOREIGN KEY (user_id)
        REFERENCES credential(user_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_user
    ON refresh_token(user_id);

CREATE INDEX idx_refresh_token_expiry
    ON refresh_token(expires_at);
