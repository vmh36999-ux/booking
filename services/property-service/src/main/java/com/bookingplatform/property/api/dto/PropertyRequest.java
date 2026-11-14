package com.bookingplatform.property.api.dto;

import com.bookingplatform.property.domain.model.PropertyDetails;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Dữ liệu host gửi khi tạo hoặc sửa khách sạn. PUT thay thế toàn bộ. */

public record PropertyRequest(
        @NotBlank(message = "Name must not be blank") @Size(max = 200, message = "Name must not exceed 200 characters") String name,

        @Size(max = 5000, message = "Description must not exceed 5000 characters") String description,

        @NotBlank(message = "City must not be blank") @Size(max = 100, message = "City must not exceed 100 characters") String city,

        @NotBlank(message = "Country must not be blank") @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2-letter uppercase code, e.g. VN") String country,

        @Size(max = 300, message = "Address must not exceed 300 characters") String address,

        @NotNull(message = "Latitude must not be null") @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90") @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90") Double latitude,

        @NotNull(message = "Longitude must not be null") @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180") @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180") Double longitude,

        @NotBlank(message = "Currency must not be blank") @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase code, e.g. VND") String currency,

        Set<String> amenities) {

    public PropertyDetails toDetails() {
        return new PropertyDetails(name, description, city, country, address, latitude, longitude, currency,
                amenities == null ? Set.of() : Set.copyOf(amenities));
    }
}