package com.bookingplatform.inventory.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingplatform.inventory.application.usecase.AdjustHostInventory;
import com.bookingplatform.inventory.domain.exception.BelowCommitmentException;
import com.bookingplatform.inventory.domain.exception.InvalidStayException;
import com.bookingplatform.inventory.domain.exception.NoInventoryException;
import com.bookingplatform.inventory.domain.exception.NotRoomTypeOwnerException;
import com.bookingplatform.inventory.domain.exception.RoomTypeNotFoundException;
import com.bookingplatform.inventory.domain.port.NightInventory;
import com.bookingplatform.inventory.domain.port.RoomTypeOwners;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdjustHostInventoryTest {

    private final RoomTypeOwners owners = mock(RoomTypeOwners.class);
    private final NightInventory nights = mock(NightInventory.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-30T03:00:00Z"), ZoneOffset.UTC);
    private final AdjustHostInventory service = new AdjustHostInventory(owners, nights, clock);

    private final LocalDate today = LocalDate.of(2026, 11, 30);
    private final LocalDate from = today.plusDays(10);
    private final LocalDate to = from.plusDays(7);
    private final UUID host = UUID.randomUUID();
    private final UUID roomType = UUID.randomUUID();

    private void ownedBy(UUID owner) {
        when(owners.findHostId(roomType)).thenReturn(Optional.of(owner));
    }

    @Test
    void updatesTheRangeAndReturnsTheNumberOfNights() {
        ownedBy(host);
        when(nights.setTotal(roomType, from, to, 8)).thenReturn(7);

        assertThat(service.execute(host, roomType, from, to, 8)).isEqualTo(7);
    }

    @Test
    void unknownRoomTypeIsNotFound() {
        when(owners.findHostId(roomType)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(host, roomType, from, to, 8))
                .isInstanceOf(RoomTypeNotFoundException.class);
        verify(nights, never()).setTotal(any(), any(), any(), anyInt());
    }

    @Test
    void anotherHostIsForbiddenBeforeAnythingChanges() {
        ownedBy(UUID.randomUUID());

        assertThatThrownBy(() -> service.execute(host, roomType, from, to, 8))
                .isInstanceOf(NotRoomTypeOwnerException.class);
        verify(nights, never()).setTotal(any(), any(), any(), anyInt());
    }

    @Test
    void loweringBelowWhatIsCommittedListsTheBlockingNights() {
        ownedBy(host);
        when(nights.setTotal(roomType, from, to, 2)).thenReturn(5); // chỉ 5 trong 7 đêm đổi được
        when(nights.countNights(roomType, from, to)).thenReturn(7); // đủ 7 đêm tồn tại
        when(nights.findNightsAboveTotal(roomType, from, to, 2))
                .thenReturn(List.of(from.plusDays(2), from.plusDays(3)));

        assertThatThrownBy(() -> service.execute(host, roomType, from, to, 2))
                .isInstanceOfSatisfying(BelowCommitmentException.class,
                        e -> assertThat(e.getNights()).containsExactly(from.plusDays(2), from.plusDays(3)));
    }

    @Test
    void nightsThatDoNotExistAreNoInventoryNotACommitmentProblem() {
        ownedBy(host);
        when(nights.setTotal(roomType, from, to, 8)).thenReturn(5);
        when(nights.countNights(roomType, from, to)).thenReturn(5);

        assertThatThrownBy(() -> service.execute(host, roomType, from, to, 8)).isInstanceOf(NoInventoryException.class);
    }

    @Test
    void rejectsRangesThatAreEmptyTooLongOrInThePast() {
        ownedBy(host);

        assertThatThrownBy(() -> service.execute(host, roomType, from, from, 8))
                .isInstanceOf(InvalidStayException.class);
        assertThatThrownBy(() -> service.execute(host, roomType, from, from.plusDays(367), 8))
                .isInstanceOf(InvalidStayException.class);
        assertThatThrownBy(() -> service.execute(host, roomType, today.minusDays(1), today.plusDays(2), 8))
                .isInstanceOf(InvalidStayException.class);
        verify(nights, never()).setTotal(any(), any(), any(), anyInt());
    }

    @Test
    void allowsStartingToday() {
        ownedBy(host);
        when(nights.setTotal(roomType, today, today.plusDays(2), 4)).thenReturn(2);

        assertThat(service.execute(host, roomType, today, today.plusDays(2), 4)).isEqualTo(2);
    }

    @Test
    void zeroRoomsIsAllowedToCloseTheNights() {
        ownedBy(host);
        when(nights.setTotal(roomType, from, to, 0)).thenReturn(7);

        assertThat(service.execute(host, roomType, from, to, 0)).isEqualTo(7);
    }
}
