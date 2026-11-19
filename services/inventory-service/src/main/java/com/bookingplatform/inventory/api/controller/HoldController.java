package com.bookingplatform.inventory.api.controller;

import com.bookingplatform.inventory.api.dto.HoldRequest;
import com.bookingplatform.inventory.api.dto.HoldResponse;
import com.bookingplatform.inventory.application.usecase.ConfirmHold;
import com.bookingplatform.inventory.application.usecase.GetHold;
import com.bookingplatform.inventory.application.usecase.HoldRooms;
import com.bookingplatform.inventory.application.usecase.ReleaseHold;
import com.bookingplatform.inventory.domain.model.HoldResult;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API giữ chỗ (nội bộ: cần vai trò SERVICE hoặc ADMIN, xem SecurityConfig). */
@RestController
@RequestMapping("/api/v1/inventory/holds")
public class HoldController {

    private final HoldRooms holdRooms;
    private final ConfirmHold confirmHold;
    private final ReleaseHold releaseHold;
    private final GetHold getHold;

    public HoldController(HoldRooms holdRooms, ConfirmHold confirmHold, ReleaseHold releaseHold, GetHold getHold) {
        this.holdRooms = holdRooms;
        this.confirmHold = confirmHold;
        this.releaseHold = releaseHold;
        this.getHold = getHold;
    }

    /**
     * 201 nếu giữ chỗ mới, 200 nếu bookingId này đã giữ chỗ từ trước (gọi lại an
     * toàn).
     */
    @PostMapping
    public ResponseEntity<HoldResponse> hold(@Valid @RequestBody HoldRequest request) {
        HoldResult result = holdRooms.execute(request.toCommand());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(HoldResponse.from(result.hold()));
    }

    @GetMapping("/{id}")
    public HoldResponse get(@PathVariable UUID id) {
        return HoldResponse.from(getHold.execute(id));
    }

    @PostMapping("/{id}/confirm")
    public HoldResponse confirm(@PathVariable UUID id) {
        return HoldResponse.from(confirmHold.execute(id));
    }

    @PostMapping("/{id}/release")
    public HoldResponse release(@PathVariable UUID id) {
        return HoldResponse.from(releaseHold.execute(id));
    }
}
