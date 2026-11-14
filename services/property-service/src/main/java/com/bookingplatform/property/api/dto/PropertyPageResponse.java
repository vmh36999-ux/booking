package com.bookingplatform.property.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Một trang danh sách. nextCursor: truyền vào "after" để lấy trang kế; null
 * nghĩa là hết.
 */
public record PropertyPageResponse(List<PropertySummaryResponse> items, UUID nextCursor) {
}
