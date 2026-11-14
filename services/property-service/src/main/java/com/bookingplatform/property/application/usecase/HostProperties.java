package com.bookingplatform.property.application.usecase;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingplatform.property.domain.port.PropertyRepository;
import com.bookingplatform.property.domain.port.RoomTypeRepository;
import com.bookingplatform.property.domain.exception.InvalidPropertyStateException;
import com.bookingplatform.property.domain.exception.UnknownAmenityException;
import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.model.PropertyDetails;
import com.bookingplatform.property.domain.model.PropertyStatus;
import com.bookingplatform.property.domain.model.PropertyView;
import com.bookingplatform.property.domain.model.RoomType;
import com.bookingplatform.property.domain.port.AmenityCatalog;

/** Các thao tác của HOST trên khách sạn của chính mình. */
@Service
public class HostProperties {
    private final PropertyRepository properties;
    private final RoomTypeRepository roomTypes;
    private final AmenityCatalog amenities;
    private final PropertyAccess access;
    private final PropertyEvents events;
    private final Clock clock;

    public HostProperties(PropertyRepository properties, RoomTypeRepository roomTypes, AmenityCatalog amenities,
            PropertyAccess access, PropertyEvents events, Clock clock) {
        this.properties = properties;
        this.roomTypes = roomTypes;
        this.amenities = amenities;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Tạo bản nháp. Chưa phát sự kiện nào vì chưa ai cần biết đến khách sạn chưa
     * đăng.
     */
    @Transactional
    public PropertyView create(UUID hostId, PropertyDetails details) {
        validateAmenities(details.amenities());
        Property property = properties.insert(Property.create(hostId, details, clock.instant()));
        return new PropertyView(property, List.of());
    }

    @Transactional
    public PropertyView update(UUID hostId, UUID propertyId, PropertyDetails details) {
        Property property = access.loadOwned(hostId, propertyId);
        validateAmenities(details.amenities());
        property.update(details, clock.instant());
        List<RoomType> rooms = roomTypes.findByProperty(propertyId);
        if (property.getStatus() == PropertyStatus.PUBLISHED) {
            events.updated(property, rooms);
        }
        return new PropertyView(property, rooms);
    }

    @Transactional(readOnly = true)
    public PropertyView get(UUID hostId, UUID propertyId) {
        Property property = access.loadOwned(hostId, propertyId);
        return new PropertyView(property, roomTypes.findByProperty(propertyId));
    }

    @Transactional(readOnly = true)
    public List<Property> list(UUID hostId, UUID after, int limit) {
        return properties.findByHost(hostId, after, limit);
    }

    @Transactional
    public void delete(UUID hostId, UUID propertyId) {
        Property property = access.loadOwned(hostId, propertyId);
        if (property.getStatus() != PropertyStatus.DRAFT) {
            throw new InvalidPropertyStateException(
                    "Only properties in DRAFT status can be deleted. Please unpublish first (current status: "
                            + property.getStatus() + ")");
        }
        properties.delete(property);
    }

    /**
     * DRAFT sang PUBLISHED. Cần ít nhất một loại phòng. Phát PropertyPublished kèm
     * ảnh chụp đầy đủ.
     */
    @Transactional
    public PropertyView publish(UUID hostId, UUID propertyId) {
        Property property = access.loadOwned(hostId, propertyId);
        List<RoomType> rooms = roomTypes.findByProperty(propertyId);
        if (rooms.isEmpty()) {
            throw new InvalidPropertyStateException(
                    "At least one room type is required before publishing the property.");
        }
        property.publish(clock.instant());
        events.published(property, rooms);
        return new PropertyView(property, rooms);
    }

    /** PUBLISHED sang DRAFT. Phát PropertyUnpublished để search xóa khỏi index. */
    @Transactional
    public PropertyView unpublish(UUID hostId, UUID propertyId) {
        Property property = access.loadOwned(hostId, propertyId);
        property.unpublish(clock.instant());
        events.unpublished(property, "HOST_UNPUBLISHED");
        return new PropertyView(property, roomTypes.findByProperty(propertyId));
    }

    private void validateAmenities(Set<String> codes) {
        Set<String> unknown = amenities.findUnknown(codes);
        if (!unknown.isEmpty()) {
            throw new UnknownAmenityException(unknown);
        }
    }

}
