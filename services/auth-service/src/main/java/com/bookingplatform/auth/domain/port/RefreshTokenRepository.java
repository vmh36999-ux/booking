package com.bookingplatform.auth.domain.port;

import com.bookingplatform.auth.domain.model.RefreshToken;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hồi nếu còn hiệu lực. Trả 1 nếu vừa thu hồi được, 0 nếu đã bị thu hồi
     * trước đó hoặc không tồn tại.
     */
    int revokeIfActive(String tokenHash);

    /**
     * Thu hồi mọi refresh token còn hiệu lực của một user. Trả về số token bị thu
     * hồi.
     */
    int revokeAllForUser(UUID userId);
}