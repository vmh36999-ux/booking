package com.bookingplatform.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * - /api/v1/inventory/** : API NỘI BỘ (giữ chỗ, tìm phòng trống hàng loạt). Cần
 * vai trò SERVICE hoặc ADMIN.
 * Gateway không mở đường này ra ngoài. Booking-service và search-service gọi
 * thẳng trong mạng nội bộ.
 * - /api/v1/host/** : API của HOST (chỉnh số phòng). Cần vai trò HOST; quyền sở
 * hữu loại phòng
 * được kiểm tra thêm trong use case.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/error")
                        .permitAll()
                        .requestMatchers("/api/v1/inventory/**").hasAnyRole("SERVICE", "ADMIN")
                        .requestMatchers("/api/v1/host/**").hasRole("HOST")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesConverter())));
        return http.build();
    }

    /**
     * Biến claim "roles": ["ADMIN"] trong JWT thành quyền ROLE_ADMIN để hasRole
     * hiểu được.
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
