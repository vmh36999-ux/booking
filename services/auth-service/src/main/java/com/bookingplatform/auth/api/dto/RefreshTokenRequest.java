package com.bookingplatform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Dùng cho cả /refresh và /logout. */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken cannot be empty") String refreshToken) {
}