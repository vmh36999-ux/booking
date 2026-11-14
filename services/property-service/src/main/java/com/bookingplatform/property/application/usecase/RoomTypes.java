package com.bookingplatform.property.application.usecase;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.annotation.Transient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingplatform.property.domain.exception.InvalidPropertyStateException;
import com.bookingplatform.property.domain.exception.RoomTypeNotFoundException;
import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.model.PropertyStatus;
import com.bookingplatform.property.domain.model.RoomType;
import com.bookingplatform.property.domain.model.RoomTypeDetails;
import com.bookingplatform.property.domain.port.RoomTypeRepository;

/** Thêm, sửa, xóa loại phòng của khách sạn thuộc về host. */
@Service
public class RoomTypes {
    private final RoomTypeRepository roomTypes;
    private final PropertyAccess access;
    private final PropertyEvents events;
    private final Clock clock;

    public RoomTypes(RoomTypeRepository roomTypes, PropertyAccess access, PropertyEvents events, Clock clock) {
        this.roomTypes = roomTypes;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Phát RoomTypeCreated (inventory dùng). Nếu khách sạn đang đăng, phát thêm
     * PropertyUpdated cho search.
     */
    @Transactional
    public RoomType add(UUID hostId, UUID propertyId, RoomTypeDetails details) {
        Property property = access.loadOwned(hostId, propertyId);
        RoomType roomType = roomTypes.insert(RoomType.create(propertyId, details, clock.instant()));
        events.roomTypeCreated(property, roomType);
        publishUpdateIfLive(property);
        return roomType;
    }

    @Transactional
    public RoomType update(UUID hostId, UUID propertyId, UUID roomTypeId, RoomTypeDetails details) {
        Property property = access.loadOwned(hostId, propertyId);
        RoomType roomType = find(propertyId, roomTypeId);
        roomType.update(details);
        publishUpdateIfLive(property);
        return roomType;
    }

    @Transactional
    public void delete(UUID hostId, UUID propertyId, UUID roomTypeId) {
        Property property = access.loadOwned(hostId, propertyId);
        RoomType roomType = find(propertyId, roomTypeId);
        boolean live = property.getStatus() == PropertyStatus.PUBLISHED;
        if (live && roomTypes.countByProperty(propertyId) <= 1) {
            throw new InvalidPropertyStateException(
                    "Cannot delete the last room type of a published property. Please unpublish first.");
        }
        roomTypes.delete(roomType);
        publishUpdateIfLive(property);
    }

    /**
     * Loại phòng phải THUỘC khách sạn này: tránh sửa loại phòng của khách sạn khác
     * bằng cách đổi id.
     */
    private RoomType find(UUID propertyId, UUID roomTypeId) {
        return roomTypes.findById(roomTypeId)
                .filter(r -> r.getPropertyId().equals(propertyId))
                .orElseThrow(RoomTypeNotFoundException::new);
    }

    private void publishUpdateIfLive(Property property) {
        if (property.getStatus() == PropertyStatus.PUBLISHED) {
            events.updated(property, roomTypes.findByProperty(property.getId()));
        }
    }
}
