package com.bookingplatform.inventory.domain.exception;

import java.time.LocalDate;
import java.util.List;

/**
 * Host muốn giảm số phòng xuống thấp hơn số đã giữ và đã đặt. Mang theo các đêm
 * bị vướng. Sẽ thành 409 BELOW_COMMITTED.
 */
public class BelowCommitmentException extends RuntimeException {

    private final List<LocalDate> nights;

    public BelowCommitmentException(List<LocalDate> nights) {
        super("Cannot reduce room count: nights " + nights
                + " already have held and booked rooms exceeding the new limit");
        this.nights = List.copyOf(nights);
    }

    public List<LocalDate> getNights() {
        return nights;
    }
}