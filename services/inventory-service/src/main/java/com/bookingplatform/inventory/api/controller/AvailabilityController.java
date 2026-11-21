package com.bookingplatform.inventory.api.controller;

import com.bookingplatform.inventory.api.dto.AvailabilityRequest;
import com.bookingplatform.inventory.api.dto.AvailabilityResponse;
import com.bookingplatform.inventory.application.usecase.SearchAvailability;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API nội bộ cho search-service (cần vai trò SERVICE hoặc ADMIN, xem
 * SecurityConfig). Chỉ đọc, không đổi dữ liệu.
 */
@RestController
@RequestMapping("/api/v1/inventory/availability")
public class AvailabilityController {

    private final SearchAvailability searchAvailability;

    public AvailabilityController(SearchAvailability searchAvailability) {
        this.searchAvailability = searchAvailability;
    }

    @PostMapping("/search")
    public AvailabilityResponse search(@Valid @RequestBody AvailabilityRequest request) {
        int qty = request.qtyOrDefault();
        return AvailabilityResponse.of(request.checkIn(), request.checkOut(), qty,
                searchAvailability.execute(request.roomTypeIds(), request.checkIn(), request.checkOut(), qty));
    }
}
