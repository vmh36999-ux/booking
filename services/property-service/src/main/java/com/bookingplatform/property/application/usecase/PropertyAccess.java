package com.bookingplatform.property.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.bookingplatform.property.domain.exception.NotPropertyOwnerException;
import com.bookingplatform.property.domain.exception.PropertyNotFoundException;
import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.port.PropertyRepository;

/**
 * Tải khách sạn và KIỂM TRA QUYỀN SỞ HỮU. Mọi thao tác ghi của host đều đi qua
 * đây.
 */
@Component
public class PropertyAccess {
    private final PropertyRepository properties;

    public PropertyAccess(PropertyRepository properties) {
        this.properties = properties;
    }

    /** 404 nếu không có; 403 nếu có nhưng không phải của host này. */
    public Property loadOwned(UUID hostId, UUID propertyId) {
        Property property = loadAny(propertyId);
        if (!property.isOwnedBy(hostId)) {
            throw new NotPropertyOwnerException();
        }
        return property;
    }

    public Property loadAny(UUID propertyId) {
        return properties.findById(propertyId).orElseThrow(PropertyNotFoundException::new);
    }
}
