package com.bookingplatform.auth.api.dto;

import com.bookingplatform.auth.domain.model.IssuedToken;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse from(IssuedToken token) {
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds());
    }
}