package com.bookingplatform.user.api.exception;

import com.bookingplatform.common.web.ProblemFactory;
import com.bookingplatform.user.domain.exception.ProfileNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lỗi nghiệp vụ của user-service. Lỗi chung do CommonExceptionHandler
 * (common-lib) xử lý.
 */
@RestControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler(ProfileNotFoundException.class)
    public ProblemDetail profileNotFound(ProfileNotFoundException ex) {
        return ProblemFactory.of(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "No profile yet", ex.getMessage());
    }
}