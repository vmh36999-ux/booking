package com.bookingplatform.inventory.domain.model;

/**
 * Kết quả giữ chỗ. created=false nghĩa là bookingId này đã giữ chỗ từ trước và
 * ta trả lại đúng lần giữ cũ.
 */
public record HoldResult(Hold hold, boolean created) {
}
