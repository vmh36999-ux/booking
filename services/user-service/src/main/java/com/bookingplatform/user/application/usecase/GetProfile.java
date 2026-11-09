package com.bookingplatform.user.application.usecase;

import com.bookingplatform.user.domain.exception.ProfileNotFoundException;
import com.bookingplatform.user.domain.model.Profile;
import com.bookingplatform.user.domain.port.ProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetProfile {

    private final ProfileRepository profiles;

    public GetProfile(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public Profile execute(UUID userId) {
        return profiles.findById(userId).orElseThrow(ProfileNotFoundException::new);
    }
}