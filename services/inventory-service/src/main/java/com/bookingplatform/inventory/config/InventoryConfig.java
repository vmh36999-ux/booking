package com.bookingplatform.inventory.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InventoryProperties.class)
public class InventoryConfig {

    /** Đồng hồ của ứng dụng. Tách riêng để test giả được giờ. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
