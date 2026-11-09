
package com.bookingplatform.auth.api.exception;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.bookingplatform.auth.domain.exception.EmailAlreadyUsedException;
import com.bookingplatform.auth.domain.exception.InvalidCredentialsException;
import com.bookingplatform.auth.domain.exception.InvalidRegistrationException;
import com.bookingplatform.auth.domain.exception.InvalidRefreshTokenException;
import com.bookingplatform.auth.domain.exception.TooManyAttemptsException;
import com.bookingplatform.common.web.ProblemFactory;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles authentication-related API exceptions.
 * Returns standardized RFC 9457 ProblemDetail responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

        /**
         * Handles duplicate email registration.
         * HTTP 409 - Conflict.
         */
        @ExceptionHandler(EmailAlreadyUsedException.class)
        public ProblemDetail emailAlreadyUsed(EmailAlreadyUsedException ex) {
                return ProblemFactory.of(
                                HttpStatus.CONFLICT,
                                "EMAIL_ALREADY_USED",
                                "Email Already Used",
                                "This email address is already registered.");
        }

        /**
         * Handles invalid login credentials.
         * HTTP 401 - Unauthorized.
         */
        @ExceptionHandler(InvalidCredentialsException.class)
        public ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
                return ProblemFactory.of(
                                HttpStatus.UNAUTHORIZED,
                                "INVALID_CREDENTIALS",
                                "Authentication Failed",
                                "Email or password is not correct.");
        }

        /**
         * Handles invalid registration data.
         * HTTP 400 - Bad Request.
         */
        @ExceptionHandler(InvalidRegistrationException.class)
        public ProblemDetail invalidRegistration(InvalidRegistrationException ex) {
                return ProblemFactory.of(
                                HttpStatus.BAD_REQUEST,
                                "VALIDATION_ERROR",
                                "Invalid Registration Data",
                                "The registration information is invalid.");
        }

        /**
         * Handles invalid, expired, or reused refresh tokens.
         * HTTP 401 - Unauthorized.
         */
        @ExceptionHandler(InvalidRefreshTokenException.class)
        public ProblemDetail invalidRefreshToken(InvalidRefreshTokenException ex) {
                return ProblemFactory.of(
                                HttpStatus.UNAUTHORIZED,
                                "INVALID_REFRESH_TOKEN",
                                "Invalid Refresh Token",
                                "The refresh token is invalid, expired, or has already been used.");
        }

        /**
         * Handles requests that exceed the allowed attempt limit.
         * HTTP 429 - Too Many Requests.
         */
        @ExceptionHandler(TooManyAttemptsException.class)
        public ProblemDetail tooManyAttempts(
                        TooManyAttemptsException ex,
                        HttpServletResponse response) {

                response.setHeader(
                                "Retry-After",
                                String.valueOf(ex.getRetryAfterSeconds()));

                return ProblemFactory.of(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "TOO_MANY_ATTEMPTS",
                                "Too Many Requests",
                                "Too many attempts. Please try again later.");
        }

        /**
         * Handles request body validation errors.
         * HTTP 400 - Bad Request.
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ProblemDetail handleValidationErrors(
                        MethodArgumentNotValidException ex) {

                ProblemDetail problem = ProblemFactory.of(
                                HttpStatus.BAD_REQUEST,
                                "VALIDATION_ERROR",
                                "Validation Failed",
                                "One or more fields are invalid.");

                List<Map<String, String>> errors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(error -> Map.of(
                                                "field", error.getField(),
                                                "message", error.getDefaultMessage() == null
                                                                ? "Invalid value"
                                                                : error.getDefaultMessage()))
                                .toList();

                problem.setProperty("errors", errors);

                return problem;
        }
}
