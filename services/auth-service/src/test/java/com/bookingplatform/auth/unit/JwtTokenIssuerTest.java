package com.bookingplatform.auth.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.bookingplatform.auth.config.AuthProperties;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.IssuedToken;
import com.bookingplatform.auth.domain.model.Role;
import com.bookingplatform.auth.infrastructure.security.JwtTokenIssuer;
import com.bookingplatform.auth.infrastructure.security.RsaKeys;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenIssuerTest {

    /*
     * TEST FIXTURES:
     *
     * Tạo RSA KeyPair để sử dụng trong các test.
     *
     * - Private Key: JwtTokenIssuer dùng để ký JWT.
     * - Public Key: NimbusJwtDecoder dùng để xác minh chữ ký JWT.
     *
     * Mỗi instance của test class sẽ có một RSA key riêng.
     */
    private final RSAKey key = RsaKeys.generate();

    /*
     * Sử dụng UTC Clock để lấy thời gian hiện tại.
     *
     * Cả JwtTokenIssuer và Credential đều sử dụng cùng Clock này
     * để đảm bảo thời gian xử lý nhất quán.
     */
    private final Clock clock = Clock.systemUTC();

    /*
     * Khởi tạo JwtTokenIssuer trực tiếp, không cần Spring Context.
     *
     * 1. NimbusJwtEncoder:
     * Dùng RSA Private Key để ký JWT.
     *
     * 2. ImmutableJWKSet:
     * Cung cấp RSA Key cho encoder.
     *
     * 3. AuthProperties:
     * issuer = http://localhost:8081
     * accessTokenTtl = 15 phút
     *
     * 4. Clock:
     * Cung cấp thời gian hiện tại.
     *
     * Đây là cách tạo dependency thủ công để unit test độc lập.
     */
    private final JwtTokenIssuer issuer = new JwtTokenIssuer(
            new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key))),
            key,
            new AuthProperties(
                    "http://localhost:8081",
                    Duration.ofMinutes(15),
                    ""),
            clock);

    /**
     * TEST 1: issuesTokenThatVerifiesWithPublicKey()
     *
     * Mục đích:
     * Kiểm tra JwtTokenIssuer có tạo và ký JWT thành công không.
     *
     * Đồng thời kiểm tra:
     * - JWT có thể xác minh bằng Public Key tương ứng.
     * - Subject (sub) chứa userId.
     * - Claim roles chứa đúng role.
     * - Claim iss chứa đúng issuer.
     * - Header có kid và alg = RS256.
     */
    @Test
    void issuesTokenThatVerifiesWithPublicKey() throws Exception {

        // ARRANGE:
        // Tạo Credential của một người dùng có role HOST.
        Credential credential = Credential.register(
                "host@example.com",
                "hash",
                Role.HOST,
                clock.instant());

        // ACT:
        // Yêu cầu JwtTokenIssuer tạo access token cho Credential.
        IssuedToken token = issuer.issue(credential);

        // ASSERT - VERIFY SIGNATURE:
        // Sử dụng Public Key tương ứng để decode và xác minh JWT.
        // Nếu chữ ký không hợp lệ, decode() sẽ ném exception.
        Jwt jwt = NimbusJwtDecoder
                .withPublicKey(key.toRSAPublicKey())
                .build()
                .decode(token.value());

        // Kiểm tra claim sub phải bằng userId của Credential.
        assertThat(jwt.getSubject())
                .isEqualTo(credential.getUserId().toString());

        // Kiểm tra JWT chứa đúng role HOST.
        assertThat(jwt.getClaimAsStringList("roles"))
                .containsExactly("HOST");

        // Kiểm tra issuer của token.
        assertThat(jwt.getClaimAsString("iss"))
                .isEqualTo("http://localhost:8081");

        // Kiểm tra header kid khớp với Key ID của RSA Key.
        // Giúp xác định key nào được sử dụng để ký token.
        assertThat(jwt.getHeaders().get("kid"))
                .isEqualTo(key.getKeyID());

        // Kiểm tra thuật toán ký token là RS256.
        assertThat(jwt.getHeaders().get("alg"))
                .hasToString("RS256");
    }

    /**
     * TEST 2: tokenLivesExactlyFifteenMinutes()
     *
     * Mục đích:
     * Kiểm tra thời gian hiệu lực của access token là chính xác 15 phút.
     *
     * Kiểm tra:
     * - issuedAt: Thời điểm token được phát hành.
     * - expiresAt: Thời điểm token hết hạn.
     * - expiresInSeconds: Thời gian sống được trả về trong IssuedToken.
     */
    @Test
    void tokenLivesExactlyFifteenMinutes() throws Exception {

        // ARRANGE:
        // Tạo Credential của người dùng có role GUEST.
        Credential credential = Credential.register(
                "a@example.com",
                "hash",
                Role.GUEST,
                clock.instant());

        // ACT:
        // Phát hành access token.
        IssuedToken token = issuer.issue(credential);

        // ASSERT - DECODE TOKEN:
        // Giải mã và xác minh token bằng Public Key.
        Jwt jwt = NimbusJwtDecoder
                .withPublicKey(key.toRSAPublicKey())
                .build()
                .decode(token.value());

        // Kiểm tra khoảng cách giữa thời điểm phát hành và hết hạn
        // phải đúng 15 phút.
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(15));

        // Kiểm tra thời gian sống trả về cho client là 900 giây.
        // 15 phút = 15 * 60 = 900 giây.
        assertThat(token.expiresInSeconds())
                .isEqualTo(900);
    }

    /**
     * TEST 3: tokenDoesNotContainEmail()
     *
     * Mục đích:
     * Đảm bảo JWT không chứa email của người dùng.
     *
     * Lý do:
     * Project thiết kế JWT chỉ chứa những claim cần thiết,
     * chẳng hạn sub và roles, không đưa email vào token.
     *
     * Điều này giúp hạn chế thông tin cá nhân xuất hiện
     * trong nội dung JWT mà client có thể đọc được.
     */
    @Test
    void tokenDoesNotContainEmail() throws Exception {

        // ARRANGE:
        // Tạo Credential với email bí mật.
        Credential credential = Credential.register(
                "secret@example.com",
                "hash",
                Role.GUEST,
                clock.instant());

        // ACT:
        // Phát hành JWT từ Credential.
        IssuedToken token = issuer.issue(credential);

        // ASSERT - DECODE TOKEN:
        // Giải mã token bằng Public Key.
        Jwt jwt = NimbusJwtDecoder
                .withPublicKey(key.toRSAPublicKey())
                .build()
                .decode(token.value());

        // Kiểm tra JWT không có claim tên "email".
        assertThat(jwt.getClaims())
                .doesNotContainKey("email");
    }
}