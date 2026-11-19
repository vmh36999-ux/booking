package com.bookingplatform.inventory.api.dto;

import com.bookingplatform.inventory.domain.model.Hold;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HoldResponse(
        UUID id,
        UUID bookingId,
        UUID roomTypeId,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int qty,
        String status,
        Instant expiresAt) {

    public static HoldResponse from(Hold h) {
        return new HoldResponse(h.id(), h.bookingId(), h.roomTypeId(), h.checkIn(), h.checkOut(), h.nights(),
                h.qty(), h.status().name(), h.expiresAt());
    }
}
