package com.bookingplatform.auth.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingplatform.auth.domain.model.OpaqueTokens;
import com.bookingplatform.auth.domain.port.RefreshTokenRepository;

/**
 * Đăng xuất: thu hồi refresh token. Gọi lại nhiều lần hay token lạ đều không
 * lỗi (idempotent).
 */
@Service
public class Logout {
    private final RefreshTokenRepository refreshTokens;

    public Logout(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public void execute(String rawRefreshToken) {
        refreshTokens.revokeIfActive(OpaqueTokens.hash(rawRefreshToken));
    }
}
