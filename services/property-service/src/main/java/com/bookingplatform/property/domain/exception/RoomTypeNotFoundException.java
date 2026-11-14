package com.bookingplatform.property.domain.exception;

/** Sẽ thành phản hồi 404. */
public class RoomTypeNotFoundException extends RuntimeException {
    public RoomTypeNotFoundException() {
        super("Room type not found");
    }
}
