package com.bookingplatform.property.domain.model;

public enum PropertyStatus {
    /** Bản nháp: chỉ host thấy. */
    DRAFT,
    /** Đang đăng: khách thấy, đi vào search. */
    PUBLISHED,
    /** Bị quản trị viên tạm khóa: không ai thấy, host không tự đăng lại được. */
    SUSPENDED
}
