package com.bookingplatform.auth.application.usecase;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingplatform.auth.domain.exception.InvalidCredentialsException;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.Emails;
import com.bookingplatform.auth.domain.model.Session;
import com.bookingplatform.auth.domain.port.CredentialRepository;
import com.bookingplatform.auth.domain.port.LoginAttemptLimiter;

@Service
public class Login {
    private final CredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final IssueSession issueSession;
    private final LoginAttemptLimiter limiter;
    /** Băm giả để thời gian phản hồi giống nhau dù email có tồn tại hay không. */
    private final String dummyHash;

    public Login(CredentialRepository credentials, PasswordEncoder passwordEncoder, LoginAttemptLimiter limiter,
            IssueSession issueSession) {
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.issueSession = issueSession;
        this.limiter = limiter;
        this.dummyHash = passwordEncoder.encode("timing-protection-dummy");
    }

    @Transactional
    public Session execute(String email, String rawPassword, String ipAddress) {
        String nomalized = Emails.normalize(email);
        limiter.assertAllowed(nomalized, ipAddress);

        Optional<Credential> found = credentials.findByEmail(Emails.normalize(email));
        String hash = found.map(Credential::getPasswordHash).orElse(dummyHash);
        boolean passwordOk = passwordEncoder.matches(rawPassword, hash);
        if (found.isEmpty() || !passwordOk || !found.get().isActive()) {
            limiter.recordFailure(nomalized, ipAddress);
            throw new InvalidCredentialsException();
        }
        limiter.recordSuccess(nomalized);
        return issueSession.execute(found.get());
    }

}
