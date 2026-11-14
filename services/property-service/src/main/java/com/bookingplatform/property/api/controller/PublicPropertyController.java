package com.bookingplatform.property.api.controller;

import com.bookingplatform.property.api.dto.AmenityResponse;
import com.bookingplatform.property.api.dto.PublicPropertyResponse;
import com.bookingplatform.property.application.usecase.PublicProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API công khai cho khách: không cần đăng nhập. */
@RestController
@RequestMapping("/api/v1/properties")
public class PublicPropertyController {

    private final PublicProperties publicProperties;

    public PublicPropertyController(PublicProperties publicProperties) {
        this.publicProperties = publicProperties;
    }

    @GetMapping("/{id}")
    public PublicPropertyResponse get(@PathVariable UUID id) {
        return PublicPropertyResponse.from(publicProperties.getPublished(id));
    }

    /** Danh mục tiện nghi hợp lệ, để giao diện hiển thị ô chọn. */
    @GetMapping("/amenities")
    public List<AmenityResponse> amenities() {
        return publicProperties.amenities().stream().map(AmenityResponse::from).toList();
    }
}
