package com.bookingplatform.auth.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.bookingplatform.auth.config.AuthProperties;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.IssuedToken;
import com.bookingplatform.auth.domain.model.Role;
import com.bookingplatform.auth.domain.port.TokenIssuer;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Ký access token RS256. Token chỉ chứa sub, roles và các claim chuẩn, không
 * chứa email.
 */

@Component
public class JwtTokenIssuer implements TokenIssuer {
    private final JwtEncoder encoder;
    private final RSAKey signingKey;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder encoder, RSAKey signingKey, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.signingKey = signingKey;
        this.issuer = properties.issuer();
        this.ttl = properties.accessTokenTtl();
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(Credential credential) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        List<String> roles = credential.getRoles().stream().map(Role::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(credential.getUserId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt, ttl.toSeconds());
    }
}
