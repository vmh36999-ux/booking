package com.bookingplatform.events.user;

import java.util.List;
import java.util.UUID;

/**
 * Sự kiện: có một tài khoản mới. Chứa đủ thông tin để các service khác tự xử
 * lý, không phải gọi ngược lại auth-service.
 */
public record UserRegistered(UUID userId, String email, List<String> roles) {
}