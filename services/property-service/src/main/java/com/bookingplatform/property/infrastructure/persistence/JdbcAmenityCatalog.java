package com.bookingplatform.property.infrastructure.persistence;

import com.bookingplatform.property.domain.model.Amenity;
import com.bookingplatform.property.domain.port.AmenityCatalog;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Đọc danh mục tiện nghi (15 dòng) từ bảng amenity. */
@Repository
public class JdbcAmenityCatalog implements AmenityCatalog {

    private final JdbcTemplate jdbc;

    public JdbcAmenityCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> findUnknown(Collection<String> codes) {
        if (codes.isEmpty()) {
            return Set.of();
        }
        Set<String> known = new HashSet<>(jdbc.queryForList("SELECT code FROM amenity", String.class));
        Set<String> unknown = new TreeSet<>(codes);
        unknown.removeAll(known);
        return unknown;
    }

    @Override
    public List<Amenity> findAll() {
        return jdbc.query("SELECT code, name FROM amenity ORDER BY code",
                (rs, i) -> new Amenity(rs.getString("code"), rs.getString("name")));
    }
}
