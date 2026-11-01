package com.bookingplatform.auth.api.dto;

import com.bookingplatform.auth.domain.model.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email must not be blank") @Email(message = "Email must be valid") @Size(max = 255, message = "Email must not exceed 255 characters") String email,

        @NotBlank(message = "Password must not be blank") @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") String password,

        AccountType accountType) {
}