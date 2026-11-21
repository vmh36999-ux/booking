package com.bookingplatform.inventory.api.dto;

import com.bookingplatform.inventory.domain.model.RoomTypeAvailability;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(LocalDate checkIn, LocalDate checkOut, int nights, int qty, List<Item> results) {

    public record Item(UUID roomTypeId, boolean available, int availableRooms, boolean hasInventory) {
    }

    public static AvailabilityResponse of(LocalDate checkIn, LocalDate checkOut, int qty,
            List<RoomTypeAvailability> results) {
        return new AvailabilityResponse(checkIn, checkOut, (int) ChronoUnit.DAYS.between(checkIn, checkOut), qty,
                results.stream()
                        .map(r -> new Item(r.roomTypeId(), r.available(), r.availableRooms(), r.hasInventory()))
                        .toList());
    }
}
