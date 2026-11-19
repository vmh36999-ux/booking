package com.bookingplatform.inventory.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Một lần giữ chỗ. Bất biến: mỗi lần đổi trạng thái tạo ra một bản mới
 * (withStatus).
 */
public record Hold(
        UUID id,
        UUID bookingId,
        UUID roomTypeId,
        LocalDate checkIn,
        LocalDate checkOut,
        int qty,
        HoldStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public static Hold create(HoldCommand command, Instant now, Duration ttl) {
        return new Hold(UUID.randomUUID(), command.bookingId(), command.roomTypeId(), command.checkIn(),
                command.checkOut(), command.qty(), HoldStatus.HELD, now.plus(ttl), now, now);
    }

    /** Số đêm: từ ngày nhận phòng đến trước ngày trả phòng. */
    public int nights() {
        return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /**
     * Yêu cầu giữ chỗ này có giống hệt lần giữ đã lưu không (dùng để nhận ra gọi
     * lại đúng cách hay gọi sai).
     */
    public boolean matches(HoldCommand command) {
        return roomTypeId.equals(command.roomTypeId())
                && checkIn.equals(command.checkIn())
                && checkOut.equals(command.checkOut())
                && qty == command.qty();
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public Hold withStatus(HoldStatus newStatus, Instant now) {
        return new Hold(id, bookingId, roomTypeId, checkIn, checkOut, qty, newStatus, expiresAt, createdAt, now);
    }
}
