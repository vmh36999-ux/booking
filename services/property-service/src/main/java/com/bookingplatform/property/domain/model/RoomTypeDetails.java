package com.bookingplatform.property.domain.model;

/** Thông tin host nhập khi tạo hoặc sửa loại phòng. */
public record RoomTypeDetails(
        String name,
        int maxGuests,
        String bedConfig,
        int totalRooms,
        long basePriceMinor) {
}
