package com.bookingplatform.inventory.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookingplatform.inventory.application.usecase.ConfirmHold;
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
 * Kiểm tra xác nhận: thành công, idempotent, từ chối hold đã nhả hoặc hết hạn,
 * hold quá hạn thì trả phòng rồi báo lỗi, thua cuộc đua, tồn kho không nhất
 * quán (9 test).
 * ConfirmHoldTest
 */
class ConfirmHoldTest {

    private final HoldRepository holds = mock(HoldRepository.class);
    private final NightInventory nights = mock(NightInventory.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private final ConfirmHold service = new ConfirmHold(holds, nights, clock);

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
    void confirmsAnActiveHoldAndMovesRoomsFromHeldToBooked() {
        Hold hold = active();
        stored(hold);
        when(holds.transition(hold.id(), HoldStatus.HELD, HoldStatus.CONFIRMED, now)).thenReturn(1);
        when(nights.confirm(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), 2)).thenReturn(3);

        Hold result = service.execute(hold.id());

        assertThat(result.status()).isEqualTo(HoldStatus.CONFIRMED);
        verify(nights).confirm(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), 2);
    }

    @Test
    void confirmingAnAlreadyConfirmedHoldChangesNothing() {
        Hold confirmed = active().withStatus(HoldStatus.CONFIRMED, now);
        stored(confirmed);

        Hold result = service.execute(confirmed.id());

        assertThat(result).isEqualTo(confirmed);
        verify(holds, never()).transition(any(), any(), any(), any());
        verifyNoInteractions(nights);
    }

    @Test
    void releasedAndExpiredHoldsCannotBeConfirmed() {
        for (HoldStatus status : new HoldStatus[] { HoldStatus.RELEASED, HoldStatus.EXPIRED }) {
            Hold hold = active().withStatus(status, now);
            stored(hold);

            assertThatThrownBy(() -> service.execute(hold.id()))
                    .isInstanceOfSatisfying(HoldConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("HOLD_NOT_ACTIVE"));
        }
        verifyNoInteractions(nights);
    }

    @Test
    void aHoldPastItsExpiryIsExpiredAndItsRoomsAreReturned() {
        Hold old = Hold.create(command, now.minusSeconds(3600), Duration.ofMinutes(15));
        stored(old);
        when(holds.transition(old.id(), HoldStatus.HELD, HoldStatus.EXPIRED, now)).thenReturn(1);

        assertThatThrownBy(() -> service.execute(old.id()))
                .isInstanceOfSatisfying(HoldConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HOLD_EXPIRED"));
        verify(nights).release(old.roomTypeId(), old.checkIn(), old.checkOut(), 2);
        verify(nights, never()).confirm(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void anExpiredHoldAlreadyHandledElsewhereDoesNotReleaseTwice() {
        Hold old = Hold.create(command, now.minusSeconds(3600), Duration.ofMinutes(15));
        stored(old);
        when(holds.transition(old.id(), HoldStatus.HELD, HoldStatus.EXPIRED, now)).thenReturn(0);

        assertThatThrownBy(() -> service.execute(old.id())).isInstanceOf(HoldConflictException.class);
        verifyNoInteractions(nights);
    }

    @Test
    void losingTheRaceReturnsWhatTheWinnerDid() {
        Hold hold = active();
        when(holds.findById(hold.id())).thenReturn(Optional.of(hold),
                Optional.of(hold.withStatus(HoldStatus.CONFIRMED, now)));
        when(holds.transition(hold.id(), HoldStatus.HELD, HoldStatus.CONFIRMED, now)).thenReturn(0);

        Hold result = service.execute(hold.id());

        assertThat(result.status()).isEqualTo(HoldStatus.CONFIRMED);
        verifyNoInteractions(nights);
    }

    @Test
    void losingTheRaceToARelease() {
        Hold hold = active();
        when(holds.findById(hold.id())).thenReturn(Optional.of(hold),
                Optional.of(hold.withStatus(HoldStatus.RELEASED, now)));
        when(holds.transition(hold.id(), HoldStatus.HELD, HoldStatus.CONFIRMED, now)).thenReturn(0);

        assertThatThrownBy(() -> service.execute(hold.id()))
                .isInstanceOfSatisfying(HoldConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HOLD_NOT_ACTIVE"));
    }

    @Test
    void unknownHoldIsNotFound() {
        UUID id = UUID.randomUUID();
        when(holds.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(id)).isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void inconsistentInventoryAbortsTheConfirmation() {
        Hold hold = active();
        stored(hold);
        when(holds.transition(hold.id(), HoldStatus.HELD, HoldStatus.CONFIRMED, now)).thenReturn(1);
        when(nights.confirm(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), 2)).thenReturn(1); // thiếu 2 đêm

        assertThatThrownBy(() -> service.execute(hold.id())).isInstanceOf(IllegalStateException.class);
    }
}
