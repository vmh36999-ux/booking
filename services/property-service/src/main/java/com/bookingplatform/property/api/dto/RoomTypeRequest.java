package com.bookingplatform.property.api.dto;

import com.bookingplatform.property.domain.model.RoomTypeDetails;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Data submitted by the host when creating or updating a room type. */
public record RoomTypeRequest(
        @NotBlank(message = "Room type name must not be blank") @Size(max = 100, message = "Room type name must not exceed 100 characters") String name,

        @NotNull(message = "Maximum number of guests must not be null") @Min(value = 1, message = "Maximum number of guests must be at least 1") @Max(value = 20, message = "Maximum number of guests must not exceed 20") Integer maxGuests,

        @Size(max = 100, message = "Bed configuration must not exceed 100 characters") String bedConfig,

        @NotNull(message = "Total number of rooms must not be null") @Min(value = 1, message = "Total number of rooms must be at least 1") @Max(value = 1000, message = "Total number of rooms must not exceed 1000") Integer totalRooms,

        @NotNull(message = "Base price must not be null") @Min(value = 0, message = "Base price must not be negative") Long basePriceMinor) {

    public RoomTypeDetails toDetails() {
        return new RoomTypeDetails(name, maxGuests, bedConfig, totalRooms, basePriceMinor);
    }
}