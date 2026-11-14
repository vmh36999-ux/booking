package com.bookingplatform.property.domain.model;

import com.bookingplatform.property.domain.exception.InvalidPropertyStateException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Khách sạn. Vòng đời:
 *
 * DRAFT --publish--> PUBLISHED --suspend (admin)--> SUSPENDED
 * ^ | |
 * +----unpublish-------+ |
 * +----------------reinstate (admin)------------------+
 */
@Entity
@Table(name = "property")
public class Property {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "country", nullable = false)
    private String country;

    @Column(name = "address")
    private String address;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PropertyStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "property_amenity", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "amenity_code", nullable = false)
    private Set<String> amenities = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected Property() {
    }

    public static Property create(UUID hostId, PropertyDetails details, Instant now) {
        Property p = new Property();
        p.id = UUID.randomUUID();
        p.hostId = hostId;
        p.status = PropertyStatus.DRAFT;
        p.createdAt = now;
        p.updatedAt = now;
        p.apply(details);
        return p;
    }

    public void update(PropertyDetails details, Instant now) {
        apply(details);
        this.updatedAt = now;
    }

    private void apply(PropertyDetails d) {
        this.name = d.name();
        this.description = d.description();
        this.city = d.city();
        this.country = d.country();
        this.address = d.address();
        this.latitude = d.latitude();
        this.longitude = d.longitude();
        this.currency = d.currency();
        this.amenities.clear();
        this.amenities.addAll(d.amenities());
    }

    // ------------------------------------------------------------ chuyển trạng
    // thái

    public void publish(Instant now) {
        requireStatus(PropertyStatus.DRAFT, "Chỉ khách sạn ở trạng thái DRAFT mới đăng được");
        this.status = PropertyStatus.PUBLISHED;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void unpublish(Instant now) {
        requireStatus(PropertyStatus.PUBLISHED, "Chỉ khách sạn đang đăng (PUBLISHED) mới gỡ đăng được");
        this.status = PropertyStatus.DRAFT;
        this.updatedAt = now;
    }

    public void suspend(Instant now) {
        requireStatus(PropertyStatus.PUBLISHED, "Chỉ khách sạn đang đăng (PUBLISHED) mới khóa được");
        this.status = PropertyStatus.SUSPENDED;
        this.updatedAt = now;
    }

    public void reinstate(Instant now) {
        requireStatus(PropertyStatus.SUSPENDED, "Chỉ khách sạn đang bị khóa (SUSPENDED) mới mở khóa được");
        this.status = PropertyStatus.DRAFT;
        this.updatedAt = now;
    }

    private void requireStatus(PropertyStatus expected, String message) {
        if (status != expected) {
            throw new InvalidPropertyStateException(message + " (hiện tại: " + status + ")");
        }
    }

    public boolean isOwnedBy(UUID userId) {
        return hostId.equals(userId);
    }

    // ------------------------------------------------------------ getter

    public UUID getId() {
        return id;
    }

    public UUID getHostId() {
        return hostId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public String getAddress() {
        return address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getCurrency() {
        return currency;
    }

    public PropertyStatus getStatus() {
        return status;
    }

    /** Mã tiện nghi, sắp xếp sẵn để kết quả ổn định. */
    public List<String> getAmenityCodes() {
        return amenities.stream().sorted().toList();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
