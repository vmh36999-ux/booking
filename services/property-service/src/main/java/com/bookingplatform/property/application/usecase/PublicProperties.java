package com.bookingplatform.property.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookingplatform.property.domain.exception.PropertyNotFoundException;
import com.bookingplatform.property.domain.model.Amenity;
import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.model.PropertyStatus;
import com.bookingplatform.property.domain.model.PropertyView;
import com.bookingplatform.property.domain.port.AmenityCatalog;
import com.bookingplatform.property.domain.port.PropertyRepository;
import com.bookingplatform.property.domain.port.RoomTypeRepository;

/** Những gì khách (không cần đăng nhập) được xem. */
@Service
public class PublicProperties {
    private final PropertyRepository properties;
    private final RoomTypeRepository roomTypes;
    private final AmenityCatalog amenities;

    public PublicProperties(PropertyRepository properties, RoomTypeRepository roomTypes, AmenityCatalog amenities) {
        this.properties = properties;
        this.roomTypes = roomTypes;
        this.amenities = amenities;
    }

    /**
     * Chỉ khách sạn PUBLISHED. Bản nháp hay bị khóa đều trả 404, không để lộ là có
     * tồn tại.
     */
    @Transactional(readOnly = true)
    public PropertyView getPublished(UUID propertyId) {
        Property property = properties.findById(propertyId)
                .filter(p -> p.getStatus() == PropertyStatus.PUBLISHED)
                .orElseThrow(PropertyNotFoundException::new);
        return new PropertyView(property, roomTypes.findByProperty(propertyId));
    }

    @Transactional(readOnly = true)
    public List<Amenity> amenities() {
        return amenities.findAll();
    }
}
