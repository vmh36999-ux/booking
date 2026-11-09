-- Tuần 3: hồ sơ người dùng của user-service.
-- Dùng TIMESTAMPTZ để khớp kiểu java.time.Instant của Hibernate (ddl-auto: validate).

CREATE TABLE profile (
    user_id    UUID         PRIMARY KEY,
    full_name  VARCHAR(200),
    phone      VARCHAR(30),
    country    VARCHAR(2),
    language   VARCHAR(10),
    currency   VARCHAR(3),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Chưa có API trong tuần 3. Tạo sẵn để schema ổn định.
CREATE TABLE saved_property (
    user_id     UUID        NOT NULL REFERENCES profile (user_id) ON DELETE CASCADE,
    property_id UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, property_id)
);