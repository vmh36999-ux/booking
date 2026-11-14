package com.bookingplatform.property.application.usecase;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.model.PropertyView;
import com.bookingplatform.property.domain.port.RoomTypeRepository;

/** Thao tác của ADMIN: khóa và mở khóa khách sạn vi phạm. */
@Service
public class PropertyModeration {
    private final PropertyAccess access;
    private final RoomTypeRepository roomTypes;
    private final PropertyEvents events;
    private final Clock clock;

    public PropertyModeration(PropertyAccess access, RoomTypeRepository roomTypes, PropertyEvents events, Clock clock) {
        this.access = access;
        this.roomTypes = roomTypes;
        this.events = events;
        this.clock = clock;
    }

    /**
     * PUBLISHED sang SUSPENDED. Phát PropertyUnpublished để search gỡ khỏi index.
     */
    @Transactional
    public PropertyView suspend(UUID propertyId) {
        Property property = access.loadAny(propertyId);
        property.suspend(clock.instant());
        events.unpublished(property, "ADMIN_SUSPENDED");
        return new PropertyView(property, roomTypes.findByProperty(propertyId));
    }

    /** SUSPENDED sang DRAFT. Host phải tự đăng lại, nên chưa phát sự kiện nào. */
    @Transactional
    public PropertyView reinstate(UUID propertyId) {
        Property property = access.loadAny(propertyId);
        property.reinstate(clock.instant());
        return new PropertyView(property, roomTypes.findByProperty(propertyId));
    }
}
