package com.bookingplatform.events.property;

import java.util.UUID;

/**
 * Có loại phòng mới. Inventory-service dùng để tạo tồn kho (totalRooms phòng
 * mỗi đêm).
 * và ghi nhớ loại phòng này của host nào (hostId) để kiểm tra quyền khi host
 * chỉnh số phòng.
 */
public record RoomTypeCreated(
                UUID propertyId,
                UUID hostId,
                UUID roomTypeId,
                String name,
                int maxGuests,
                String bedConfig,
                int totalRooms,
                long basePriceMinor,
                String currency) {
}
