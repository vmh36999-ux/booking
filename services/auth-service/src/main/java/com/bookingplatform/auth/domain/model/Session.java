package com.bookingplatform.auth.domain.model;

/**
 * Kết quả đăng nhập hoặc làm mới: access token (JWT) và refresh token (chuỗi
 * ngẫu nhiên).
 */
public record Session(IssuedToken accessToken, String refreshToken, long refreshExpiresInSeconds) {
}