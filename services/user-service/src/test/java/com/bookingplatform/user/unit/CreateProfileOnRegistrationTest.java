package com.bookingplatform.user.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingplatform.user.application.usecase.CreateProfileOnRegistration;
import com.bookingplatform.user.domain.model.Profile;
import com.bookingplatform.user.domain.port.ProcessedEventRepository;
import com.bookingplatform.user.domain.port.ProfileRepository;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateProfileOnRegistrationTest {

    private final ProcessedEventRepository processedEvents = mock(ProcessedEventRepository.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final CreateProfileOnRegistration useCase = new CreateProfileOnRegistration(processedEvents, profiles,
            Clock.systemUTC());

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void firstTimeCreatesAnEmptyProfile() {
        when(processedEvents.markProcessed(eventId)).thenReturn(true);
        when(profiles.exists(userId)).thenReturn(false);

        boolean processed = useCase.handle(eventId, userId);

        assertThat(processed).isTrue();
        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getFullName()).isNull();
    }

    @Test
    void duplicateEventIsIgnored() {
        when(processedEvents.markProcessed(eventId)).thenReturn(false);

        boolean processed = useCase.handle(eventId, userId);

        assertThat(processed).isFalse();
        verify(profiles, never()).exists(any());
        verify(profiles, never()).insert(any());
    }

    @Test
    void existingProfileIsNotCreatedAgain() {
        when(processedEvents.markProcessed(eventId)).thenReturn(true);
        when(profiles.exists(userId)).thenReturn(true);

        boolean processed = useCase.handle(eventId, userId);

        assertThat(processed).isTrue();
        verify(profiles, never()).insert(any());
    }
}