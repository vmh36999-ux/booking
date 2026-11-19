package com.bookingplatform.inventory.domain.exception;

/**
 * Not enough available rooms for at least one night in the requested date
 * range. Maps to a 409 SOLD_OUT response.
 */
public class SoldOutException extends RuntimeException {
    public SoldOutException() {
        super("Not enough available rooms for the selected dates");
    }
}