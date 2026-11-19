package com.bookingplatform.inventory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.inventory.* : thời hạn giữ chỗ, số ngày tạo tồn kho, số đêm tối đa mỗi
 * lần giữ.
 */
@ConfigurationProperties(prefix = "app.inventory")
public record InventoryProperties(
        @DefaultValue("15m") Duration holdTtl,
        @DefaultValue("365") int horizonDays,
        @DefaultValue("30") int maxNights) {
}
