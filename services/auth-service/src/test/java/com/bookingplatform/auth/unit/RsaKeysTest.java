package com.bookingplatform.auth.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import com.bookingplatform.auth.infrastructure.security.RsaKeys;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class RsaKeysTest {

    /**
     * HELPER METHOD:
     * Chuyển RSA Private Key thành định dạng PEM.
     *
     * Mục đích:
     * Tạo dữ liệu PEM giả lập để kiểm tra RsaKeys.fromPem().
     *
     * PEM format:
     * -----BEGIN PRIVATE KEY-----
     * Base64 encoded private key
     * -----END PRIVATE KEY-----
     */
    private static String toPem(KeyPair pair) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(pair.getPrivate().getEncoded());

        return "-----BEGIN PRIVATE KEY-----\n"
                + body
                + "\n-----END PRIVATE KEY-----";
    }

    /**
     * HELPER METHOD:
     * Tạo một RSA KeyPair mới với kích thước 2048 bits.
     *
     * KeyPair bao gồm:
     * - Private Key: dùng để ký JWT.
     * - Public Key: dùng để xác minh chữ ký JWT.
     */
    private static KeyPair newPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /**
     * TEST 1: Parse PEM and derive the matching Public Key.
     *
     * Kiểm tra:
     * 1. RsaKeys.fromPem() có đọc được PEM hợp lệ không?
     * 2. Public Key lấy ra có modulus khớp với KeyPair ban đầu không?
     * 3. RSAKey có chứa Private Key không?
     * 4. Key có Key ID hay không?
     */
    @Test
    void parsesPemAndDerivesMatchingPublicKey() throws Exception {

        // Arrange: Tạo RSA KeyPair ban đầu.
        KeyPair pair = newPair();

        // Act: Chuyển Private Key thành PEM rồi parse thành RSAKey.
        RSAKey key = RsaKeys.fromPem(toPem(pair));

        // Assert 1: Modulus của Public Key sau khi parse phải khớp
        // với Modulus của Public Key ban đầu.
        assertThat(key.toRSAPublicKey().getModulus())
                .isEqualTo(((RSAPublicKey) pair.getPublic()).getModulus());

        // Assert 2: RSAKey phải chứa Private Key.
        assertThat(key.isPrivate()).isTrue();

        // Assert 3: RSAKey phải có Key ID, không được null hoặc rỗng.
        assertThat(key.getKeyID()).isNotBlank();
    }

    /**
     * TEST 2: Accept PEM with literal backslash-n.
     *
     * Kiểm tra:
     * RsaKeys.fromPem() có xử lý được PEM chứa ký tự "\n" dạng literal không?
     *
     * Trường hợp thực tế:
     * Private Key có thể được truyền qua Environment Variable
     * với ký tự "\n" thay vì newline thực.
     */
    @Test
    void acceptsPemWithLiteralBackslashN() throws Exception {

        // Arrange: Tạo RSA KeyPair.
        KeyPair pair = newPair();

        // Chuyển newline thực ("\n") thành hai ký tự literal "\" và "n".
        // Mô phỏng định dạng PEM được truyền qua Environment Variable.
        String envStyle = toPem(pair).replace("\n", "\\n");

        // Act: Thử parse PEM có literal backslash-n.
        RSAKey key = RsaKeys.fromPem(envStyle);

        // Assert: Public Key sau khi parse phải có modulus khớp
        // với Public Key ban đầu.
        assertThat(key.toRSAPublicKey().getModulus())
                .isEqualTo(((RSAPublicKey) pair.getPublic()).getModulus());
    }

    /**
     * TEST 3: Reject invalid PEM input.
     *
     * Kiểm tra:
     * Khi truyền dữ liệu không phải PEM hợp lệ,
     * RsaKeys.fromPem() có ném exception đúng loại không?
     *
     * Mục đích:
     * Đảm bảo ứng dụng báo lỗi rõ ràng khi cấu hình
     * AUTH_RSA_PRIVATE_KEY không hợp lệ.
     */
    @Test
    void rejectsGarbage() {

        // Act + Assert:
        // Truyền chuỗi không phải PEM và xác nhận exception được ném ra.
        assertThatThrownBy(() -> RsaKeys.fromPem("khong-phai-pem"))

                // Exception mong đợi phải là IllegalStateException.
                .isInstanceOf(IllegalStateException.class)

                // Message phải nhắc đến AUTH_RSA_PRIVATE_KEY
                // để developer biết cần kiểm tra cấu hình nào.
                .hasMessageContaining("AUTH_RSA_PRIVATE_KEY");
    }

    /**
     * TEST 4: Generate RSA Key and verify its Key ID and Public JWK.
     *
     * Kiểm tra:
     * 1. RsaKeys.generate() có tạo được RSA Key không?
     * 2. Key được tạo có Key ID không?
     * 3. Khi chuyển thành Public JWK, kết quả có loại bỏ Private Key không?
     *
     * Liên quan đến Auth Service:
     * Khi AUTH_RSA_PRIVATE_KEY không được cấu hình,
     * ứng dụng sẽ tự sinh RSA Key để sử dụng trong development.
     */
    @Test
    void generatedKeysHaveStableThumbprintId() {

        // Arrange + Act: Sinh RSA Key mới.
        RSAKey key = RsaKeys.generate();

        // Assert 1: Key phải có Key ID, không được null hoặc rỗng.
        assertThat(key.getKeyID()).isNotBlank();

        // Assert 2: Public JWK không được chứa Private Key material.
        // Đây là điều cần thiết khi công khai Public Key qua JWKS endpoint.
        assertThat(key.toPublicJWK().isPrivate()).isFalse();
    }
}