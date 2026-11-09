package com.bookingplatform.user.application.usecase;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingplatform.user.domain.model.Profile;
import com.bookingplatform.user.domain.port.ProcessedEventRepository;
import com.bookingplatform.user.domain.port.ProfileRepository;

/**
 * Tạo hồ sơ rỗng khi có người đăng ký. Đây là MẪU IDEMPOTENT CONSUMER:
 * ghi processed_event và tạo profile trong CÙNG một transaction.
 * Nhận cùng một sự kiện hai lần thì lần hai bị bỏ qua.
 */
@Service
public class CreateProfileOnRegistration {

    private final ProcessedEventRepository processedEvents;
    private final ProfileRepository profiles;
    private final Clock clock;

    public CreateProfileOnRegistration(ProcessedEventRepository processedEvents, ProfileRepository profiles,
            Clock clock) {
        this.processedEvents = processedEvents;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** Trả true nếu đã xử lý, false nếu là sự kiện trùng. */
    @Transactional
    public boolean handle(UUID eventId, UUID userId) {
        if (!processedEvents.markProcessed(eventId)) {
            return false;
        }
        if (!profiles.exists(userId)) {
            profiles.insert(Profile.empty(userId, clock.instant()));
        }
        return true;
    }
}
