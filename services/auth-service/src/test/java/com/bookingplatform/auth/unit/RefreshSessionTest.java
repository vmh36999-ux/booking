package com.bookingplatform.auth.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingplatform.auth.application.usecase.IssueSession;
import com.bookingplatform.auth.application.usecase.RefreshSession;
import com.bookingplatform.auth.domain.exception.InvalidRefreshTokenException;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.IssuedToken;
import com.bookingplatform.auth.domain.model.OpaqueTokens;
import com.bookingplatform.auth.domain.model.RefreshToken;
import com.bookingplatform.auth.domain.model.Session;
import com.bookingplatform.auth.domain.port.CredentialRepository;
import com.bookingplatform.auth.domain.port.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshSessionTest {

    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final CredentialRepository credentials = mock(CredentialRepository.class);
    private final IssueSession issueSession = mock(IssueSession.class);
    private final Clock clock = Clock.systemUTC();
    private final RefreshSession refreshSession = new RefreshSession(tokens, credentials, issueSession, clock);

    private final UUID userId = UUID.randomUUID();
    private final String raw = "raw-refresh-token";
    private final String hash = OpaqueTokens.hash(raw);

    private RefreshToken activeToken() {
        return RefreshToken.issue(userId, hash, clock.instant(), Duration.ofDays(14));
    }

    private Credential user(boolean active) {
        Credential credential = mock(Credential.class);
        when(credential.isActive()).thenReturn(active);
        return credential;
    }

    private Session newSession() {
        return new Session(new IssuedToken("new-jwt", Instant.now().plusSeconds(900), 900), "new-refresh", 1_209_600);
    }

    @Test
    void rotatesTokenWhenValid() {
        Credential credential = user(true);
        Session expected = newSession();
        when(tokens.findByTokenHash(hash)).thenReturn(Optional.of(activeToken()));
        when(tokens.revokeIfActive(hash)).thenReturn(1);
        when(credentials.findById(userId)).thenReturn(Optional.of(credential));
        when(issueSession.execute(credential)).thenReturn(expected);

        Session result = refreshSession.execute(raw);

        assertThat(result).isSameAs(expected);
        verify(tokens).revokeIfActive(hash);
        verify(tokens, never()).revokeAllForUser(any());
    }

    @Test
    void rejectsUnknownToken() {
        when(tokens.findByTokenHash(hash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshSession.execute(raw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(tokens, never()).revokeAllForUser(any());
    }

    @Test
    void reusedTokenRevokesAllSessionsOfThatUser() {
        RefreshToken used = activeToken();
        used.revoke();
        when(tokens.findByTokenHash(hash)).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> refreshSession.execute(raw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(tokens).revokeAllForUser(userId);
        verify(issueSession, never()).execute(any());
    }

    @Test
    void rejectsExpiredToken() {
        RefreshToken expired = RefreshToken.issue(userId, hash, clock.instant().minus(Duration.ofDays(2)),
                Duration.ofDays(1));
        when(tokens.findByTokenHash(hash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshSession.execute(raw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(tokens, never()).revokeIfActive(any());
    }

    @Test
    void losingTheRaceRevokesAllSessions() {
        when(tokens.findByTokenHash(hash)).thenReturn(Optional.of(activeToken()));
        when(tokens.revokeIfActive(hash)).thenReturn(0);

        assertThatThrownBy(() -> refreshSession.execute(raw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(tokens).revokeAllForUser(userId);
    }

    @Test
    void rejectsDisabledAccount() {
        when(tokens.findByTokenHash(hash)).thenReturn(Optional.of(activeToken()));
        when(tokens.revokeIfActive(hash)).thenReturn(1);
        when(credentials.findById(userId)).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> refreshSession.execute(raw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(issueSession, never()).execute(any());
    }

    @Test
    void rejectsWhenAccountNoLongerExists() {
        when(tokens.findByTokenHash(hash)).thenReturn(Optional.of(activeToken()));
        when(tokens.revokeIfActive(hash)).thenReturn(1);
        when(credentials.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshSession.execute(raw)).isInstanceOf(InvalidRefreshTokenException.class);
    }
}