package com.bookingplatform.property.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingplatform.common.outbox.OutboxWriter;
import com.bookingplatform.events.EventEnvelope;
import com.bookingplatform.events.EventTypes;
import com.bookingplatform.events.Topics;
import com.bookingplatform.events.property.PropertyUnpublished;
import com.bookingplatform.property.application.usecase.PropertyAccess;
import com.bookingplatform.property.application.usecase.PropertyEvents;
import com.bookingplatform.property.application.usecase.PropertyModeration;
import com.bookingplatform.property.domain.exception.InvalidPropertyStateException;
import com.bookingplatform.property.domain.exception.PropertyNotFoundException;
import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.model.PropertyDetails;
import com.bookingplatform.property.domain.model.PropertyStatus;
import com.bookingplatform.property.domain.port.PropertyRepository;
import com.bookingplatform.property.domain.port.RoomTypeRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Kiểm tra admin khóa và mở khóa, kèm sự kiện với lý do ADMIN_SUSPENDED (4
 * test).
 */
public class PropertyModerationTest {
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final RoomTypeRepository roomTypes = mock(RoomTypeRepository.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final Clock clock = Clock.systemUTC();
    private final PropertyModeration moderation = new PropertyModeration(
            new PropertyAccess(properties), roomTypes, new PropertyEvents(outbox), clock);

    private Property stored() {
        PropertyDetails d = new PropertyDetails("Sunrise", "Mô tả", "Da Nang", "VN", "1 Duong A", 16.05, 108.2, "VND",
                Set.of());
        Property p = Property.create(UUID.randomUUID(), d, clock.instant());
        when(properties.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<EventEnvelope<?>> events() {
        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass((Class) EventEnvelope.class);
        verify(outbox, atLeast(0)).write(eq(Topics.PROPERTY_EVENTS), captor.capture());
        return captor.getAllValues();
    }

    /**
     * Admin đình chỉ khách sạn đang PUBLISHED: trạng thái chuyển sang SUSPENDED,
     * đồng thời ghi sự kiện PROPERTY_UNPUBLISHED với lý do ADMIN_SUSPENDED.
     */
    @Test
    void suspendingAPublishedPropertyWritesPropertyUnpublished() {
        Property p = stored();
        p.publish(clock.instant());

        moderation.suspend(p.getId());

        assertThat(p.getStatus()).isEqualTo(PropertyStatus.SUSPENDED);
        List<EventEnvelope<?>> events = events();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(EventTypes.PROPERTY_UNPUBLISHED);
        assertThat(((PropertyUnpublished) events.get(0).data()).reason()).isEqualTo("ADMIN_SUSPENDED");
    }

    /**
     * Không cho phép đình chỉ khách sạn đang DRAFT; không ghi sự kiện Outbox.
     */
    @Test
    void cannotSuspendADraft() {
        Property p = stored();

        assertThatThrownBy(() -> moderation.suspend(p.getId())).isInstanceOf(InvalidPropertyStateException.class);
        assertThat(events()).isEmpty();
    }

    /**
     * Admin khôi phục khách sạn đang SUSPENDED: trạng thái chuyển về DRAFT, không
     * ghi sự kiện Outbox.
     */
    @Test
    void reinstateReturnsToDraftWithoutEvent() {
        Property p = stored();
        p.publish(clock.instant());
        p.suspend(clock.instant());

        moderation.reinstate(p.getId());

        assertThat(p.getStatus()).isEqualTo(PropertyStatus.DRAFT);
        assertThat(events()).isEmpty();
    }

    /**
     * Khi Admin đình chỉ một khách sạn không tồn tại, hệ thống phải ném
     * PropertyNotFoundException.
     */
    @Test
    void unknownPropertyIsNotFound() {
        UUID id = UUID.randomUUID();
        when(properties.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moderation.suspend(id)).isInstanceOf(PropertyNotFoundException.class);
    }
}
