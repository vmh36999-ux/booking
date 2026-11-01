package com.bookingplatform.auth.api.dto;

import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.Role;
import java.util.List;
import java.util.UUID;

public record RegisterResponse(UUID userId, String email, List<String> roles) {

    public static RegisterResponse from(Credential credential) {
        return new RegisterResponse(
                credential.getUserId(),
                credential.getEmail(),
                credential.getRoles().stream().map(Role::name).sorted().toList());
    }
}