package com.bookingplatform.common.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Outbox dùng chung cho mọi service. CÔNG TẮC: app.outbox.enabled=true trong
 * application.yml.
 *
 * - Bật: có bean OutboxWriter (ghi sự kiện) và OutboxPublisher (gửi lên Kafka).
 * - app.outbox.publisher.enabled=false: chỉ ghi, không gửi (dùng khi seed dữ
 * liệu hoặc khi test không có Kafka).
 * - Không đặt công tắc: service không có outbox, không tạo gì cả.
 *
 * Service dùng outbox cần bảng outbox (có sẵn trong V1__baseline.sql), JDBC và
 * Kafka.
 */
@AutoConfiguration
@ConditionalOnClass({ JdbcTemplate.class, KafkaTemplate.class })
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
@EnableScheduling
public class OutboxAutoConfiguration {
    // Ghi event vào bảng outbox trong database
    @Bean
    public OutboxWriter outboxWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new JdbcOutboxWriter(jdbc, objectMapper);
    }

    // Đọc event từ outbox và gửi lên Kafka
    @Bean
    @ConditionalOnProperty(name = "app.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
    public OutboxPublisher outboxPublisher(
            JdbcTemplate jdbc,
            KafkaTemplate<String, String> kafka,
            TransactionTemplate transaction,
            @Value("${app.outbox.batch.size:50}") int batchSize) {
        return new OutboxPublisher(jdbc, kafka, transaction, batchSize);
    }
}
