package com.bookingplatform.events.property;

import java.util.UUID;

/**
 * Có loại phòng mới. Inventory-service dùng để tạo tồn kho (totalRooms phòng
 * mỗi đêm).
 */
public record RoomTypeCreated(
        UUID propertyId,
        UUID roomTypeId,
        String name,
        int maxGuests,
        String bedConfig,
        int totalRooms,
        long basePriceMinor,
        String currency) {
}
