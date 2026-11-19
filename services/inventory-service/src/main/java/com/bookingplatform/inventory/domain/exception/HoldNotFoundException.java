package com.bookingplatform.inventory.domain.exception;

/** Inventory hold not found. Maps to a 404 NOT_FOUND response. */
public class HoldNotFoundException extends RuntimeException {
    public HoldNotFoundException() {
        super("Inventory hold not found");
    }
}