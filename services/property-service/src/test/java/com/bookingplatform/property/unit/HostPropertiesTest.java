package com.bookingplatform.property.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingplatform.common.outbox.OutboxWriter;
import com.bookingplatform.events.EventEnvelope;
import com.bookingplatform.events.EventTypes;
import com.bookingplatform.events.Topics;
import com.bookingplatform.events.property.PropertyPublished;
import com.bookingplatform.events.property.PropertySnapshot;
import com.bookingplatform.events.property.PropertyUnpublished;
import com.bookingplatform.property.application.usecase.HostProperties;
import com.bookingplatform.property.application.usecase.PropertyAccess;
import com.bookingplatform.property.application.usecase.PropertyEvents;
import com.bookingplatform.property.domain.exception.InvalidPropertyStateException;
import com.bookingplatform.property.domain.exception.NotPropertyOwnerException;
import com.bookingplatform.property.domain.exception.PropertyNotFoundException;
import com.bookingplatform.property.domain.exception.UnknownAmenityException;
import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.model.PropertyDetails;
import com.bookingplatform.property.domain.model.PropertyStatus;
import com.bookingplatform.property.domain.model.PropertyView;
import com.bookingplatform.property.domain.model.RoomType;
import com.bookingplatform.property.domain.model.RoomTypeDetails;
import com.bookingplatform.property.domain.port.AmenityCatalog;
import com.bookingplatform.property.domain.port.PropertyRepository;
import com.bookingplatform.property.domain.port.RoomTypeRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class HostPropertiesTest {
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final RoomTypeRepository roomTypes = mock(RoomTypeRepository.class);
    private final AmenityCatalog amenities = mock(AmenityCatalog.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final Clock clock = Clock.systemUTC();
    private final HostProperties service = new HostProperties(
            properties, roomTypes, amenities, new PropertyAccess(properties), new PropertyEvents(outbox), clock);

    private final UUID host = UUID.randomUUID();
    private final UUID otherHost = UUID.randomUUID();

    HostPropertiesTest() {
        when(properties.insert(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));
        when(amenities.findUnknown(any())).thenReturn(Set.of());
    }

    private PropertyDetails details(String name) {
        return new PropertyDetails(name, "Mô tả", "Da Nang", "VN", "1 Võ Nguyên Giáp", 16.05, 108.2, "VND",
                Set.of("WIFI"));
    }

    private Property stored() {
        Property p = Property.create(host, details("Sunrise"), clock.instant());
        when(properties.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private RoomType room(UUID propertyId, int maxGuests, long price) {
        return RoomType.create(propertyId, new RoomTypeDetails("Room", maxGuests, "1 giường đôi", 5, price),
                clock.instant());
    }

    /**
     * Helper dùng để bắt (capture) lại tất cả các EventEnvelope được đẩy vào Outbox
     * trong quá trình chạy test, giúp phục vụ cho việc kiểm tra (assert) kết quả.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<EventEnvelope<?>> events() {
        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass((Class) EventEnvelope.class);
        verify(outbox, atLeast(0)).write(eq(Topics.PROPERTY_EVENTS), captor.capture());
        return captor.getAllValues();
    }

    /**
     * Host tạo khách sạn mới: khách sạn ở trạng thái DRAFT, thuộc Host đó và chưa
     * ghi sự kiện Outbox.
     */
    @Test
    void createsDraftOwnedByTheCallerAndWritesNoEvent() {
        PropertyView view = service.create(host, details("Sunrise"));

        assertThat(view.property().getStatus()).isEqualTo(PropertyStatus.DRAFT);
        assertThat(view.property().getHostId()).isEqualTo(host);
        assertThat(events()).isEmpty();
    }

    /**
     * Không cho tạo khách sạn nếu sử dụng tiện ích không tồn tại trong danh mục.
     */
    @Test
    void rejectsUnknownAmenity() {
        when(amenities.findUnknown(any())).thenReturn(Set.of("KHONG_CO"));

        assertThatThrownBy(() -> service.create(host, details("Sunrise"))).isInstanceOf(UnknownAmenityException.class);
        verify(properties, never()).insert(any());
    }

    /**
     * Host khác không được phép cập nhật khách sạn không thuộc quyền sở hữu của
     * mình.
     */
    @Test
    void anotherHostCannotUpdate() {
        Property p = stored();

        assertThatThrownBy(() -> service.update(otherHost, p.getId(), details("Hack")))
                .isInstanceOf(NotPropertyOwnerException.class);
        assertThat(p.getName()).isEqualTo("Sunrise");
        assertThat(events()).isEmpty();
    }

    /**
     * Khi tìm khách sạn bằng ID không tồn tại, hệ thống phải ném
     * PropertyNotFoundException.
     */
    @Test
    void unknownPropertyIsNotFound() {
        UUID id = UUID.randomUUID();
        when(properties.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(host, id)).isInstanceOf(PropertyNotFoundException.class);
    }

    /**
     * Cập nhật khách sạn DRAFT thành công nhưng không ghi sự kiện Outbox.
     */
    @Test
    void updatingADraftWritesNoEvent() {
        Property p = stored();

        service.update(host, p.getId(), details("Moon"));

        assertThat(p.getName()).isEqualTo("Moon");
        assertThat(events()).isEmpty();
    }

    /**
     * Cập nhật khách sạn PUBLISHED phải ghi sự kiện PROPERTY_UPDATED, kèm dữ liệu
     * phòng hiện tại.
     */
    @Test
    void updatingAPublishedPropertyWritesPropertyUpdated() {
        Property p = stored();
        p.publish(clock.instant());
        when(roomTypes.findByProperty(p.getId())).thenReturn(List.of(room(p.getId(), 2, 1_000_000L)));

        service.update(host, p.getId(), details("Moon"));

        List<EventEnvelope<?>> events = events();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(EventTypes.PROPERTY_UPDATED);
    }

    /**
     * Không cho publish khách sạn nếu chưa có loại phòng nào.
     */
    @Test
    void publishNeedsAtLeastOneRoomType() {
        Property p = stored();
        when(roomTypes.findByProperty(p.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.publish(host, p.getId())).isInstanceOf(InvalidPropertyStateException.class);
        assertThat(p.getStatus()).isEqualTo(PropertyStatus.DRAFT);
        assertThat(events()).isEmpty();
    }

    /**
     * Publish thành công phải ghi sự kiện PROPERTY_PUBLISHED với snapshot đầy đủ:
     * Host, tên, thành phố, tiện ích, loại phòng, giá thấp nhất, số khách tối đa và
     * tiền tệ.
     */
    @Test
    void publishWritesPropertyPublishedWithAFullSnapshot() {
        Property p = stored();
        when(roomTypes.findByProperty(p.getId()))
                .thenReturn(List.of(room(p.getId(), 2, 1_000_000L), room(p.getId(), 4, 2_000_000L)));

        service.publish(host, p.getId());

        List<EventEnvelope<?>> events = events();
        assertThat(events).hasSize(1);
        EventEnvelope<?> event = events.get(0);
        assertThat(event.type()).isEqualTo(EventTypes.PROPERTY_PUBLISHED);
        assertThat(event.aggregateId()).isEqualTo(p.getId().toString());

        PropertySnapshot snapshot = ((PropertyPublished) event.data()).property();
        assertThat(snapshot.hostId()).isEqualTo(host);
        assertThat(snapshot.name()).isEqualTo("Sunrise");
        assertThat(snapshot.city()).isEqualTo("Da Nang");
        assertThat(snapshot.amenities()).containsExactly("WIFI");
        assertThat(snapshot.roomTypes()).hasSize(2);
        assertThat(snapshot.priceFromMinor()).isEqualTo(1_000_000L);
        assertThat(snapshot.maxGuests()).isEqualTo(4);
        assertThat(snapshot.currency()).isEqualTo("VND");
    }

    /**
     * Host khác không được phép publish khách sạn không thuộc quyền sở hữu của
     * mình.
     */
    @Test
    void anotherHostCannotPublish() {
        Property p = stored();

        assertThatThrownBy(() -> service.publish(otherHost, p.getId())).isInstanceOf(NotPropertyOwnerException.class);
        assertThat(p.getStatus()).isEqualTo(PropertyStatus.DRAFT);
    }

    /**
     * Khi Host unpublish, trạng thái trở về DRAFT và ghi sự kiện
     * PROPERTY_UNPUBLISHED với lý do HOST_UNPUBLISHED.
     */
    @Test
    void unpublishWritesPropertyUnpublishedWithReason() {
        Property p = stored();
        p.publish(clock.instant());

        service.unpublish(host, p.getId());

        assertThat(p.getStatus()).isEqualTo(PropertyStatus.DRAFT);
        List<EventEnvelope<?>> events = events();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(EventTypes.PROPERTY_UNPUBLISHED);
        assertThat(((PropertyUnpublished) events.get(0).data()).reason()).isEqualTo("HOST_UNPUBLISHED");
    }

    /**
     * Cho phép xóa khách sạn DRAFT, nhưng không cho xóa khách sạn PUBLISHED.
     */
    @Test
    void draftCanBeDeletedButPublishedCannot() {
        Property draft = stored();
        service.delete(host, draft.getId());
        verify(properties).delete(draft);

        Property live = stored();
        live.publish(clock.instant());
        assertThatThrownBy(() -> service.delete(host, live.getId())).isInstanceOf(InvalidPropertyStateException.class);
        verify(properties, never()).delete(live);
    }

    /**
     * Host khác không được phép xóa khách sạn không thuộc quyền sở hữu của mình.
     */
    @Test
    void anotherHostCannotDelete() {
        Property p = stored();

        assertThatThrownBy(() -> service.delete(otherHost, p.getId())).isInstanceOf(NotPropertyOwnerException.class);
        verify(properties, never()).delete(any());
    }
}
