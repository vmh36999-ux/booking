package com.bookingplatform.property.domain.model;

import java.util.List;

/** Một khách sạn kèm các loại phòng của nó. */
public record PropertyView(Property property, List<RoomType> roomTypes) {
}
