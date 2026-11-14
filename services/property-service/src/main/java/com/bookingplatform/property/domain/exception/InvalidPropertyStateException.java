package com.bookingplatform.property.domain.exception;

/**
 * Thao tác không hợp lệ ở trạng thái hiện tại (ví dụ đăng khách sạn chưa có
 * loại phòng). Sẽ thành phản hồi 409.
 */
public class InvalidPropertyStateException extends RuntimeException {
    public InvalidPropertyStateException(String message) {
        super(message);
    }
}
