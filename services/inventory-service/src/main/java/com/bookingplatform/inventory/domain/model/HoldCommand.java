package com.bookingplatform.inventory.domain.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Yêu cầu giữ chỗ: giữ qty phòng loại roomTypeId cho các đêm từ checkIn đến
 * trước checkOut.
 */
public record HoldCommand(UUID bookingId, UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int qty) {
}
