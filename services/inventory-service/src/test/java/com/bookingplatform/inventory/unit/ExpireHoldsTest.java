package com.bookingplatform.inventory.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingplatform.inventory.application.usecase.ExpireHolds;
import com.bookingplatform.inventory.application.usecase.HoldExpirer;
import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldCommand;
import com.bookingplatform.inventory.domain.port.HoldRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpireHoldsTest {

    private final HoldRepository holds = mock(HoldRepository.class);
    private final HoldExpirer expirer = mock(HoldExpirer.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-30T03:00:00Z"), ZoneOffset.UTC);
    /** Cỡ lượt là 2 để dễ thử việc chia lượt. */
    private final ExpireHolds service = new ExpireHolds(holds, expirer, clock, 2);

    private Hold hold() {
        HoldCommand command = new HoldCommand(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 22), 1);
        return Hold.create(command, clock.instant().minusSeconds(3600), Duration.ofMinutes(15));
    }

    @Test
    void nothingDueDoesNothing() {
        when(holds.findDueForExpiry(any(Instant.class), eq(2))).thenReturn(List.of());

        assertThat(service.execute()).isZero();
        verify(expirer, never()).expire(any());
    }

    @Test
    void expiresEveryDueHoldAcrossBatches() {
        Hold h1 = hold();
        Hold h2 = hold();
        Hold h3 = hold();
        when(holds.findDueForExpiry(any(Instant.class), eq(2))).thenReturn(List.of(h1, h2), List.of(h3));
        when(expirer.expire(any(UUID.class))).thenReturn(true);

        assertThat(service.execute()).isEqualTo(3);

        verify(expirer, times(3)).expire(any(UUID.class));
    }

    @Test
    void oneFailingHoldDoesNotStopTheOthers() {
        Hold broken = hold();
        Hold fine = hold();
        when(holds.findDueForExpiry(any(Instant.class), eq(2))).thenReturn(List.of(broken, fine), List.of());
        when(expirer.expire(broken.id())).thenThrow(new IllegalStateException("tồn kho lệch"));
        when(expirer.expire(fine.id())).thenReturn(true);

        assertThat(service.execute()).isEqualTo(1);

        verify(expirer).expire(fine.id());
    }

    @Test
    void stopsWhenABatchMakesNoProgressInsteadOfLoopingForever() {
        Hold h1 = hold();
        Hold h2 = hold();
        when(holds.findDueForExpiry(any(Instant.class), eq(2))).thenReturn(List.of(h1, h2));
        when(expirer.expire(any(UUID.class))).thenReturn(false);

        assertThat(service.execute()).isZero();

        verify(holds, times(1)).findDueForExpiry(any(Instant.class), eq(2));
    }
}
