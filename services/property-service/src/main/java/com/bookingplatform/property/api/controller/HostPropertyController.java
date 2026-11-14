package com.bookingplatform.property.api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookingplatform.property.api.dto.PropertyPageResponse;
import com.bookingplatform.property.api.dto.PropertyRequest;
import com.bookingplatform.property.api.dto.PropertyResponse;
import com.bookingplatform.property.api.dto.PropertySummaryResponse;
import com.bookingplatform.property.api.dto.RoomTypeRequest;
import com.bookingplatform.property.api.dto.RoomTypeResponse;
import com.bookingplatform.property.application.usecase.HostProperties;
import com.bookingplatform.property.application.usecase.RoomTypes;
import com.bookingplatform.property.domain.model.Property;

import jakarta.validation.Valid;

/**
 * API của HOST. Cần vai trò HOST (cấu hình ở SecurityConfig).
 * hostId luôn lấy từ claim "sub" của JWT, KHÔNG nhận từ client: host không thể
 * giả danh host khác.
 */
@RestController
@RequestMapping("/api/v1/host/properties")
public class HostPropertyController {
    private static final int MAX_PAGE_SIZE = 100;

    private final HostProperties hostProperties;
    private final RoomTypes roomTypes;

    public HostPropertyController(HostProperties hostProperties, RoomTypes roomTypes) {
        this.hostProperties = hostProperties;
        this.roomTypes = roomTypes;
    }

    private static UUID hostId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PropertyRequest request) {
        return PropertyResponse.from(hostProperties.create(hostId(jwt), request.toDetails()));
    }

    @GetMapping
    public PropertyPageResponse list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) UUID after) {
        int size = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        List<Property> page = hostProperties.list(hostId(jwt), after, size);
        UUID next = page.size() == size ? page.get(page.size() - 1).getId() : null;
        return new PropertyPageResponse(page.stream().map(PropertySummaryResponse::from).toList(), next);
    }

    @GetMapping("/{id}")
    public PropertyResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return PropertyResponse.from(hostProperties.get(hostId(jwt), id));
    }

    @PutMapping("/{id}")
    public PropertyResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody PropertyRequest request) {
        return PropertyResponse.from(hostProperties.update(hostId(jwt), id, request.toDetails()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        hostProperties.delete(hostId(jwt), id);
    }

    @PostMapping("/{id}/publish")
    public PropertyResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return PropertyResponse.from(hostProperties.publish(hostId(jwt), id));
    }

    @PostMapping("/{id}/unpublish")
    public PropertyResponse unpublish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return PropertyResponse.from(hostProperties.unpublish(hostId(jwt), id));
    }

    // ------------------------------------------------------------ loại phòng
    @PostMapping("/{id}/room-types")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomTypeResponse addRoomType(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RoomTypeRequest request) {
        return RoomTypeResponse.from(
                roomTypes.add(hostId(jwt), id, request.toDetails()));
    }

    @PutMapping("/{id}/room-types/{roomTypeId}")
    public RoomTypeResponse updateRoomType(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @PathVariable UUID roomTypeId,
            @Valid @RequestBody RoomTypeRequest request) {
        return RoomTypeResponse.from(roomTypes.update(hostId(jwt), id, roomTypeId, request.toDetails()));
    }

    @DeleteMapping("/{id}/room-types/{roomTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoomType(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @PathVariable UUID roomTypeId) {
        roomTypes.delete(hostId(jwt), id, roomTypeId);
    }
}
