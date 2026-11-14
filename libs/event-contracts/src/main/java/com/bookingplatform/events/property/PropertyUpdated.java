package com.bookingplatform.events.property;

/**
 * Khách sạn ĐANG ĐĂNG vừa được sửa (thông tin hoặc loại phòng). Search-service
 * cập nhật index.
 */
public record PropertyUpdated(PropertySnapshot property) {
}
