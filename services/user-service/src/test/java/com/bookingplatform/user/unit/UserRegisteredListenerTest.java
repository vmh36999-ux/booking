package com.bookingplatform.user.unit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bookingplatform.user.application.usecase.CreateProfileOnRegistration;
import com.bookingplatform.user.infrastructure.messaging.UserRegisteredListener;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UserRegisteredListenerTest {

    private final CreateProfileOnRegistration useCase = mock(CreateProfileOnRegistration.class);
    private final UserRegisteredListener listener = new UserRegisteredListener(JsonMapper.builder().build(), useCase);

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private String json(String type, String extraField) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"" + type + "\",\"version\":1,"
                + "\"occurredAt\":\"2026-11-10T03:22:11.123456Z\",\"aggregateId\":\"" + userId + "\","
                + "\"correlationId\":null," + extraField
                + "\"data\":{\"userId\":\"" + userId + "\",\"email\":\"a@example.com\",\"roles\":[\"GUEST\"]}}";
    }

    @Test
    void validEventCallsTheUseCase() {
        listener.handle(json("UserRegistered", ""));
        verify(useCase).handle(eventId, userId);
    }

    @Test
    void unknownExtraFieldsAreTolerated() {
        listener.handle(json("UserRegistered", "\"truongMoi\":\"gia-tri\","));
        verify(useCase).handle(eventId, userId);
    }

    @Test
    void otherEventTypesAreIgnored() {
        listener.handle(json("ProfileUpdated", ""));
        verifyNoInteractions(useCase);
    }

    @Test
    void malformedJsonThrowsSoTheErrorHandlerCanRetryAndDeadLetter() {
        assertThatThrownBy(() -> listener.handle("khong phai json"))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(useCase);
    }
}