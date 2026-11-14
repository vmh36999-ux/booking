package com.bookingplatform.property.domain.exception;

/** Sẽ thành phản hồi 403. */
public class NotPropertyOwnerException extends RuntimeException {
    public NotPropertyOwnerException() {
        super("You are not the owner of this hotel");
    }
}
