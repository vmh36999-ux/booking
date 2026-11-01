package com.bookingplatform.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String issuer,
        Duration accessTokenTtl,
        String rsaPrivateKey) {
}
