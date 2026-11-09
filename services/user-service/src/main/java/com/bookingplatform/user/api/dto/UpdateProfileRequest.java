package com.bookingplatform.user.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT thay thế toàn bộ: trường nào không gửi (hoặc null) sẽ được xóa. */
public record UpdateProfileRequest(
        @Size(max = 200, message = "Full name must not exceed 200 characters") String fullName,
        @Size(max = 30, message = "Phone number must not exceed 30 characters") String phone,
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2-letter uppercase code, e.g. VN") String country,
        @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Language must follow the format vi or vi-VN") String language,
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase code, e.g. VND") String currency) {
}