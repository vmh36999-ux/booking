package com.bookingplatform.auth.application.usecase;

import com.bookingplatform.auth.domain.exception.EmailAlreadyUsedException;
import com.bookingplatform.auth.domain.exception.InvalidRegistrationException;
import com.bookingplatform.auth.domain.model.AccountType;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.Emails;
import com.bookingplatform.auth.domain.port.CredentialRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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
    private final Clock clock;

    public RegisterUser(CredentialRepository credentials, PasswordEncoder passwordEncoder, Clock clock) {
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

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
        return credentials.save(credential);
    }

}
