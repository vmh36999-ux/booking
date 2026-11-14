
package com.bookingplatform.property.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.security.permit-all:false}") boolean permitAll) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/actuator/health/**",
                            "/actuator/info",
                            "/actuator/prometheus",
                            "/error").permitAll();

                    if (permitAll) {
                        auth.anyRequest().permitAll();
                    } else {
                        // Guest có thể xem danh sách và chi tiết property.
                        auth.requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/properties/**").permitAll();

                        // Chỉ HOST được thao tác API dành cho host.
                        auth.requestMatchers(
                                "/api/v1/host/**").hasRole("HOST");

                        // Chỉ ADMIN được thao tác API quản trị.
                        auth.requestMatchers(
                                "/api/v1/admin/**").hasRole("ADMIN");

                        // Các API còn lại cần đăng nhập.
                        auth.anyRequest().authenticated();
                    }
                })

                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(rolesConverter())));

        return http.build();
    }

    /**
     * Chuyển claim roles trong JWT thành Spring Security authorities.
     * Ví dụ: HOST -> ROLE_HOST.
     */
    private static JwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();

        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return converter;
    }
}
