package com.bookingplatform.common.outbox;

import com.bookingplatform.events.EventEnvelope;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Ghi sự kiện vào bảng outbox bằng JDBC. JdbcTemplate dùng chung kết nối với
 * transaction JPA đang chạy,
 * nên dòng outbox được lưu hoặc hủy CÙNG với dữ liệu nghiệp vụ.
 */
public class JdbcOutboxWriter implements OutboxWriter {

    private static final String INSERT_SQL = "INSERT INTO outbox (id, aggregate_id, type, topic, payload) VALUES (?, ?, ?, ?, ?::jsonb)";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcOutboxWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(String topic, EventEnvelope<?> envelope) {
        String json = objectMapper.writeValueAsString(envelope);
        jdbc.update(INSERT_SQL,
                envelope.eventId(),
                UUID.fromString(envelope.aggregateId()),
                envelope.type(),
                topic,
                json);
    }
}
