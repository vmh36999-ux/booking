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
import com.bookingplatform.events.property.RoomTypeCreated;
import com.bookingplatform.property.application.usecase.PropertyAccess;
import com.bookingplatform.property.application.usecase.PropertyEvents;
import com.bookingplatform.property.application.usecase.RoomTypes;
import com.bookingplatform.property.domain.exception.InvalidPropertyStateException;
import com.bookingplatform.property.domain.exception.NotPropertyOwnerException;
import com.bookingplatform.property.domain.exception.RoomTypeNotFoundException;
import com.bookingplatform.property.domain.model.Property;
import com.bookingplatform.property.domain.model.PropertyDetails;
import com.bookingplatform.property.domain.model.RoomType;
import com.bookingplatform.property.domain.model.RoomTypeDetails;
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
 * Kiểm tra loại phòng: phát RoomTypeCreated, không xóa được loại phòng cuối của
 * khách sạn đang đăng, không sửa nhầm loại phòng của khách sạn khác (6 test).
 * RoomTypesTest
 */
public class RoomTypesTest {
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final RoomTypeRepository roomTypes = mock(RoomTypeRepository.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final Clock clock = Clock.systemUTC();
    private final RoomTypes service = new RoomTypes(
            roomTypes, new PropertyAccess(properties), new PropertyEvents(outbox), clock);

    private final UUID host = UUID.randomUUID();
    private final UUID otherHost = UUID.randomUUID();

    RoomTypesTest() {
        when(roomTypes.insert(any(RoomType.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Property stored() {
        PropertyDetails d = new PropertyDetails("Sunrise", "Mô tả", "Da Nang", "VN", "1 Duong A", 16.05, 108.2, "VND",
                Set.of());
        Property p = Property.create(host, d, clock.instant());
        when(properties.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private RoomTypeDetails details() {
        return new RoomTypeDetails("Deluxe", 2, "1 giường đôi", 10, 1_200_000L);
    }

    private RoomType storedRoom(UUID propertyId) {
        RoomType r = RoomType.create(propertyId, details(), clock.instant());
        when(roomTypes.findById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<EventEnvelope<?>> events() {
        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass((Class) EventEnvelope.class);
        verify(outbox, atLeast(0)).write(eq(Topics.PROPERTY_EVENTS), captor.capture());
        return captor.getAllValues();
    }

    /**
     * Thêm loại phòng vào khách sạn DRAFT: ghi sự kiện ROOM_TYPE_CREATED, không ghi
     * PROPERTY_UPDATED. Kiểm tra thêm thông tin trong event như Property ID, Room
     * Type ID, số phòng và tiền tệ.
     */
    @Test
    void addingToADraftWritesOnlyRoomTypeCreated() {
        Property p = stored();

        RoomType created = service.add(host, p.getId(), details());

        List<EventEnvelope<?>> events = events();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(EventTypes.ROOM_TYPE_CREATED);
        RoomTypeCreated payload = (RoomTypeCreated) events.get(0).data();
        assertThat(payload.propertyId()).isEqualTo(p.getId());
        assertThat(payload.roomTypeId()).isEqualTo(created.getId());
        assertThat(payload.totalRooms()).isEqualTo(10);
        assertThat(payload.currency()).isEqualTo("VND");
    }

    /**
     * Thêm loại phòng vào khách sạn PUBLISHED: phải ghi cả ROOM_TYPE_CREATED và
     * PROPERTY_UPDATED để cập nhật thông tin khách sạn cho các service khác.
     */
    @Test
    void addingToAPublishedPropertyAlsoWritesPropertyUpdated() {
        Property p = stored();
        p.publish(clock.instant());
        when(roomTypes.findByProperty(p.getId())).thenReturn(List.of());

        service.add(host, p.getId(), details());

        List<String> types = events().stream().map(EventEnvelope::type).toList();
        assertThat(types).containsExactly(EventTypes.ROOM_TYPE_CREATED, EventTypes.PROPERTY_UPDATED);
    }

    /**
     * Host khác không được thêm loại phòng vào khách sạn không thuộc quyền sở hữu
     * của mình. Không INSERT và không ghi Outbox.
     */
    @Test
    void anotherHostCannotAddRoomTypes() {
        Property p = stored();

        assertThatThrownBy(() -> service.add(otherHost, p.getId(), details()))
                .isInstanceOf(NotPropertyOwnerException.class);
        verify(roomTypes, never()).insert(any());
        assertThat(events()).isEmpty();
    }

    /**
     * Không cho xóa loại phòng cuối cùng của khách sạn đang PUBLISHED.
     */
    @Test
    void cannotDeleteTheLastRoomTypeOfAPublishedProperty() {
        Property p = stored();
        p.publish(clock.instant());
        RoomType room = storedRoom(p.getId());
        when(roomTypes.countByProperty(p.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(host, p.getId(), room.getId()))
                .isInstanceOf(InvalidPropertyStateException.class);
        verify(roomTypes, never()).delete(any());
    }

    @Test
    void canDeleteARoomTypeFromADraft() {
        Property p = stored();
        RoomType room = storedRoom(p.getId());

        service.delete(host, p.getId(), room.getId());

        verify(roomTypes).delete(room);
        assertThat(events()).isEmpty();
    }

    @Test
    void roomTypeOfAnotherPropertyIsNotFound() {
        Property mine = stored();
        RoomType foreign = storedRoom(UUID.randomUUID()); // thuộc khách sạn khác

        assertThatThrownBy(() -> service.update(host, mine.getId(), foreign.getId(), details()))
                .isInstanceOf(RoomTypeNotFoundException.class);
    }
}
