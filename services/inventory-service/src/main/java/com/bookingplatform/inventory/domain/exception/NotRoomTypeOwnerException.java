package com.bookingplatform.inventory.domain.exception;

/** Host không phải chủ của loại phòng này. Sẽ thành 403. */
public class NotRoomTypeOwnerException extends RuntimeException {
    public NotRoomTypeOwnerException() {
        super("You are not the owner of this room type");
    }
}
