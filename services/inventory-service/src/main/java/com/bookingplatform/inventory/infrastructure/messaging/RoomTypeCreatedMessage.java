package com.bookingplatform.inventory.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Phần của sự kiện RoomTypeCreated mà inventory-service CẦN đọc. Mỗi consumer
 * tự định nghĩa phần mình cần,
 * trường nào thừa thì bỏ qua (ignoreUnknown): property-service thêm trường mới
 * cũng không làm inventory hỏng.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomTypeCreatedMessage(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(UUID propertyId, UUID hostId, UUID roomTypeId, String name, int maxGuests, int totalRooms) {
    }
}
