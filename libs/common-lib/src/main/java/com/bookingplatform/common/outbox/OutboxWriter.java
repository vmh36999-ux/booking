package com.bookingplatform.common.outbox;

import com.bookingplatform.events.EventEnvelope;

/**
 * Ghi sự kiện vào bảng outbox TRONG CÙNG transaction đang chạy. Việc gửi lên
 * Kafka do OutboxPublisher làm sau đó.
 */
public interface OutboxWriter {

    void write(String topic, EventEnvelope<?> envelope);
}
