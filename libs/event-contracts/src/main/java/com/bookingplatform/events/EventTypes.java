package com.bookingplatform.events;

/** Giá trị của trường "type" trong EventEnvelope. */
public final class EventTypes {
    public static final String USER_REGISTERED = "UserRegistered";
    public static final String PROFILE_UPDATED = "ProfileUpdated";

    public static final String PROPERTY_PUBLISHED = "PropertyPublished";
    public static final String PROPERTY_UPDATED = "PropertyUpdated";
    public static final String PROPERTY_UNPUBLISHED = "PropertyUnpublished";
    public static final String ROOM_TYPE_CREATED = "RoomTypeCreated";

    public static final String PRICE_CHANGED = "PriceChanged";

    public static final String PAYMENT_REQUESTED = "PaymentRequested";
    public static final String BOOKING_CONFIRMED = "BookingConfirmed";
    public static final String BOOKING_CANCELLED = "BookingCancelled";
    public static final String BOOKING_COMPLETED = "BookingCompleted";
    public static final String REFUND_REQUESTED = "RefundRequested";

    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String REFUND_COMPLETED = "RefundCompleted";

    public static final String REVIEW_AGGREGATED = "ReviewAggregated";

    private EventTypes() {
    }
}
