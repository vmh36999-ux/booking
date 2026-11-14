package com.bookingplatform.property.application.usecase;

import java.util.List;

import org.springframework.stereotype.Component;

import com.bookingplatform.common.outbox.OutboxWriter;
import com.bookingplatform.common.web.CorrelationIdFilter;
import com.bookingplatform.events.EventEnvelope;
import com.bookingplatform.events.EventTypes;
import com.bookingplatform.events.Topics;
import com.bookingplatform.events.property.PropertyPublished;
import com.bookingplatform.events.property.PropertySnapshot;
import com.bookingplatform.events.property.PropertyUnpublished;
import com.bookingplatform.events.property.PropertyUpdated;
import com.bookingplatform.events.property.RoomTypeCreated;
import com.bookingplatform.events.property.RoomTypeData;
import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.model.RoomType;
import org.slf4j.MDC;

/**
 * Dựng và ghi các sự kiện của khách sạn vào outbox. Gọi trong transaction của
 * use case.
 */
@Component
public class PropertyEvents {
    private final OutboxWriter outbox;

    /**
     * Dựng và ghi các sự kiện của khách sạn vào outbox. Gọi trong transaction của
     * use case.
     */
    public PropertyEvents(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void published(Property property, List<RoomType> roomTypes) {
        write(EventTypes.PROPERTY_PUBLISHED, property, new PropertyPublished(snapshot(property, roomTypes)));
    }

    public void updated(Property property, List<RoomType> roomTypes) {
        write(EventTypes.PROPERTY_UPDATED, property, new PropertyUpdated(snapshot(property, roomTypes)));

    }

    public void unpublished(Property property, String reason) {
        write(EventTypes.PROPERTY_UNPUBLISHED, property,
                new PropertyUnpublished(property.getId(), property.getHostId(), reason));
    }

    public void roomTypeCreated(Property property, RoomType roomType) {
        write(EventTypes.ROOM_TYPE_CREATED, property,
                new RoomTypeCreated(property.getId(), roomType.getId(), roomType.getName(), roomType.getMaxGuests(),
                        roomType.getBedConfig(), roomType.getTotalRooms(), roomType.getBasePriceMinor(),
                        property.getCurrency()));
    }

    /**
     * Ảnh chụp đầy đủ khách sạn: đủ để service khác dựng dữ liệu mà không phải gọi
     * ngược.
     */
    public static PropertySnapshot snapshot(Property p, List<RoomType> roomTypes) {
        List<RoomTypeData> rooms = roomTypes.stream()
                .map(r -> new RoomTypeData(r.getId(), r.getName(), r.getMaxGuests(), r.getBedConfig(),
                        r.getTotalRooms(), r.getBasePriceMinor()))
                .toList();
        int maxGuests = roomTypes.stream().mapToInt(RoomType::getMaxGuests).max().orElse(0);
        long priceFrom = roomTypes.stream().mapToLong(RoomType::getBasePriceMinor).min().orElse(0L);
        return new PropertySnapshot(p.getId(), p.getHostId(), p.getName(), p.getDescription(), p.getCity(),
                p.getCountry(), p.getAddress(), p.getLatitude(), p.getLongitude(), p.getAmenityCodes(),
                maxGuests, priceFrom, p.getCurrency(), rooms);

    }

    private void write(String type, Property property, Object data) {
        outbox.write(Topics.PROPERTY_EVENTS, EventEnvelope.of(
                type, property.getId().toString(), MDC.get(CorrelationIdFilter.MDC_KEY), data));
    }
}
