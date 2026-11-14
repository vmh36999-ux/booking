package com.bookingplatform.events.property;

import java.util.UUID;

/**
 * Khách sạn không còn được đăng. reason: HOST_UNPUBLISHED hoặc ADMIN_SUSPENDED.
 * Search-service xóa khỏi index.
 */
public record PropertyUnpublished(UUID propertyId, UUID hostId, String reason) {
}
