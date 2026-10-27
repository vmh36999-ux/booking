package com.bookingplatform.events;

/** Tên topic Kafka. Mỗi topic có thêm "<tên>.DLT" làm dead-letter. */
public final class Topics {
    public static final String USER_EVENTS = "user.events";
    public static final String PROPERTY_EVENTS = "property.events";
    public static final String PRICING_EVENTS = "pricing.events";
    public static final String BOOKING_EVENTS = "booking.events";
    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String REVIEW_EVENTS = "review.events";

    private Topics() {
    }
}
