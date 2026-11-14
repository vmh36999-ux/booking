package com.bookingplatform.property.domain.exception;

/** Sẽ thành phản hồi 404. */
public class PropertyNotFoundException extends RuntimeException {
    public PropertyNotFoundException() {
        super("Hotel not found");
    }
}
