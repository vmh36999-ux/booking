package com.bookingplatform.inventory.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingplatform.inventory.application.usecase.HoldRooms;
import com.bookingplatform.inventory.config.InventoryProperties;
import com.bookingplatform.inventory.domain.exception.HoldConflictException;
import com.bookingplatform.inventory.domain.exception.InvalidStayException;
import com.bookingplatform.inventory.domain.exception.NoInventoryException;
import com.bookingplatform.inventory.domain.exception.SoldOutException;
import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldCommand;
import com.bookingplatform.inventory.domain.model.HoldResult;
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
 * Kiểm tra giữ chỗ với các cổng giả: thành công, SOLD_OUT, NO_INVENTORY, gọi
 * lại cùng đơn (không giữ thêm), gọi lại khác nội dung, thua cuộc đua chèn,
 * ngày không hợp lệ (10 test).
 * HoldRoomsTest
 */
class HoldRoomsTest {

    private final HoldRepository holds = mock(HoldRepository.class);
    private final NightInventory nights = mock(NightInventory.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private final InventoryProperties properties = new InventoryProperties(Duration.ofMinutes(15), 365, 30);
    private final HoldRooms service = new HoldRooms(holds, nights, properties, clock);

    private final UUID roomType = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 23);

    HoldRoomsTest() {
        when(holds.insertIfAbsent(any(Hold.class))).thenAnswer(inv -> Optional.of(inv.getArgument(0)));
    }

    private HoldCommand command(LocalDate in, LocalDate out, int qty) {
        return new HoldCommand(UUID.randomUUID(), roomType, in, out, qty);
    }

    @Test
    void holdsEveryNightAndReportsCreated() {
        HoldCommand c = command(today.plusDays(5), today.plusDays(8), 2);
        when(nights.hold(roomType, c.checkIn(), c.checkOut(), 2)).thenReturn(3);

        HoldResult result = service.execute(c);

        assertThat(result.created()).isTrue();
        assertThat(result.hold().status()).isEqualTo(HoldStatus.HELD);
        assertThat(result.hold().nights()).isEqualTo(3);
        assertThat(result.hold().expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    }

    @Test
    void soldOutWhenFewerNightsWereUpdatedThanRequested() {
        HoldCommand c = command(today.plusDays(5), today.plusDays(8), 2);
        when(nights.hold(roomType, c.checkIn(), c.checkOut(), 2)).thenReturn(2); // chỉ 2 trong 3 đêm đủ phòng
        when(nights.countNights(roomType, c.checkIn(), c.checkOut())).thenReturn(3);

        assertThatThrownBy(() -> service.execute(c)).isInstanceOf(SoldOutException.class);
    }

    @Test
    void noInventoryWhenTheNightsDoNotExist() {
        HoldCommand c = command(today.plusDays(5), today.plusDays(8), 1);
        when(nights.hold(roomType, c.checkIn(), c.checkOut(), 1)).thenReturn(0);
        when(nights.countNights(roomType, c.checkIn(), c.checkOut())).thenReturn(0);

        assertThatThrownBy(() -> service.execute(c)).isInstanceOf(NoInventoryException.class);
    }

    @Test
    void onlySomeNightsExistingIsAlsoNoInventory() {
        HoldCommand c = command(today.plusDays(5), today.plusDays(8), 1);
        when(nights.hold(roomType, c.checkIn(), c.checkOut(), 1)).thenReturn(2);
        when(nights.countNights(roomType, c.checkIn(), c.checkOut())).thenReturn(2);

        assertThatThrownBy(() -> service.execute(c)).isInstanceOf(NoInventoryException.class);
    }

    @Test
    void sameBookingWithSameRequestReturnsTheExistingHoldWithoutHoldingAgain() {
        HoldCommand c = command(today.plusDays(5), today.plusDays(8), 2);
        Hold existing = Hold.create(c, clock.instant(), Duration.ofMinutes(15));
        when(holds.findByBookingId(c.bookingId())).thenReturn(Optional.of(existing));

        HoldResult result = service.execute(c);

        assertThat(result.created()).isFalse();
        assertThat(result.hold()).isEqualTo(existing);
        verify(nights, never()).hold(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void sameBookingWithDifferentRequestIsRejected() {
        HoldCommand original = command(today.plusDays(5), today.plusDays(8), 2);
        Hold existing = Hold.create(original, clock.instant(), Duration.ofMinutes(15));
        when(holds.findByBookingId(original.bookingId())).thenReturn(Optional.of(existing));
        HoldCommand different = new HoldCommand(original.bookingId(), roomType, original.checkIn(), original.checkOut(),
                3);

        assertThatThrownBy(() -> service.execute(different))
                .isInstanceOfSatisfying(HoldConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("HOLD_MISMATCH"));
    }

    @Test
    void losingTheInsertRaceFallsBackToReplay() {
        HoldCommand c = command(today.plusDays(5), today.plusDays(8), 2);
        Hold winner = Hold.create(c, clock.instant(), Duration.ofMinutes(15));
        when(holds.findByBookingId(c.bookingId())).thenReturn(Optional.empty(), Optional.of(winner));
        doReturn(Optional.empty()).when(holds).insertIfAbsent(any(Hold.class));

        HoldResult result = service.execute(c);

        assertThat(result.created()).isFalse();
        assertThat(result.hold()).isEqualTo(winner);
        verify(nights, never()).hold(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void rejectsCheckOutThatIsNotAfterCheckIn() {
        HoldCommand c = command(today.plusDays(5), today.plusDays(5), 1);

        assertThatThrownBy(() -> service.execute(c)).isInstanceOf(InvalidStayException.class);
        verify(holds, never()).insertIfAbsent(any());
    }

    @Test
    void rejectsMoreThanTheMaximumNumberOfNights() {
        HoldCommand c = command(today.plusDays(5), today.plusDays(36), 1); // 31 đêm

        assertThatThrownBy(() -> service.execute(c)).isInstanceOf(InvalidStayException.class);
    }

    @Test
    void rejectsCheckInInThePastButAllowsToday() {
        assertThatThrownBy(() -> service.execute(command(today.minusDays(1), today.plusDays(1), 1)))
                .isInstanceOf(InvalidStayException.class);

        HoldCommand todayCommand = command(today, today.plusDays(1), 1);
        when(nights.hold(roomType, todayCommand.checkIn(), todayCommand.checkOut(), 1)).thenReturn(1);
        assertThatCode(() -> service.execute(todayCommand)).doesNotThrowAnyException();
    }
}
