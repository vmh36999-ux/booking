package com.bookingplatform.inventory.unit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bookingplatform.inventory.application.usecase.InitializeInventory;
import com.bookingplatform.inventory.infrastructure.messaging.RoomTypeCreatedListener;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RoomTypeCreatedListenerTest {

    private final InitializeInventory useCase = mock(InitializeInventory.class);
    private final RoomTypeCreatedListener listener = new RoomTypeCreatedListener(JsonMapper.builder().build(), useCase);

    private final UUID eventId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID hostId = UUID.randomUUID();
    private final UUID roomTypeId = UUID.randomUUID();

    private String json(String type, String extraField, boolean withHostId) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"" + type + "\",\"version\":1,"
                + "\"occurredAt\":\"2026-11-23T03:22:11.123456Z\",\"aggregateId\":\"" + propertyId + "\","
                + "\"correlationId\":null," + extraField
                + "\"data\":{\"propertyId\":\"" + propertyId + "\","
                + (withHostId ? "\"hostId\":\"" + hostId + "\"," : "")
                + "\"roomTypeId\":\"" + roomTypeId + "\","
                + "\"name\":\"Deluxe\",\"maxGuests\":2,\"bedConfig\":\"1 giuong doi\",\"totalRooms\":12,"
                + "\"basePriceMinor\":1200000,\"currency\":\"VND\"}}";
    }

    @Test
    void roomTypeCreatedInitializesInventoryAndPassesTheOwner() {
        listener.handle(json("RoomTypeCreated", "", true));
        verify(useCase).handle(eventId, roomTypeId, 12, propertyId, hostId);
    }

    @Test
    void anOldEventWithoutHostIdStillInitializesInventoryWithANullOwner() {
        listener.handle(json("RoomTypeCreated", "", false));
        verify(useCase).handle(eventId, roomTypeId, 12, propertyId, null);
    }

    @Test
    void unknownExtraFieldsAreTolerated() {
        listener.handle(json("RoomTypeCreated", "\"truongMoi\":\"gia-tri\",", true));
        verify(useCase).handle(eventId, roomTypeId, 12, propertyId, hostId);
    }

    @Test
    void otherEventTypesOnTheSameTopicAreIgnored() {
        listener.handle(json("PropertyPublished", "", true));
        verifyNoInteractions(useCase);
    }

    @Test
    void malformedJsonThrowsSoTheErrorHandlerCanRetryAndDeadLetter() {
        assertThatThrownBy(() -> listener.handle("khong phai json")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(useCase);
    }
}
