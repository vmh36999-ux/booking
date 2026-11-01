package com.bookingplatform.auth.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookingplatform.auth.application.usecase.Login;
import com.bookingplatform.auth.domain.exception.InvalidCredentialsException;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.IssuedToken;
import com.bookingplatform.auth.domain.model.Role;
import com.bookingplatform.auth.domain.port.CredentialRepository;
import com.bookingplatform.auth.domain.port.TokenIssuer;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginTest {

    // Mock repository để giả lập thao tác truy vấn user trong database.
    private final CredentialRepository repository = mock(CredentialRepository.class);

    // Mock TokenIssuer để giả lập việc tạo JWT, không cần ký token thật trong unit
    // test.
    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);

    // Tạo PasswordEncoder giống cấu hình thực tế của ứng dụng.
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    // Khởi tạo Login với các dependency đã được mock.
    private final Login login = new Login(repository, encoder, tokenIssuer);

    // Tạo một user mẫu đã đăng ký, lưu password dưới dạng BCrypt hash.
    private Credential existingUser() {
        return Credential.register(
                "user@example.com",
                encoder.encode("Passw0rd!2026"),
                Role.GUEST,
                Instant.now());
    }

    @Test
    void returnsTokenForCorrectPassword() {
        // Arrange: Chuẩn bị user và JWT giả lập.
        Credential user = existingUser();
        IssuedToken token = new IssuedToken("jwt", Instant.now().plusSeconds(900), 900);

        // Khi tìm email này, repository trả về user đã chuẩn bị.
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        // Khi TokenIssuer được gọi với user này, trả về JWT giả lập.
        when(tokenIssuer.issue(user)).thenReturn(token);

        // Act: Đăng nhập bằng email viết hoa và có khoảng trắng ở đầu.
        IssuedToken result = login.execute("  USER@example.com", "Passw0rd!2026");

        // Assert: Kết quả trả về phải chính là token đã cấu hình.
        assertThat(result).isSameAs(token);
    }

    @Test
    void rejectsWrongPassword() {
        // Arrange: Email tồn tại trong database.
        when(repository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(existingUser()));

        // Act & Assert: Password sai thì phải ném InvalidCredentialsException.
        assertThatThrownBy(() -> login.execute("user@example.com", "sai-mat-khau"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejectsUnknownEmailWithSameError() {
        // Arrange: Email không tồn tại trong database.
        when(repository.findByEmail("nobody@example.com"))
                .thenReturn(Optional.empty());

        // Act & Assert: Email không tồn tại cũng phải trả cùng loại lỗi
        // và cùng message với trường hợp nhập sai password.
        assertThatThrownBy(() -> login.execute("nobody@example.com", "Passw0rd!2026"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is not correct");
    }
}