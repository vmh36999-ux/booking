package com.bookingplatform.property.support;

import com.nimbusds.jose.JOSEException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Cho service tin khóa của test thay vì gọi về auth-service. Dùng
 * bằng @Import(TestJwtConfig.class).
 */
@TestConfiguration
public class TestJwtConfig {

    @Bean
    JwtDecoder jwtDecoder() throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(TestJwtSupport.KEY.toRSAPublicKey()).build();
    }
}
