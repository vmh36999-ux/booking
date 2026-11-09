package com.bookingplatform.auth.infrastructure.messaging;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Định kỳ lấy các dòng outbox chưa gửi, gửi lên Kafka, rồi đánh dấu đã gửi.
 *
 * - FOR UPDATE SKIP LOCKED: chạy nhiều instance cùng lúc cũng không gửi trùng
 * một dòng.
 * - Chờ Kafka xác nhận (ack) rồi mới đánh dấu published_at.
 * - Lỗi giữa chừng thì dừng, các dòng gửi xong trước đó vẫn được đánh dấu, dòng
 * lỗi được thử lại ở lượt sau.
 * - Đảm bảo "ít nhất một lần": consumer phải chống trùng bằng eventId.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final String SELECT_SQL = """
            SELECT id, topic, aggregate_id, payload::text AS payload
            FROM outbox
            WHERE published_at IS NULL
            ORDER BY created_at, id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final int batchSize;

    public OutboxPublisher(JdbcTemplate jdbc,
            KafkaTemplate<String, String> kafka,
            TransactionTemplate transaction,
            @Value("${app.outbox.batch-size:50}") int batchSize) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transaction = transaction;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:400}")
    public void publishPending() {
        try {
            transaction.executeWithoutResult(status -> publishBatch());
        } catch (RuntimeException e) {
            log.warn("Outbox: Failed to publish message, will retry on next attempt: {}", e.toString());
        }
    }

    private void publishBatch() {
        List<Row> rows = jdbc.query(SELECT_SQL,
                (rs, i) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getString("topic"),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("payload")),
                batchSize);

        for (Row row : rows) {
            try {
                kafka.send(row.topic(), row.aggregateId().toString(), row.payload()).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException e) {
                log.warn("Outbox: Failed to send event {} to topic {}: {}", row.id(), row.topic(), e.toString());
                return;
            }
            jdbc.update("UPDATE outbox SET published_at = now() WHERE id = ?", row.id());
        }
    }

    private record Row(UUID id, String topic, UUID aggregateId, String payload) {
    }
}