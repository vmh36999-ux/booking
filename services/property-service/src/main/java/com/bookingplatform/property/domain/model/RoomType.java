package com.bookingplatform.property.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Một loại phòng của khách sạn (ví dụ "Deluxe Twin", có 10 phòng). */
@Entity
@Table(name = "room_type")
public class RoomType {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "max_guests", nullable = false)
    private int maxGuests;

    @Column(name = "bed_config")
    private String bedConfig;

    @Column(name = "total_rooms", nullable = false)
    private int totalRooms;

    @Column(name = "base_price_minor", nullable = false)
    private long basePriceMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoomType() {
    }

    public static RoomType create(UUID propertyId, RoomTypeDetails d, Instant now) {
        RoomType r = new RoomType();
        r.id = UUID.randomUUID();
        r.propertyId = propertyId;
        r.createdAt = now;
        r.update(d);
        return r;
    }

    public void update(RoomTypeDetails d) {
        this.name = d.name();
        this.maxGuests = d.maxGuests();
        this.bedConfig = d.bedConfig();
        this.totalRooms = d.totalRooms();
        this.basePriceMinor = d.basePriceMinor();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public String getName() {
        return name;
    }

    public int getMaxGuests() {
        return maxGuests;
    }

    public String getBedConfig() {
        return bedConfig;
    }

    public int getTotalRooms() {
        return totalRooms;
    }

    public long getBasePriceMinor() {
        return basePriceMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
