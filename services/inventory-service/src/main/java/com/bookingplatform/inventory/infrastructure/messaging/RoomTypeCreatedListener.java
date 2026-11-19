package com.bookingplatform.inventory.infrastructure.messaging;

import com.bookingplatform.events.EventTypes;
import com.bookingplatform.events.Topics;
import com.bookingplatform.inventory.application.usecase.InitializeInventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Nghe topic property.events. Chỉ quan tâm RoomTypeCreated, các loại sự kiện
 * khác trên topic này bỏ qua.
 */
@Component
public class RoomTypeCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(RoomTypeCreatedListener.class);

    private final ObjectMapper objectMapper;
    private final InitializeInventory initializeInventory;

    public RoomTypeCreatedListener(ObjectMapper objectMapper, InitializeInventory initializeInventory) {
        this.objectMapper = objectMapper;
        this.initializeInventory = initializeInventory;
    }

    @KafkaListener(topics = Topics.PROPERTY_EVENTS)
    public void onMessage(String json) {
        handle(json);
    }

    /** Tách riêng để unit test gọi trực tiếp, không cần Kafka. */
    public void handle(String json) {
        RoomTypeCreatedMessage message = objectMapper.readValue(json, RoomTypeCreatedMessage.class);
        if (!EventTypes.ROOM_TYPE_CREATED.equals(message.type())) {
            return;
        }
        boolean processed = initializeInventory.handle(
                message.eventId(), message.data().roomTypeId(), message.data().totalRooms());
        if (!processed) {
            log.info("Skipping already processed event: {}", message.eventId());
        }
    }
}
