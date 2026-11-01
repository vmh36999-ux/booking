package com.bookingplatform.auth.domain.model;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {
}