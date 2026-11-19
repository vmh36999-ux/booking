package com.bookingplatform.inventory.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookingplatform.inventory.application.usecase.ReleaseHold;
import com.bookingplatform.inventory.domain.exception.HoldConflictException;
import com.bookingplatform.inventory.domain.exception.HoldNotFoundException;
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

/**
 * Kiểm tra nhả: thành công, idempotent, từ chối hold đã xác nhận, thua cuộc đua
 * (6 test).
 * ReleaseHoldTest
 */
class ReleaseHoldTest {

    private final HoldRepository holds = mock(HoldRepository.class);
    private final NightInventory nights = mock(NightInventory.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private final ReleaseHold service = new ReleaseHold(holds, nights, clock);

    private final Instant now = clock.instant();
    private final HoldCommand command = new HoldCommand(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 23), 2);

    private Hold active() {
        return Hold.create(command, now, Duration.ofMinutes(15));
    }

    private void stored(Hold hold) {
        when(holds.findById(hold.id())).thenReturn(Optional.of(hold));
    }

    @Test
    void releasesAnActiveHoldAndReturnsTheRooms() {
        Hold hold = active();
        stored(hold);
        when(holds.transition(hold.id(), HoldStatus.HELD, HoldStatus.RELEASED, now)).thenReturn(1);
        when(nights.release(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), 2)).thenReturn(3);

        Hold result = service.execute(hold.id());

        assertThat(result.status()).isEqualTo(HoldStatus.RELEASED);
        verify(nights).release(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), 2);
    }

    @Test
    void releasedAndExpiredHoldsAreReturnedUntouched() {
        for (HoldStatus status : new HoldStatus[] { HoldStatus.RELEASED, HoldStatus.EXPIRED }) {
            Hold hold = active().withStatus(status, now);
            stored(hold);

            assertThat(service.execute(hold.id())).isEqualTo(hold);
        }
        verifyNoInteractions(nights);
    }

    @Test
    void aConfirmedHoldCannotBeReleasedThisWay() {
        Hold confirmed = active().withStatus(HoldStatus.CONFIRMED, now);
        stored(confirmed);

        assertThatThrownBy(() -> service.execute(confirmed.id()))
                .isInstanceOfSatisfying(HoldConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HOLD_ALREADY_CONFIRMED"));
        verifyNoInteractions(nights);
    }

    @Test
    void losingTheRaceToAConfirmationIsAConflict() {
        Hold hold = active();
        when(holds.findById(hold.id())).thenReturn(Optional.of(hold),
                Optional.of(hold.withStatus(HoldStatus.CONFIRMED, now)));
        when(holds.transition(hold.id(), HoldStatus.HELD, HoldStatus.RELEASED, now)).thenReturn(0);

        assertThatThrownBy(() -> service.execute(hold.id()))
                .isInstanceOfSatisfying(HoldConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HOLD_ALREADY_CONFIRMED"));
        verifyNoInteractions(nights);
    }

    @Test
    void losingTheRaceToAnotherReleaseJustReturnsIt() {
        Hold hold = active();
        when(holds.findById(hold.id())).thenReturn(Optional.of(hold),
                Optional.of(hold.withStatus(HoldStatus.RELEASED, now)));
        when(holds.transition(hold.id(), HoldStatus.HELD, HoldStatus.RELEASED, now)).thenReturn(0);

        assertThat(service.execute(hold.id()).status()).isEqualTo(HoldStatus.RELEASED);
        verifyNoInteractions(nights);
    }

    @Test
    void unknownHoldIsNotFound() {
        UUID id = UUID.randomUUID();
        when(holds.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(id)).isInstanceOf(HoldNotFoundException.class);
    }
}
