package com.bookingplatform.inventory.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookingplatform.inventory.application.usecase.HoldExpirer;
import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldCommand;
import com.bookingplatform.inventory.domain.model.HoldStatus;
import com.bookingplatform.inventory.domain.port.HoldRepository;
import com.bookingplatform.inventory.domain.port.NightInventory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HoldExpirerTest {

    private final HoldRepository holds = mock(HoldRepository.class);
    private final NightInventory nights = mock(NightInventory.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-30T03:00:00Z"), ZoneOffset.UTC);
    private final HoldExpirer expirer = new HoldExpirer(holds, nights, clock);

    private final Instant now = clock.instant();
    private final HoldCommand command = new HoldCommand(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 23), 2);

    /** Hold tạo cách đây một tiếng, TTL 15 phút: đã quá hạn. */
    private Hold overdue() {
        return Hold.create(command, now.minusSeconds(3600), Duration.ofMinutes(15));
    }

    private void stored(Hold hold) {
        when(holds.findById(hold.id())).thenReturn(Optional.of(hold));
    }

    @Test
    void expiresAnOverdueHoldAndReturnsItsRooms() {
        Hold hold = overdue();
        stored(hold);
        when(holds.transition(hold.id(), HoldStatus.HELD, HoldStatus.EXPIRED, now)).thenReturn(1);
        when(nights.release(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), 2)).thenReturn(3);

        assertThat(expirer.expire(hold.id())).isTrue();

        verify(nights).release(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), 2);
    }

    @Test
    void aHoldNotYetDueIsLeftAlone() {
        Hold fresh = Hold.create(command, now, Duration.ofMinutes(15));
        stored(fresh);

        assertThat(expirer.expire(fresh.id())).isFalse();

        verify(holds, never()).transition(any(), any(), any(), any());
        verifyNoInteractions(nights);
    }

    @Test
    void aHoldThatIsNotHeldIsIgnoredEvenIfItsTimeHasPassed() {
        for (HoldStatus status : new HoldStatus[] { HoldStatus.CONFIRMED, HoldStatus.RELEASED, HoldStatus.EXPIRED }) {
            Hold hold = overdue().withStatus(status, now);
            stored(hold);

            assertThat(expirer.expire(hold.id())).isFalse();
        }
        verifyNoInteractions(nights);
    }

    @Test
    void losingTheRaceDoesNotReleaseTheRoomsTwice() {
        Hold hold = overdue();
        stored(hold);
        when(holds.transition(hold.id(), HoldStatus.HELD, HoldStatus.EXPIRED, now)).thenReturn(0);

        assertThat(expirer.expire(hold.id())).isFalse();

        verifyNoInteractions(nights);
    }

    @Test
    void anUnknownHoldIsIgnored() {
        UUID id = UUID.randomUUID();
        when(holds.findById(id)).thenReturn(Optional.empty());

        assertThat(expirer.expire(id)).isFalse();
        verifyNoInteractions(nights);
    }

    @Test
    void inconsistentInventoryAbortsSoNothingIsHalfDone() {
        Hold hold = overdue();
        stored(hold);
        when(holds.transition(hold.id(), HoldStatus.HELD, HoldStatus.EXPIRED, now)).thenReturn(1);
        when(nights.release(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), 2)).thenReturn(1); // thiếu 2 đêm

        assertThatThrownBy(() -> expirer.expire(hold.id())).isInstanceOf(IllegalStateException.class);
    }
}
