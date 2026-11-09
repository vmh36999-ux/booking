package com.bookingplatform.user.domain.port;

import java.util.Optional;
import java.util.UUID;

import com.bookingplatform.user.domain.model.Profile;

public interface ProfileRepository {
    boolean exists(UUID userId);

    Optional<Profile> findById(UUID userId);

    Profile insert(Profile profile);
}
