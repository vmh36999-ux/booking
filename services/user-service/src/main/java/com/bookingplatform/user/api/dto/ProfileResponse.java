package com.bookingplatform.user.api.dto;

import com.bookingplatform.user.domain.model.Profile;
import java.util.UUID;

public record ProfileResponse(
        UUID userId, String fullName, String phone, String country, String language, String currency) {

    public static ProfileResponse from(Profile p) {
        return new ProfileResponse(p.getUserId(), p.getFullName(), p.getPhone(),
                p.getCountry(), p.getLanguage(), p.getCurrency());
    }
}