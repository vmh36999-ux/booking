package com.bookingplatform.inventory.domain.exception;

/**
 * Khoảng ngày không hợp lệ (trả phòng không sau nhận phòng, quá nhiều đêm, ngày
 * đã qua). Sẽ thành 400 INVALID_STAY.
 */
public class InvalidStayException extends RuntimeException {
    public InvalidStayException(String message) {
        super(message);
    }
}
