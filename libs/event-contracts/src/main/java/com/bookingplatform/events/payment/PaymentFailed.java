package com.bookingplatform.events.payment;

import java.util.UUID;

public record PaymentFailed(
        UUID paymentId,
        UUID bookingId,
        String reason) {
}
