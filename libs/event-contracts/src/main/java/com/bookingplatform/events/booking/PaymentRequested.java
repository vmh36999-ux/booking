package com.bookingplatform.events.booking;

import java.util.UUID;

/** booking-service yêu cầu payment-service thu tiền. */
public record PaymentRequested(
        UUID bookingId,
        UUID userId,
        long amountMinor,
        String currency) {
}
