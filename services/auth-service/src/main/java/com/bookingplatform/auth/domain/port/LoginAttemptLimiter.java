package com.bookingplatform.auth.domain.port;

public interface LoginAttemptLimiter {

    /**
     * Ném TooManyAttemptsException nếu email hoặc IP đã đăng nhập sai quá số lần
     * cho phép.
     */
    void assertAllowed(String normalizedEmail, String ipAddress);

    void recordFailure(String normalizedEmail, String ipAddress);

    /** Đăng nhập đúng thì xóa bộ đếm theo email (bộ đếm theo IP vẫn giữ). */
    void recordSuccess(String normalizedEmail);
}