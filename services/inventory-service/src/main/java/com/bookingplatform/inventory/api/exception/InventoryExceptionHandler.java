package com.bookingplatform.inventory.api.exception;

import com.bookingplatform.common.web.ProblemFactory;
import com.bookingplatform.inventory.domain.exception.BelowCommitmentException;
import com.bookingplatform.inventory.domain.exception.HoldConflictException;
import com.bookingplatform.inventory.domain.exception.HoldNotFoundException;
import com.bookingplatform.inventory.domain.exception.InvalidStayException;
import com.bookingplatform.inventory.domain.exception.NoInventoryException;
import com.bookingplatform.inventory.domain.exception.NotRoomTypeOwnerException;
import com.bookingplatform.inventory.domain.exception.RoomTypeNotFoundException;
import com.bookingplatform.inventory.domain.exception.SoldOutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Business exception handler for inventory-service. General errors (validation,
 * bad JSON) are handled by CommonExceptionHandler.
 */
@RestControllerAdvice
public class InventoryExceptionHandler {

    @ExceptionHandler(SoldOutException.class)
    public ProblemDetail soldOut(SoldOutException ex) {
        return ProblemFactory.of(HttpStatus.CONFLICT, "SOLD_OUT", "Sold Out", ex.getMessage());
    }

    @ExceptionHandler(NoInventoryException.class)
    public ProblemDetail noInventory(NoInventoryException ex) {
        return ProblemFactory.of(HttpStatus.CONFLICT, "NO_INVENTORY", "No Inventory", ex.getMessage());
    }

    @ExceptionHandler(HoldNotFoundException.class)
    public ProblemDetail holdNotFound(HoldNotFoundException ex) {
        return ProblemFactory.of(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "Hold Not Found", ex.getMessage());
    }

    @ExceptionHandler(InvalidStayException.class)
    public ProblemDetail invalidStay(InvalidStayException ex) {
        return ProblemFactory.of(HttpStatus.BAD_REQUEST, "INVALID_STAY", "Invalid Stay", ex.getMessage());
    }

    @ExceptionHandler(HoldConflictException.class)
    public ProblemDetail holdConflict(HoldConflictException ex) {
        return ProblemFactory.of(HttpStatus.CONFLICT, ex.getCode(), "Hold Conflict", ex.getMessage());
    }

    @ExceptionHandler(RoomTypeNotFoundException.class)
    public ProblemDetail roomTypeNotFound(RoomTypeNotFoundException ex) {
        return ProblemFactory.of(HttpStatus.NOT_FOUND, "ROOM_TYPE_NOT_FOUND", "Room type not found", ex.getMessage());
    }

    @ExceptionHandler(NotRoomTypeOwnerException.class)
    public ProblemDetail notOwner(NotRoomTypeOwnerException ex) {
        return ProblemFactory.of(HttpStatus.FORBIDDEN, "NOT_ROOM_TYPE_OWNER", "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(BelowCommitmentException.class)
    public ProblemDetail belowCommitment(BelowCommitmentException ex) {
        ProblemDetail problem = ProblemFactory.of(HttpStatus.CONFLICT, "BELOW_COMMITTED",
                "Below held and booked room count", ex.getMessage());
        problem.setProperty("nights", ex.getNights());
        return problem;
    }
}