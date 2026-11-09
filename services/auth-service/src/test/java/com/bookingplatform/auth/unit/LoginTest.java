package com.bookingplatform.auth.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingplatform.auth.application.usecase.IssueSession;
import com.bookingplatform.auth.application.usecase.Login;
import com.bookingplatform.auth.domain.exception.InvalidCredentialsException;
import com.bookingplatform.auth.domain.exception.TooManyAttemptsException;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.IssuedToken;
import com.bookingplatform.auth.domain.model.Role;
import com.bookingplatform.auth.domain.model.Session;
import com.bookingplatform.auth.domain.port.CredentialRepository;
import com.bookingplatform.auth.domain.port.LoginAttemptLimiter;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginTest {

        private static final String IP = "203.0.113.7";

        private final CredentialRepository repository = mock(CredentialRepository.class);
        private final IssueSession issueSession = mock(IssueSession.class);
        private final LoginAttemptLimiter limiter = mock(LoginAttemptLimiter.class);
        private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        private final Login login = new Login(repository, encoder, limiter, issueSession);

        private Credential existingUser() {
                return Credential.register("user@example.com", encoder.encode("Passw0rd!2026"), Role.GUEST,
                                Instant.now());
        }

        private Session session() {
                return new Session(new IssuedToken("jwt", Instant.now().plusSeconds(900), 900), "refresh", 1_209_600);
        }

        @Test
        void returnsSessionForCorrectPasswordAndResetsEmailCounter() {
                Credential user = existingUser();
                Session expected = session();
                when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
                when(issueSession.execute(user)).thenReturn(expected);

                Session result = login.execute("  USER@example.com", "Passw0rd!2026", IP);

                assertThat(result).isSameAs(expected);
                verify(limiter).recordSuccess("user@example.com");
                verify(limiter, never()).recordFailure(any(), any());
        }

        @Test
        void wrongPasswordIsRejectedAndCounted() {
                when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser()));

                assertThatThrownBy(() -> login.execute("user@example.com", "sai-mat-khau", IP))
                                .isInstanceOf(InvalidCredentialsException.class);
                verify(limiter).recordFailure("user@example.com", IP);
                verify(issueSession, never()).execute(any());
        }

        @Test
        void unknownEmailGetsSameErrorAndIsCounted() {
                when(repository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> login.execute("nobody@example.com", "Passw0rd!2026", IP))
                                .isInstanceOf(InvalidCredentialsException.class)
                                .hasMessage("Email or password is not correct");
                verify(limiter).recordFailure("nobody@example.com", IP);
        }

        @Test
        void disabledAccountIsRejectedEvenWithCorrectPassword() {
                Credential disabled = mock(Credential.class);
                when(disabled.getPasswordHash()).thenReturn(encoder.encode("Passw0rd!2026"));
                when(disabled.isActive()).thenReturn(false);
                when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(disabled));

                assertThatThrownBy(() -> login.execute("user@example.com", "Passw0rd!2026", IP))
                                .isInstanceOf(InvalidCredentialsException.class);
                verify(issueSession, never()).execute(any());
        }

        @Test
        void blockedByLimiterStopsBeforeAnyLookup() {
                doThrow(new TooManyAttemptsException(60)).when(limiter).assertAllowed("user@example.com", IP);

                assertThatThrownBy(() -> login.execute("user@example.com", "Passw0rd!2026", IP))
                                .isInstanceOf(TooManyAttemptsException.class);
                verify(repository, never()).findByEmail(any());
        }
}