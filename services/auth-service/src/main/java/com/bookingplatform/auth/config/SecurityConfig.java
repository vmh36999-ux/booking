package com.bookingplatform.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource server: xác thực JWT do auth-service ký (kiểm tra bằng JWKS).
 * Profile "local" bật app.security.permit-all=true để chạy thử mà không cần
 * token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${app.security.permit-all:false}") boolean permitAll) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll();
                    auth.requestMatchers(new String[] { "/error", "/api/v1/auth/**", "/.well-known/**" }).permitAll();
                    if (permitAll) {
                        auth.anyRequest().permitAll();
                    } else {
                        auth.anyRequest().authenticated();
                    }
                })
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
