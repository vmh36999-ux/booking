package com.bookingplatform.auth.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookingplatform.auth.api.dto.LoginRequest;
import com.bookingplatform.auth.api.dto.RegisterRequest;
import com.bookingplatform.auth.api.dto.RegisterResponse;
import com.bookingplatform.auth.api.dto.TokenResponse;
import com.bookingplatform.auth.application.usecase.Login;
import com.bookingplatform.auth.application.usecase.RegisterUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegisterUser registerUser;
    private final Login login;

    public AuthController(RegisterUser registerUser, Login login) {
        this.registerUser = registerUser;
        this.login = login;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return RegisterResponse.from(
                registerUser.execute(request.email(), request.password(), request.accountType()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(login.execute(request.email(), request.password()));
    }
}
