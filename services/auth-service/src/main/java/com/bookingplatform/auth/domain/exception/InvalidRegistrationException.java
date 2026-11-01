package com.bookingplatform.auth.domain.exception;

public class InvalidRegistrationException extends RuntimeException {
    public InvalidRegistrationException(String message) {
        super(message);
    }
}