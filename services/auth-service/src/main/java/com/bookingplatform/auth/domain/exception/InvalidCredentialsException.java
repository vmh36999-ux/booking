package com.bookingplatform.auth.domain.exception;

/**
 * Use the same response for both "email not found" and "incorrect password" to
 * avoid revealing
 * which emails are registered.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Email or password is not correct");
    }
}