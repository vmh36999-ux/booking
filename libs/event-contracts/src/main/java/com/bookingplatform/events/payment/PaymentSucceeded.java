package com.bookingplatform.events.payment;

import java.util.UUID;

public record PaymentSucceeded(
        UUID paymentId,
        UUID bookingId,
        long amountMinor,
        String currency) {
}
