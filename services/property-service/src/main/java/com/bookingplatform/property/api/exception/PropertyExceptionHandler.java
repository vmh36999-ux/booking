package com.bookingplatform.property.api.exception;

import com.bookingplatform.common.web.ProblemFactory;
import com.bookingplatform.property.domain.exception.InvalidPropertyStateException;
import com.bookingplatform.property.domain.exception.NotPropertyOwnerException;
import com.bookingplatform.property.domain.exception.PropertyNotFoundException;
import com.bookingplatform.property.domain.exception.RoomTypeNotFoundException;
import com.bookingplatform.property.domain.exception.UnknownAmenityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles property-service business exceptions. Common errors (validation,
 * invalid JSON) are handled by CommonExceptionHandler.
 */
@RestControllerAdvice
public class PropertyExceptionHandler {

    @ExceptionHandler(PropertyNotFoundException.class)
    public ProblemDetail propertyNotFound(PropertyNotFoundException ex) {
        return ProblemFactory.of(HttpStatus.NOT_FOUND, "PROPERTY_NOT_FOUND", "Property not found", ex.getMessage());
    }

    @ExceptionHandler(RoomTypeNotFoundException.class)
    public ProblemDetail roomTypeNotFound(RoomTypeNotFoundException ex) {
        return ProblemFactory.of(HttpStatus.NOT_FOUND, "ROOM_TYPE_NOT_FOUND", "Room type not found", ex.getMessage());
    }

    @ExceptionHandler(NotPropertyOwnerException.class)
    public ProblemDetail notOwner(NotPropertyOwnerException ex) {
        return ProblemFactory.of(HttpStatus.FORBIDDEN, "NOT_PROPERTY_OWNER", "Access denied", ex.getMessage());
    }

    @ExceptionHandler(InvalidPropertyStateException.class)
    public ProblemDetail invalidState(InvalidPropertyStateException ex) {
        return ProblemFactory.of(HttpStatus.CONFLICT, "INVALID_PROPERTY_STATE", "Invalid property state",
                ex.getMessage());
    }

    @ExceptionHandler(UnknownAmenityException.class)
    public ProblemDetail unknownAmenity(UnknownAmenityException ex) {
        return ProblemFactory.of(HttpStatus.BAD_REQUEST, "UNKNOWN_AMENITY", "Invalid amenity", ex.getMessage());
    }
}