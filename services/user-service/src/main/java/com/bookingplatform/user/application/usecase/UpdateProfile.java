package com.bookingplatform.user.application.usecase;

import com.bookingplatform.user.domain.exception.ProfileNotFoundException;
import com.bookingplatform.user.domain.model.Profile;
import com.bookingplatform.user.domain.port.ProfileRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateProfile {

    private final ProfileRepository profiles;
    private final Clock clock;

    public UpdateProfile(ProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    /**
     * Sửa đối tượng đã nạp: Hibernate tự ghi thay đổi khi transaction kết thúc
     * (dirty checking).
     */
    @Transactional
    public Profile execute(UUID userId, String fullName, String phone, String country, String language,
            String currency) {
        Profile profile = profiles.findById(userId).orElseThrow(ProfileNotFoundException::new);
        profile.update(fullName, phone, country, language, currency, clock.instant());
        return profile;
    }
}