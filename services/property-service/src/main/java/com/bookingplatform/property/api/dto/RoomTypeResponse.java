package com.bookingplatform.property.api.dto;

import com.bookingplatform.property.domain.model.RoomType;
import java.util.UUID;

public record RoomTypeResponse(
        UUID id, String name, int maxGuests, String bedConfig, int totalRooms, long basePriceMinor) {

    public static RoomTypeResponse from(RoomType r) {
        return new RoomTypeResponse(r.getId(), r.getName(), r.getMaxGuests(), r.getBedConfig(),
                r.getTotalRooms(), r.getBasePriceMinor());
    }
}
