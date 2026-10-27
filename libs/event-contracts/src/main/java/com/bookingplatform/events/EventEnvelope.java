package com.bookingplatform.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Phong bì chung cho mọi sự kiện đi qua Kafka.
 * data: payload riêng của từng loại sự kiện.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        T data) {

    public static <T> EventEnvelope<T> of(String type, String aggregateId, String correlationId, T data) {
        return new EventEnvelope<>(UUID.randomUUID(), type, 1, Instant.now(), aggregateId, correlationId, data);
    }
}
