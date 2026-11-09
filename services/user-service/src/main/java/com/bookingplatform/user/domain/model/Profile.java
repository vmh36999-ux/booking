package com.bookingplatform.user.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Hồ sơ của một người dùng. user_id chính là "sub" trong JWT do auth-service
 * phát.
 */
@Entity
@Table(name = "profile")
public class Profile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "country")
    private String country;

    @Column(name = "language")
    private String language;

    @Column(name = "currency")
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Profile() {
    }

    /** Hồ sơ rỗng, tạo tự động khi nhận sự kiện UserRegistered. */
    public static Profile empty(UUID userId, Instant now) {
        Profile p = new Profile();
        p.userId = userId;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public void update(String fullName, String phone, String country, String language, String currency, Instant now) {
        this.fullName = fullName;
        this.phone = phone;
        this.country = country;
        this.language = language;
        this.currency = currency;
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getCountry() {
        return country;
    }

    public String getLanguage() {
        return language;
    }

    public String getCurrency() {
        return currency;
    }
}