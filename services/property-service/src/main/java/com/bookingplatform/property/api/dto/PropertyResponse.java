package com.bookingplatform.property.api.dto;

import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.model.PropertyView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Góc nhìn của HOST (và admin): có hostId, trạng thái. */
public record PropertyResponse(
        UUID id,
        UUID hostId,
        String name,
        String description,
        String city,
        String country,
        String address,
        double latitude,
        double longitude,
        String currency,
        String status,
        List<String> amenities,
        List<RoomTypeResponse> roomTypes,
        Instant createdAt,
        Instant updatedAt) {

    public static PropertyResponse from(PropertyView view) {
        Property p = view.property();
        return new PropertyResponse(p.getId(), p.getHostId(), p.getName(), p.getDescription(), p.getCity(),
                p.getCountry(), p.getAddress(), p.getLatitude(), p.getLongitude(), p.getCurrency(),
                p.getStatus().name(), p.getAmenityCodes(),
                view.roomTypes().stream().map(RoomTypeResponse::from).toList(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
