package com.bookingplatform.events.booking;

import java.util.UUID;

/** Đặt phòng bị hủy. refundMinor = số tiền cần hoàn theo chính sách (0 nếu không hoàn). */
public record BookingCancelled(
        UUID bookingId,
        UUID userId,
        UUID holdId,
        String reason,
        long refundMinor,
        String currency) {
}
