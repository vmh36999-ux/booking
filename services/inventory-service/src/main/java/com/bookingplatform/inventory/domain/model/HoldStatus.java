package com.bookingplatform.inventory.domain.model;

public enum HoldStatus {
    /** Đang giữ chỗ, chờ thanh toán. Tính vào cột "held". */
    HELD,
    /** Đã xác nhận (đã thanh toán). Chuyển từ "held" sang "booked". */
    CONFIRMED,
    /** Đã nhả chủ động (thanh toán lỗi, khách bỏ). Trả phòng về. */
    RELEASED,
    /** Hết hạn mà chưa xác nhận. Trả phòng về. */
    EXPIRED
}
