package com.bookingplatform.inventory.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Query multiple room types at once. Defaults to 1 room if qty is not provided.
 */
public record AvailabilityRequest(
        @NotEmpty(message = "roomTypeIds must not be empty") @Size(max = 200, message = "Maximum of 200 room types per request") List<@NotNull(message = "roomTypeIds must not contain null values") UUID> roomTypeIds,

        @NotNull(message = "checkIn must not be blank (format 2026-12-20)") LocalDate checkIn,
        @NotNull(message = "checkOut must not be blank (format 2026-12-22)") LocalDate checkOut,

        @Min(value = 1, message = "Minimum number of rooms is 1") @Max(value = 20, message = "Maximum number of rooms is 20") Integer qty) {

    public int qtyOrDefault() {
        return qty == null ? 1 : qty;
    }
}