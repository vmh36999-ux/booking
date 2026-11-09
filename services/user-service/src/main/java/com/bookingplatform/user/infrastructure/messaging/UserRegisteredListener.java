package com.bookingplatform.user.infrastructure.messaging;

import com.bookingplatform.events.EventTypes;
import com.bookingplatform.events.Topics;
import com.bookingplatform.user.application.usecase.CreateProfileOnRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Nghe topic user.events. Message hỏng (JSON sai) sẽ ném lỗi để error handler
 * thử lại rồi đẩy vào DLT.
 */
@Component
public class UserRegisteredListener {
    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);
    private final ObjectMapper objectMapper;
    private final CreateProfileOnRegistration createProfile;

    public UserRegisteredListener(ObjectMapper objectMapper, CreateProfileOnRegistration createProfile) {
        this.objectMapper = objectMapper;
        this.createProfile = createProfile;
    }

    @KafkaListener(topics = Topics.USER_EVENTS)
    public void onMessage(String json) {
        handle(json);
    }

    public void handle(String json) {
        UserRegisteredMessage message = objectMapper.readValue(json, UserRegisteredMessage.class);
        if (!EventTypes.USER_REGISTERED.equals(message.type())) {
            return;
        }
        boolean processed = createProfile.handle(message.eventId(), message.data().userId());
        if (!processed) {
            log.info("Skip already processed event: {}", message.eventId());
        }
    }
}
