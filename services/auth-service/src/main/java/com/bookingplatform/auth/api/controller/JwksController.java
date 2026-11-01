package com.bookingplatform.auth.api.controller;

import java.time.Duration;
import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;

@RestController
public class JwksController {
    private final RSAKey rsaKey;

    public JwksController(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks() {
        // toPublicJWK() bỏ phần khóa riêng; chỉ khóa công khai được trả ra
        String body = new JWKSet(rsaKey.toPublicJWK()).toString();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(body);
    }

}