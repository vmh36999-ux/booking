package com.bookingplatform.user.api.controller;

import com.bookingplatform.user.api.dto.ProfileResponse;
import com.bookingplatform.user.api.dto.UpdateProfileRequest;
import com.bookingplatform.user.application.usecase.GetProfile;
import com.bookingplatform.user.application.usecase.UpdateProfile;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "me" nghĩa là chính người đang đăng nhập: userId lấy từ claim "sub" của JWT,
 * KHÔNG nhận từ client.
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileController {

    private final GetProfile getProfile;
    private final UpdateProfile updateProfile;

    public ProfileController(GetProfile getProfile, UpdateProfile updateProfile) {
        this.getProfile = getProfile;
        this.updateProfile = updateProfile;
    }

    @GetMapping
    public ProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return ProfileResponse.from(getProfile.execute(UUID.fromString(jwt.getSubject())));
    }

    @PutMapping
    public ProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return ProfileResponse.from(updateProfile.execute(
                UUID.fromString(jwt.getSubject()),
                request.fullName(), request.phone(), request.country(), request.language(), request.currency()));
    }
}