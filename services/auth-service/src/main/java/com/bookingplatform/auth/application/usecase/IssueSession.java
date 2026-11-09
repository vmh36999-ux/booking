package com.bookingplatform.auth.application.usecase;

import java.time.Instant;
import java.time.Clock;

import org.springframework.stereotype.Service;

import com.bookingplatform.auth.config.RefreshTokenProperties;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.IssuedToken;
import com.bookingplatform.auth.domain.model.OpaqueTokens;
import com.bookingplatform.auth.domain.model.RefreshToken;
import com.bookingplatform.auth.domain.model.Session;
import com.bookingplatform.auth.domain.port.RefreshTokenRepository;
import com.bookingplatform.auth.domain.port.TokenIssuer;

/**
 * Tạo một phiên đăng nhập mới: access token (JWT) và refresh token (lưu băm vào
 * DB).
 */
@Service
public class IssueSession {
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    public IssueSession(TokenIssuer tokenIssuer, RefreshTokenRepository refreshTokens,
            RefreshTokenProperties properties, Clock clock) {
        this.tokenIssuer = tokenIssuer;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
        this.clock = clock;
    }

    /** Chạy trong transaction của nơi gọi (Login hoặc RefreshSession). */
    public Session execute(Credential credential) {
        IssuedToken access = tokenIssuer.issue(credential);
        String rawRefreshToken = OpaqueTokens.generate();
        Instant now = clock.instant();
        refreshTokens.save(
                RefreshToken.issue(credential.getUserId(), OpaqueTokens.hash(rawRefreshToken), now, properties.ttl()));
        return new Session(access, rawRefreshToken, properties.ttl().toSeconds());
    }
}
