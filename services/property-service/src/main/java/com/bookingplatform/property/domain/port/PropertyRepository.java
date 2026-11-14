package com.bookingplatform.property.domain.port;

import com.bookingplatform.property.domain.model.Property;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository {

    Property insert(Property property);

    Optional<Property> findById(UUID id);

    /**
     * Khách sạn của một host, phân trang bằng cursor: lấy các id lớn hơn "after"
     * (null = từ đầu).
     */
    List<Property> findByHost(UUID hostId, UUID after, int limit);

    void delete(Property property);
}
