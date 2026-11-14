package com.bookingplatform.events.property;

import java.util.List;
import java.util.UUID;

/**
 * Ảnh chụp ĐẦY ĐỦ của một khách sạn tại thời điểm phát sự kiện.
 * Đủ thông tin để search-service dựng index mà không phải gọi ngược lại
 * property-service.
 */
public record PropertySnapshot(
        UUID propertyId,
        UUID hostId,
        String name,
        String description,
        String city,
        String country,
        String address,
        double latitude,
        double longitude,
        List<String> amenities,
        int maxGuests,
        long priceFromMinor,
        String currency,
        List<RoomTypeData> roomTypes) {
}
