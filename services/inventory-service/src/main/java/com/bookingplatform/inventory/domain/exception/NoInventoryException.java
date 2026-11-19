package com.bookingplatform.inventory.domain.exception;

/**
 * No inventory initialized for these dates (inventory has not been created or
 * dates are out of range). Maps to a 409 NO_INVENTORY response.
 */
public class NoInventoryException extends RuntimeException {
    public NoInventoryException() {
        super("Inventory has not been initialized for the selected dates");
    }
}