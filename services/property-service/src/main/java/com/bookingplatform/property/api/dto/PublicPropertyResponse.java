package com.bookingplatform.property.api.dto;

import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.model.PropertyView;
import java.util.List;
import java.util.UUID;

/**
 * Góc nhìn của KHÁCH: không có hostId, không có trạng thái (khách chỉ thấy
 * khách sạn đã đăng).
 */
public record PublicPropertyResponse(
        UUID id,
        String name,
        String description,
        String city,
        String country,
        String address,
        double latitude,
        double longitude,
        String currency,
        List<String> amenities,
        List<RoomTypeResponse> roomTypes) {

    public static PublicPropertyResponse from(PropertyView view) {
        Property p = view.property();
        return new PublicPropertyResponse(p.getId(), p.getName(), p.getDescription(), p.getCity(), p.getCountry(),
                p.getAddress(), p.getLatitude(), p.getLongitude(), p.getCurrency(), p.getAmenityCodes(),
                view.roomTypes().stream().map(RoomTypeResponse::from).toList());
    }
}
