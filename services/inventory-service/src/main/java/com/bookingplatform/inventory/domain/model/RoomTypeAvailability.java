package com.bookingplatform.inventory.domain.model;

import java.util.UUID;

/**
 * Kết quả tìm phòng cho một loại phòng.
 *
 * hasInventory : đã có tồn kho cho ĐỦ mọi đêm trong khoảng ngày chưa
 * availableRooms : số phòng trống ở đêm "chật" nhất (muốn ở cả kỳ thì phải còn
 * phòng ở mọi đêm)
 * available : availableRooms >= số phòng khách cần
 */
public record RoomTypeAvailability(UUID roomTypeId, boolean hasInventory, int nights, int availableRooms,
        boolean available) {
}
