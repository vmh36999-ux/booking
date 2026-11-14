package com.bookingplatform.events.property;

import java.util.UUID;

/**
 * Một loại phòng trong ảnh chụp (snapshot) của khách sạn. Giá tính theo đơn vị
 * nhỏ nhất của tiền tệ khách sạn.
 */
public record RoomTypeData(
        UUID roomTypeId,
        String name,
        int maxGuests,
        String bedConfig,
        int totalRooms,
        long basePriceMinor) {
}
