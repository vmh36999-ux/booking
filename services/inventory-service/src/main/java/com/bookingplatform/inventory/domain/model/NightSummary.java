package com.bookingplatform.inventory.domain.model;

/**
 * Tóm tắt một loại phòng trong khoảng ngày: có bao nhiêu đêm đã có tồn kho, và
 * đêm ÍT phòng trống nhất còn bao nhiêu.
 */
public record NightSummary(int nights, int minFree) {
}
