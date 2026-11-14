package com.bookingplatform.property.domain.exception;

import java.util.Set;

/** Có mã tiện nghi không nằm trong danh mục. Sẽ thành phản hồi 400. */
public class UnknownAmenityException extends RuntimeException {
    public UnknownAmenityException(Set<String> codes) {
        super("invalid amenities: " + String.join(", ", codes));
    }
}
