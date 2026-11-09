package com.bookingplatform.auth.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingplatform.events.EventEnvelope;
import com.bookingplatform.events.EventTypes;
import com.bookingplatform.events.Topics;
import com.bookingplatform.events.user.UserRegistered;
import com.bookingplatform.auth.application.usecase.RegisterUser;
import com.bookingplatform.auth.domain.exception.EmailAlreadyUsedException;
import com.bookingplatform.auth.domain.exception.InvalidRegistrationException;
import com.bookingplatform.auth.domain.model.AccountType;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.Role;
import com.bookingplatform.auth.domain.port.CredentialRepository;
import com.bookingplatform.auth.domain.port.OutboxWriter;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegisterUserTest {

        private final CredentialRepository repository = mock(CredentialRepository.class);
        private final OutboxWriter outbox = mock(OutboxWriter.class);
        private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        private final RegisterUser registerUser = new RegisterUser(repository, encoder, outbox, Clock.systemUTC());

        RegisterUserTest() {
                when(repository.save(any(Credential.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void normalizesEmailAndHashesPassword() {
                Credential result = registerUser.execute("  Test.User@Example.COM ", "Passw0rd!2026",
                                AccountType.GUEST);

                assertThat(result.getEmail()).isEqualTo("test.user@example.com");
                assertThat(result.getPasswordHash()).isNotEqualTo("Passw0rd!2026");
                assertThat(encoder.matches("Passw0rd!2026", result.getPasswordHash())).isTrue();
                assertThat(result.getRoles()).containsExactly(Role.GUEST);
                assertThat(result.isActive()).isTrue();
        }

        @Test
        void hostAccountGetsHostRole() {
                Credential result = registerUser.execute("host@example.com", "Passw0rd!2026", AccountType.HOST);
                assertThat(result.getRoles()).containsExactly(Role.HOST);
        }

        @Test
        void missingAccountTypeDefaultsToGuest() {
                Credential result = registerUser.execute("a@example.com", "Passw0rd!2026", null);
                assertThat(result.getRoles()).containsExactly(Role.GUEST);
        }

        @Test
        void writesUserRegisteredEventToOutbox() {
                Credential result = registerUser.execute("Event@Example.com", "Passw0rd!2026", AccountType.HOST);

                @SuppressWarnings({ "unchecked", "rawtypes" })
                ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass((Class) EventEnvelope.class);
                verify(outbox).write(eq(Topics.USER_EVENTS), captor.capture());

                EventEnvelope<?> event = captor.getValue();
                assertThat(event.type()).isEqualTo(EventTypes.USER_REGISTERED);
                assertThat(event.aggregateId()).isEqualTo(result.getUserId().toString());
                assertThat(event.eventId()).isNotNull();
                assertThat(event.data()).isInstanceOf(UserRegistered.class);
                UserRegistered payload = (UserRegistered) event.data();
                assertThat(payload.userId()).isEqualTo(result.getUserId());
                assertThat(payload.email()).isEqualTo("event@example.com");
                assertThat(payload.roles()).containsExactly("HOST");
        }

        @Test
        void rejectsDuplicateEmailAndWritesNothing() {
                when(repository.existsByEmail("dup@example.com")).thenReturn(true);

                assertThatThrownBy(() -> registerUser.execute("DUP@example.com", "Passw0rd!2026", AccountType.GUEST))
                                .isInstanceOf(EmailAlreadyUsedException.class);
                verify(repository, never()).save(any());
                verify(outbox, never()).write(any(), any());
        }

        @Test
        void rejectsPasswordLongerThan72Bytes() {
                String tooLong = "a".repeat(73);
                assertThatThrownBy(() -> registerUser.execute("a@example.com", tooLong, AccountType.GUEST))
                                .isInstanceOf(InvalidRegistrationException.class);
                verify(outbox, never()).write(any(), any());
        }
}