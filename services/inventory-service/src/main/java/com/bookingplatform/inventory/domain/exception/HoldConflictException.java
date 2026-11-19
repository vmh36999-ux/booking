package com.bookingplatform.inventory.domain.exception;

import com.bookingplatform.inventory.domain.model.HoldStatus;

/**
 * Invalid operation for the current state of the hold. Carries an error code to
 * return to the client (409).
 */
public class HoldConflictException extends RuntimeException {

    private final String code;

    private HoldConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static HoldConflictException notActive(HoldStatus status) {
        return new HoldConflictException("HOLD_NOT_ACTIVE",
                "Hold is already in " + status + " status and cannot perform this operation");
    }

    public static HoldConflictException expired() {
        return new HoldConflictException("HOLD_EXPIRED", "Inventory hold has expired");
    }

    public static HoldConflictException alreadyConfirmed() {
        return new HoldConflictException("HOLD_ALREADY_CONFIRMED",
                "Inventory hold is already confirmed and cannot be released via this operation");
    }

    public static HoldConflictException mismatch() {
        return new HoldConflictException("HOLD_MISMATCH",
                "This booking already holds inventory with different details (room type, dates, or quantity)");
    }
}