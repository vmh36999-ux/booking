-- Hai bảng hạ tầng cho mẫu Outbox và Idempotent consumer.
-- Các bảng nghiệp vụ thêm ở V2, V3, ...

CREATE TABLE outbox (
    id           UUID PRIMARY KEY,
    aggregate_id UUID         NOT NULL,
    type         VARCHAR(100) NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    published_at TIMESTAMP
);
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT now()
);
