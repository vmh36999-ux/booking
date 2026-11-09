package com.bookingplatform.auth.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingplatform.auth.application.usecase.RegisterUser;
import com.bookingplatform.auth.domain.exception.EmailAlreadyUsedException;
import com.bookingplatform.auth.domain.exception.InvalidRegistrationException;
import com.bookingplatform.auth.domain.model.AccountType;
import com.bookingplatform.auth.domain.model.Credential;
import com.bookingplatform.auth.domain.model.Role;
import com.bookingplatform.auth.domain.port.CredentialRepository;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegisterUserTest {

    /*
     * MOCK REPOSITORY:
     *
     * Tạo một CredentialRepository giả lập bằng Mockito.
     *
     * Mục đích:
     * - Không cần kết nối PostgreSQL.
     * - Không thực hiện INSERT/SELECT thật.
     * - Có thể chủ động giả lập kết quả khi gọi repository.
     */
    private final CredentialRepository repository = mock(CredentialRepository.class);

    /*
     * REAL PASSWORD ENCODER:
     *
     * Tạo PasswordEncoder thật của Spring Security.
     *
     * createDelegatingPasswordEncoder():
     * - Mặc định sử dụng BCrypt để hash password.
     * - Hash được lưu kèm prefix {bcrypt}.
     *
     * Trong test này, chúng ta sử dụng encoder thật,
     * không mock PasswordEncoder.
     */
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    /*
     * KHỞI TẠO USE CASE:
     *
     * RegisterUser nhận 3 dependencies:
     * 1. repository: lưu Credential.
     * 2. encoder: hash password.
     * 3. clock: lấy thời gian hiện tại.
     *
     * Clock.systemUTC() cung cấp thời gian UTC.
     *
     * Đây là unit test nên khởi tạo trực tiếp object,
     * không cần Spring ApplicationContext.
     */
    private final RegisterUser registerUser = new RegisterUser(repository, encoder, Clock.systemUTC());

    /*
     * MOCK SETUP:
     *
     * Khi RegisterUser gọi repository.save(credential),
     * Mockito sẽ trả lại chính Credential được truyền vào.
     *
     * Vì repository đang là mock nên nếu không cấu hình,
     * save() thường trả về null (giá trị mặc định).
     *
     * thenAnswer(inv -> inv.getArgument(0)):
     * Lấy argument đầu tiên của lời gọi save() và trả lại nó.
     *
     * Nhờ vậy, test có thể nhận được Credential sau khi lưu.
     */
    RegisterUserTest() {
        when(repository.save(any(Credential.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * TEST 1: Normalize email and hash password.
     *
     * Mục đích:
     * Kiểm tra nghiệp vụ đăng ký tài khoản thông thường.
     *
     * Các yêu cầu:
     * - Email phải được trim khoảng trắng.
     * - Email phải được chuyển thành chữ thường.
     * - Password phải được hash, không lưu plaintext.
     * - Password hash phải xác thực được bằng encoder.
     * - AccountType GUEST phải tương ứng Role GUEST.
     * - Credential mới phải có trạng thái ACTIVE.
     */
    @Test
    void normalizesEmailAndHashesPassword() {

        // ARRANGE + ACT:
        // Gọi use case đăng ký với email có khoảng trắng
        // và chữ hoa để kiểm tra email normalization.
        Credential result = registerUser.execute(
                "  Test.User@Example.COM ",
                "Passw0rd!2026",
                AccountType.GUEST);

        // ASSERT 1:
        // Email phải được trim và chuyển thành lowercase.
        assertThat(result.getEmail())
                .isEqualTo("test.user@example.com");

        // ASSERT 2:
        // Password lưu trong Credential không được là plaintext.
        assertThat(result.getPasswordHash())
                .isNotEqualTo("Passw0rd!2026");

        // ASSERT 3:
        // PasswordEncoder phải xác nhận password gốc
        // khớp với password hash đã tạo.
        assertThat(encoder.matches(
                "Passw0rd!2026",
                result.getPasswordHash())).isTrue();

        // ASSERT 4:
        // AccountType GUEST phải được chuyển thành Role GUEST.
        assertThat(result.getRoles())
                .containsExactly(Role.GUEST);

        // ASSERT 5:
        // Credential đăng ký mới phải ở trạng thái ACTIVE.
        assertThat(result.isActive()).isTrue();
    }

    /**
     * TEST 2: Host account gets HOST role.
     *
     * Mục đích:
     * Kiểm tra khi người dùng đăng ký với AccountType.HOST,
     * Credential phải được gán đúng Role.HOST.
     */
    @Test
    void hostAccountGetsHostRole() {

        // ARRANGE + ACT:
        // Đăng ký tài khoản với loại HOST.
        Credential result = registerUser.execute(
                "host@example.com",
                "Passw0rd!2026",
                AccountType.HOST);

        // ASSERT:
        // User phải được gán Role.HOST,
        // không phải Role.GUEST.
        assertThat(result.getRoles())
                .containsExactly(Role.HOST);
    }

    /**
     * TEST 3: Missing account type defaults to GUEST.
     *
     * Mục đích:
     * Kiểm tra trường hợp client không truyền accountType.
     *
     * Quy tắc nghiệp vụ:
     * Nếu accountType = null thì mặc định là GUEST.
     */
    @Test
    void missingAccountTypeDefaultsToGuest() {

        // ARRANGE + ACT:
        // Truyền null thay vì AccountType.GUEST hoặc HOST.
        Credential result = registerUser.execute(
                "a@example.com",
                "Passw0rd!2026",
                null);

        // ASSERT:
        // Hệ thống phải tự động gán Role.GUEST.
        assertThat(result.getRoles())
                .containsExactly(Role.GUEST);
    }

    /**
     * TEST 4: Reject duplicate email.
     *
     * Mục đích:
     * Kiểm tra hệ thống từ chối đăng ký khi email đã tồn tại.
     *
     * Không chỉ kiểm tra exception,
     * test còn xác nhận repository.save() không được gọi.
     */
    @Test
    void rejectsDuplicateEmail() {

        // ARRANGE:
        // Giả lập repository xác nhận email đã tồn tại.
        when(repository.existsByEmail("dup@example.com"))
                .thenReturn(true);

        // ACT + ASSERT:
        // Email đầu vào được viết hoa nhưng sau normalization
        // sẽ trở thành dup@example.com.
        //
        // RegisterUser phải ném EmailAlreadyUsedException.
        assertThatThrownBy(() -> registerUser.execute(
                "DUP@example.com",
                "Passw0rd!2026",
                AccountType.GUEST)).isInstanceOf(EmailAlreadyUsedException.class);

        // VERIFY:
        // Đảm bảo không thực hiện lưu Credential
        // khi email đã tồn tại.
        verify(repository, never()).save(any());
    }

    /**
     * TEST 5: Reject password longer than 72 bytes.
     *
     * Mục đích:
     * Kiểm tra hệ thống từ chối password vượt giới hạn BCrypt.
     *
     * BCrypt chỉ hỗ trợ tối đa 72 bytes đầu vào
     * trong các implementation phổ biến.
     *
     * Nếu vượt quá giới hạn, hệ thống phải reject
     * thay vì để password bị truncate.
     */
    @Test
    void rejectsPasswordLongerThan72Bytes() {

        // ARRANGE:
        // Tạo password gồm 73 ký tự 'a'.
        //
        // Vì 'a' là ASCII nên mỗi ký tự chiếm 1 byte UTF-8.
        // Tổng cộng: 73 characters = 73 bytes.
        String tooLong = "a".repeat(73);

        // ACT + ASSERT:
        // Đăng ký với password dài hơn 72 bytes.
        // Hệ thống phải ném InvalidRegistrationException.
        assertThatThrownBy(() -> registerUser.execute(
                "a@example.com",
                tooLong,
                AccountType.GUEST)).isInstanceOf(InvalidRegistrationException.class);
    }
}