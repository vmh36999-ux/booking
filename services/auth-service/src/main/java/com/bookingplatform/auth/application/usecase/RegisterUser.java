package com.bookingplatform.auth.application.usecase;

import com.bookingplatform.auth.domain.exception.EmailAlreadyUsedException;
import com.bookingplatform.auth.domain.exception.InvalidRegistrationException;
import com.bookingplatform.auth.domain.model.AccountType;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.Emails;
import com.bookingplatform.auth.domain.model.Role;
import com.bookingplatform.auth.domain.port.CredentialRepository;
import com.bookingplatform.auth.domain.port.OutboxWriter;
import com.bookingplatform.common.web.CorrelationIdFilter;
import com.bookingplatform.events.EventEnvelope;
import com.bookingplatform.events.EventTypes;
import com.bookingplatform.events.Topics;
import com.bookingplatform.events.user.UserRegistered;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import org.jboss.logging.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUser {
    /**
     * BCrypt chỉ dùng 72 byte đầu của mật khẩu, nên chặn ở đây thay vì để cắt ngầm.
     */
    private static final int MAX_PASSWORD_BYTES = 72;
    private final CredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final OutboxWriter outbox;
    private final Clock clock;

    public RegisterUser(CredentialRepository credentials, PasswordEncoder passwordEncoder, OutboxWriter outbox,
            Clock clock) {
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Lưu tài khoản VÀ ghi sự kiện UserRegistered vào outbox trong CÙNG một
     * transaction:
     * hoặc có cả hai, hoặc không có cái nào.
     */
    @Transactional
    public Credential execute(String email, String rawPassword, AccountType accountType) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidRegistrationException("Maximum password length is 72 bytes");
        }
        String normalized = Emails.normalize(email);
        if (credentials.existsByEmail(normalized)) {
            throw new EmailAlreadyUsedException(email);
        }
        AccountType type = accountType == null ? AccountType.GUEST : accountType;
        Credential credential = Credential.register(
                normalized, passwordEncoder.encode(rawPassword), type.toRole(), clock.instant());
        Credential saved = credentials.save(credential);

        List<String> roles = saved.getRoles()
                .stream()
                .map(Role::name)
                .sorted()
                .toList();
        // Lấy Correlation ID từ MDC
        Object correlationIdValue = MDC.get(CorrelationIdFilter.MDC_KEY);
        // Chuyển sang String, giữ null nếu không có giá trị
        String correlationId = correlationIdValue == null ? null : correlationIdValue.toString();
        // Tạo Event Envelope và ghi vào Outbox
        outbox.write(
                Topics.USER_EVENTS,
                EventEnvelope.of(
                        EventTypes.USER_REGISTERED,
                        saved.getUserId().toString(),
                        correlationId,
                        new UserRegistered(
                                saved.getUserId(),
                                saved.getEmail(),
                                roles)));
        return saved;
    }

}
