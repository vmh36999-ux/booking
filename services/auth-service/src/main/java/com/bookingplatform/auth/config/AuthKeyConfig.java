package com.bookingplatform.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.bookingplatform.auth.infrastructure.security.RsaKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthKeyConfig {
    private static final Logger log = LoggerFactory.getLogger(AuthKeyConfig.class);

    @Bean
    public RSAKey rsaKey(AuthProperties properties) {
        String pem = properties.rsaPrivateKey();
        if (pem == null || pem.isBlank()) {
            log.warn("AUTH_RSA_PRIVATE_KEY is not configured: using a temporary RSA key. "
                    + "Tokens will become invalid after application restart. "
                    + "FOR DEVELOPMENT ENVIRONMENTS ONLY.");
            return RsaKeys.generate();
        }
        return RsaKeys.fromPem(pem);
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    /**
     * Auth-service tự kiểm tra token của mình bằng khóa công khai trong bộ nhớ,
     * không gọi HTTP vào chính nó.
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey, AuthProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
