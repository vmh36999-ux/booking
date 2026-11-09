package com.bookingplatform.auth.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingplatform.auth.domain.model.OpaqueTokens;
import org.junit.jupiter.api.Test;

class OpaqueTokensTest {

    @Test
    void generatesUrlSafeTokenOf43Characters() {
        String token = OpaqueTokens.generate();
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void generatesDifferentTokenEveryTime() {
        assertThat(OpaqueTokens.generate()).isNotEqualTo(OpaqueTokens.generate());
    }

    @Test
    void hashIsDeterministicHexAndNotTheRawToken() {
        String raw = OpaqueTokens.generate();
        String hash = OpaqueTokens.hash(raw);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash).isEqualTo(OpaqueTokens.hash(raw));
        assertThat(hash).isNotEqualTo(raw);
    }

    @Test
    void matchesKnownSha256Vector() {
        assertThat(OpaqueTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}