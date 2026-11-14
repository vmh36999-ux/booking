package com.bookingplatform.property.api.controller;

import com.bookingplatform.property.api.dto.PropertyResponse;
import com.bookingplatform.property.application.usecase.PropertyModeration;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API của ADMIN. Cần vai trò ADMIN (cấu hình ở SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/properties")
public class AdminPropertyController {

    private final PropertyModeration moderation;

    public AdminPropertyController(PropertyModeration moderation) {
        this.moderation = moderation;
    }

    @PostMapping("/{id}/suspend")
    public PropertyResponse suspend(@PathVariable UUID id) {
        return PropertyResponse.from(moderation.suspend(id));
    }

    @PostMapping("/{id}/reinstate")
    public PropertyResponse reinstate(@PathVariable UUID id) {
        return PropertyResponse.from(moderation.reinstate(id));
    }
}
