package com.bookingplatform.inventory.api.controller;

import com.bookingplatform.inventory.api.dto.HostInventoryRequest;
import com.bookingplatform.inventory.api.dto.HostInventoryResponse;
import com.bookingplatform.inventory.application.usecase.AdjustHostInventory;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API của HOST (cần vai trò HOST). hostId lấy từ claim "sub" của JWT, KHÔNG
 * nhận từ client.
 * Đi qua gateway: /api/v1/host/inventory/** đã có sẵn route.
 */
@RestController
@RequestMapping("/api/v1/host/inventory")
public class HostInventoryController {

    private final AdjustHostInventory adjustHostInventory;

    public HostInventoryController(AdjustHostInventory adjustHostInventory) {
        this.adjustHostInventory = adjustHostInventory;
    }

    @PutMapping
    public HostInventoryResponse adjust(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody HostInventoryRequest request) {
        int nights = adjustHostInventory.execute(UUID.fromString(jwt.getSubject()), request.roomTypeId(),
                request.from(), request.to(), request.totalRooms());
        return new HostInventoryResponse(request.roomTypeId(), request.from(), request.to(), nights,
                request.totalRooms());
    }
}
