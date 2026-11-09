package com.bookingplatform.auth.api.dto;

import com.bookingplatform.auth.domain.model.Session;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
        long refreshExpiresIn) {

    public static TokenResponse from(Session session) {
        return new TokenResponse(
                session.accessToken().value(),
                "Bearer",
                session.accessToken().expiresInSeconds(),
                session.refreshToken(),
                session.refreshExpiresInSeconds());
    }
}