package com.bookingplatform.property.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookingplatform.property.domain.exception.InvalidPropertyStateException;
import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.model.PropertyDetails;
import com.bookingplatform.property.domain.model.PropertyStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class PropertyLifecycleTest {
    private final Instant now = Instant.parse("2026-11-16T00:00:00Z");
    private final UUID host = UUID.randomUUID();

    private PropertyDetails details(String name, Set<String> amenities) {
        return new PropertyDetails(name, "Mô tả", "Da Nang", "VN", "1 Võ Nguyên Giáp", 16.05, 108.2, "VND", amenities);
    }

    private Property draft() {
        return Property.create(host, details("Sunrise", Set.of("WIFI")), now);
    }

    // Khách sạn mới tạo phải có trạng thái DRAFT, thuộc sở hữu của Host tạo ra và
    // chưa có thời điểm publish.
    @Test
    void newPropertyIsDraftAndOwnedByItsHost() {
        Property p = draft();

        assertThat(p.getStatus()).isEqualTo(PropertyStatus.DRAFT);
        assertThat(p.isOwnedBy(host)).isTrue();
        assertThat(p.isOwnedBy(UUID.randomUUID())).isFalse();
        assertThat(p.getPublishedAt()).isNull();
    }

    @Test
    void publishMovesDraftToPublishedAndStampsTheTime() {
        Property p = draft();
        p.publish(now.plusSeconds(60));

        assertThat(p.getStatus()).isEqualTo(PropertyStatus.PUBLISHED);
        assertThat(p.getPublishedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void cannotPublishTwice() {
        Property p = draft();
        p.publish(now);

        assertThatThrownBy(() -> p.publish(now)).isInstanceOf(InvalidPropertyStateException.class);
    }

    @Test
    void unpublishReturnsToDraft() {
        Property p = draft();
        p.publish(now);
        p.unpublish(now);

        assertThat(p.getStatus()).isEqualTo(PropertyStatus.DRAFT);
    }

    @Test
    void cannotUnpublishADraft() {
        assertThatThrownBy(() -> draft().unpublish(now)).isInstanceOf(InvalidPropertyStateException.class);
    }

    @Test
    void reinstateOnlyWorksOnSuspended() {
        Property p = draft();
        assertThatThrownBy(() -> p.reinstate(now)).isInstanceOf(InvalidPropertyStateException.class);

        p.publish(now);
        assertThatThrownBy(() -> p.reinstate(now)).isInstanceOf(InvalidPropertyStateException.class);
    }

    @Test
    void suspendedPropertyCannotBeRepublishedUntilReinstated() {
        Property p = draft();
        p.publish(now);
        p.suspend(now);

        assertThatThrownBy(() -> p.publish(now)).isInstanceOf(InvalidPropertyStateException.class);

        p.reinstate(now);
        assertThat(p.getStatus()).isEqualTo(PropertyStatus.DRAFT);
        p.publish(now);
        assertThat(p.getStatus()).isEqualTo(PropertyStatus.PUBLISHED);
    }

    @Test
    void updateReplacesDetailsAndAmenities() {
        Property p = draft();
        p.update(details("Moon", Set.of("POOL", "SPA")), now.plusSeconds(5));

        assertThat(p.getName()).isEqualTo("Moon");
        assertThat(p.getAmenityCodes()).containsExactly("POOL", "SPA");
        assertThat(p.getUpdatedAt()).isEqualTo(now.plusSeconds(5));
    }
}
