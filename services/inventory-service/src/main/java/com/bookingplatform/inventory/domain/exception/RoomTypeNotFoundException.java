package com.bookingplatform.inventory.domain.exception;

/**
 * Không biết loại phòng này (chưa nhận sự kiện tạo loại phòng, hoặc id sai). Sẽ
 * thành 404.
 */
public class RoomTypeNotFoundException extends RuntimeException {
    public RoomTypeNotFoundException() {
        super("Room type not found");
    }
}
