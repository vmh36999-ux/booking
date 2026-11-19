package com.bookingplatform.inventory.domain.port;

import java.util.UUID;

/**
 * Sổ ghi các sự kiện đã xử lý, để nhận trùng cùng một sự kiện thì không làm
 * lại.
 */
public interface ProcessedEventRepository {

    /**
     * Trả true nếu đây là LẦN ĐẦU (cứ xử lý tiếp), false nếu đã xử lý rồi (bỏ qua).
     */
    boolean markProcessed(UUID eventId);
}
