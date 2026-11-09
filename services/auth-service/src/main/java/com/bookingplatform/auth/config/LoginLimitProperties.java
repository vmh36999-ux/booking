package com.bookingplatform.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.auth.login-limit.* : giới hạn số lần đăng nhập sai trong một khoảng thời
 * gian.
 */
@ConfigurationProperties(prefix = "app.auth.login-limit")
public record LoginLimitProperties(
        @DefaultValue("5") int maxPerEmail,
        @DefaultValue("20") int maxPerIp,
        @DefaultValue("15m") Duration window) {
}