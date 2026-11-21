package com.bookingplatform.inventory.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sets the total room count for a room type for nights from "from" up to BEFORE
 * "to" ("to" is exclusive).
 */
public record HostInventoryRequest(
        @NotNull(message = "roomTypeId must not be null") UUID roomTypeId,
        @NotNull(message = "from must not be null (format 2026-12-20)") LocalDate from,
        @NotNull(message = "to must not be null (format 2026-12-27)") LocalDate to,
        @NotNull(message = "totalRooms must not be null") @Min(value = 0, message = "Room count cannot be negative (0 means closed for sale)") @Max(value = 10000, message = "Maximum number of rooms is 10000") Integer totalRooms) {
}