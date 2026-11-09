package com.bookingplatform.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật @Scheduled (OutboxPublisher cần). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}