package com.bookingplatform.user.infrastructure.persistence;

import com.bookingplatform.user.domain.model.Profile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProfileRepository extends JpaRepository<Profile, UUID> {
}