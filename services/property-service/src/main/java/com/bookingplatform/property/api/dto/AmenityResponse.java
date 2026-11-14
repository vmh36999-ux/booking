package com.bookingplatform.property.api.dto;

import com.bookingplatform.property.domain.model.Amenity;

public record AmenityResponse(String code, String name) {

    public static AmenityResponse from(Amenity a) {
        return new AmenityResponse(a.code(), a.name());
    }
}
