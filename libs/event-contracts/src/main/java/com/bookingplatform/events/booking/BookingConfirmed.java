package com.bookingplatform.events.booking;

import java.time.LocalDate;
import java.util.UUID;

/** Đặt phòng đã được xác nhận (đã thu tiền và chốt phòng). */
public record BookingConfirmed(
        UUID bookingId,
        UUID userId,
        UUID propertyId,
        UUID roomTypeId,
        LocalDate checkIn,
        LocalDate checkOut,
        long totalMinor,
        String currency) {
}
