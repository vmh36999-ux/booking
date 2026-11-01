package com.bookingplatform.auth.api.exception;

import com.bookingplatform.auth.domain.exception.EmailAlreadyUsedException;
import com.bookingplatform.auth.domain.exception.InvalidCredentialsException;
import com.bookingplatform.auth.domain.exception.InvalidRegistrationException;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Handles all API errors using RFC 9457 (application/problem+json),
 * including a stable "code" field for clients.
 *
 * Extends ResponseEntityExceptionHandler to ensure standard
 * Spring MVC exceptions are also returned as ProblemDetail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail emailAlreadyUsed(EmailAlreadyUsedException ex) {
        return problem(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_USED",
                "Email Already Used",
                ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Authentication Failed",
                ex.getMessage());
    }

    @ExceptionHandler(InvalidRegistrationException.class)
    public ProblemDetail invalidRegistration(InvalidRegistrationException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Invalid Registration Data",
                ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail pd = problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation Failed",
                "One or more fields are invalid");

        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null
                                ? "Invalid value"
                                : fe.getDefaultMessage()))
                .toList();

        pd.setProperty("errors", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .headers(headers)
                .body(pd);
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String title,
            String detail) {

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create(
                "urn:problem:" + code.toLowerCase().replace('_', '-')));
        pd.setProperty("code", code);

        return pd;
    }
}