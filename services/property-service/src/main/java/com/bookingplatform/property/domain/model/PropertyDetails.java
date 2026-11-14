package com.bookingplatform.property.domain.model;

import java.util.Set;

/** Thông tin host nhập khi tạo hoặc sửa khách sạn. */
public record PropertyDetails(
        String name,
        String description,
        String city,
        String country,
        String address,
        double latitude,
        double longitude,
        String currency,
        Set<String> amenities) {
}
