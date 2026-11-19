package com.bookingplatform.inventory.api.dto;

import com.bookingplatform.inventory.domain.model.HoldCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record HoldRequest(
        @NotNull(message = "Booking ID is required") UUID bookingId,
        @NotNull(message = "Room type ID is required") UUID roomTypeId,
        @NotNull(message = "Check-in date is required (e.g., 2026-12-20)") LocalDate checkIn,
        @NotNull(message = "Check-out date is required (e.g., 2026-12-22)") LocalDate checkOut,
        @NotNull(message = "Quantity is required") @Min(value = 1, message = "Minimum quantity must be at least 1") @Max(value = 20, message = "Maximum quantity cannot exceed 20") Integer qty) {

    public HoldCommand toCommand() {
        return new HoldCommand(bookingId, roomTypeId, checkIn, checkOut, qty);
    }
}