package com.bookingplatform.property.domain.port;

import com.bookingplatform.property.domain.model.Amenity;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Danh mục tiện nghi hợp lệ. */
public interface AmenityCatalog {

    /** Trả về những mã KHÔNG có trong danh mục (rỗng nếu tất cả đều hợp lệ). */
    Set<String> findUnknown(Collection<String> codes);

    List<Amenity> findAll();
}
