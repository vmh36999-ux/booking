package com.bookingplatform.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ RefreshTokenProperties.class, LoginLimitProperties.class })
public class AuthSessionConfig {
}