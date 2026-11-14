package com.bookingplatform.events.property;

/**
 * Khách sạn vừa được đăng (DRAFT sang PUBLISHED). Search-service thêm vào
 * index.
 */
public record PropertyPublished(PropertySnapshot property) {
}
