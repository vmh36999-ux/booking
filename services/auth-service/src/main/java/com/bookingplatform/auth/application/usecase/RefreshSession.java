package com.bookingplatform.auth.application.usecase;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingplatform.auth.domain.exception.InvalidRefreshTokenException;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.OpaqueTokens;
import com.bookingplatform.auth.domain.model.RefreshToken;
import com.bookingplatform.auth.domain.model.Session;
import com.bookingplatform.auth.domain.port.CredentialRepository;
import com.bookingplatform.auth.domain.port.RefreshTokenRepository;

/**
 * Đổi refresh token cũ lấy cặp token mới (xoay vòng). Mỗi refresh token chỉ
 * dùng được MỘT lần.
 * Nếu một token đã dùng rồi mà lại xuất hiện (nghi bị đánh cắp), thu hồi TOÀN
 * BỘ token của user đó.
 */
@Service
public class RefreshSession {
    private final RefreshTokenRepository refreshTokens;
    private final CredentialRepository credentials;
    private final IssueSession issueSession;
    private final Clock clock;

    public RefreshSession(RefreshTokenRepository refreshTokens, CredentialRepository credentials,
            IssueSession issueSession, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.credentials = credentials;
        this.issueSession = issueSession;
        this.clock = clock;
    }

    /**
     * noRollbackFor: khi phát hiện dùng lại token, ta vừa thu hồi hàng loạt rồi mới
     * ném lỗi.
     * Nếu transaction rollback theo lỗi thì việc thu hồi bị hủy theo, mất tác dụng.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Session execute(String rawRefreshToken) {
        String hash = OpaqueTokens.hash(rawRefreshToken);
        RefreshToken token = refreshTokens.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);

        if (token.isRevoked()) {
            refreshTokens.revokeAllForUser(token.getUserId());
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpired(clock.instant())) {
            throw new InvalidRefreshTokenException();
        }
        // Chốt chặn nguyên tử: hai request cùng dùng một token thì chỉ một request thu
        // hồi thành công
        if (refreshTokens.revokeIfActive(hash) == 0) {
            refreshTokens.revokeAllForUser(token.getUserId());
            throw new InvalidRefreshTokenException();
        }

        Optional<Credential> credential = credentials.findById(token.getUserId());
        if (credential.isEmpty() || !credential.get().isActive()) {
            throw new InvalidRefreshTokenException();
        }
        return issueSession.execute(credential.get());
    }
}
