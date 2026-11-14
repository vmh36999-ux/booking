package com.bookingplatform.property.api.dto;

import com.bookingplatform.property.domain.model.Property;
import java.util.UUID;

/** Dòng tóm tắt trong danh sách khách sạn của host. */
public record PropertySummaryResponse(UUID id, String name, String city, String country, String status) {

    public static PropertySummaryResponse from(Property p) {
        return new PropertySummaryResponse(p.getId(), p.getName(), p.getCity(), p.getCountry(), p.getStatus().name());
    }
}
