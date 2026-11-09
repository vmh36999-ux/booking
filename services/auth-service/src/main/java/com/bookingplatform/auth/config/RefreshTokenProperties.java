package com.bookingplatform.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.auth.refresh-token.* : refresh token sống bao lâu. */
@ConfigurationProperties(prefix = "app.auth.refresh-token")
public record RefreshTokenProperties(@DefaultValue("14d") Duration ttl) {
}