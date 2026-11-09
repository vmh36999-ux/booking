package com.bookingplatform.common.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Xử lý lỗi chung cho mọi service.
 * Các lỗi validation được chuẩn hóa thành application/problem+json.
 */
@RestControllerAdvice
public class CommonExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        // Tạo response lỗi chuẩn RFC 9457.
        ProblemDetail problem = ProblemFactory.of(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Invalid request data",
                "One or more fields are invalid");

        // Lấy danh sách các field bị validation thất bại.
        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null
                                ? "Invalid value"
                                : fe.getDefaultMessage()))
                .toList();

        // Thêm danh sách lỗi chi tiết vào response.
        problem.setProperty("errors", errors);

        // Trả HTTP 400 Bad Request.
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .headers(headers)
                .body(problem);
    }
}