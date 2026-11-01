package com.bookingplatform.auth.domain.exception;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String email) {
        super("Email " + email + " already exists");
    }
}