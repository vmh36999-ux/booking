package com.bookingplatform.auth.domain.port;

import com.bookingplatform.events.EventEnvelope;

public interface OutboxWriter {

    /**
     * Ghi sự kiện vào bảng outbox TRONG CÙNG transaction đang chạy.
     * Việc gửi lên Kafka do OutboxPublisher làm.
     */
    void write(String topic, EventEnvelope<?> envelope);
}