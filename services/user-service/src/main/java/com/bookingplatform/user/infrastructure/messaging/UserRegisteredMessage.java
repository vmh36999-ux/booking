package com.bookingplatform.user.infrastructure.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Phần của sự kiện UserRegistered mà user-service CẦN đọc. Mỗi consumer tự định
 * nghĩa phần mình cần,
 * trường nào thừa thì bỏ qua (ignoreUnknown): nhờ vậy producer thêm trường mới
 * không làm consumer hỏng.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserRegisteredMessage(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(UUID userId, String email, List<String> roles) {
    }
}
