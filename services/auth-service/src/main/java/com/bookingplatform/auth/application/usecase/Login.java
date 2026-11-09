package com.bookingplatform.auth.application.usecase;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingplatform.auth.domain.exception.InvalidCredentialsException;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.Emails;
import com.bookingplatform.auth.domain.model.IssuedToken;
import com.bookingplatform.auth.domain.port.CredentialRepository;
import com.bookingplatform.auth.domain.port.TokenIssuer;

@Service
public class Login {
    private final CredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final String dummyHash;

    public Login(CredentialRepository credentials, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.dummyHash = passwordEncoder.encode("timing-protection-dummy");
    }

    @Transactional(readOnly = true)
    public IssuedToken execute(String email, String rawPassword) {
        Optional<Credential> found = credentials.findByEmail(Emails.normalize(email));
        String hash = found.map(Credential::getPasswordHash).orElse(dummyHash);
        boolean passwordOk = passwordEncoder.matches(rawPassword, hash);
        if (found.isEmpty() || !passwordOk || !found.get().isActive()) {
            throw new InvalidCredentialsException();
        }
        return tokenIssuer.issue(found.get());
    }

}
