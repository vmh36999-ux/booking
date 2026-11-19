package com.bookingplatform.inventory.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldCommand;
import com.bookingplatform.inventory.domain.model.HoldStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HoldDomainTest {

    private final Instant now = Instant.parse("2026-11-23T03:00:00Z");
    private final UUID roomType = UUID.randomUUID();
    private final HoldCommand command = new HoldCommand(
            UUID.randomUUID(), roomType, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 23), 2);

    @Test
    void nightsCountFromCheckInUpToTheDayBeforeCheckOut() {
        Hold hold = Hold.create(command, now, Duration.ofMinutes(15));

        assertThat(hold.nights()).isEqualTo(3);
        assertThat(hold.status()).isEqualTo(HoldStatus.HELD);
        assertThat(hold.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
    }

    @Test
    void matchesOnlyAnIdenticalRequest() {
        Hold hold = Hold.create(command, now, Duration.ofMinutes(15));

        assertThat(hold.matches(command)).isTrue();
        assertThat(
                hold.matches(new HoldCommand(command.bookingId(), roomType, command.checkIn(), command.checkOut(), 3)))
                .isFalse();
        assertThat(hold.matches(
                new HoldCommand(command.bookingId(), UUID.randomUUID(), command.checkIn(), command.checkOut(), 2)))
                .isFalse();
        assertThat(hold.matches(
                new HoldCommand(command.bookingId(), roomType, command.checkIn(), command.checkOut().plusDays(1), 2)))
                .isFalse();
    }

    @Test
    void expiryAndStatusChange() {
        Hold hold = Hold.create(command, now, Duration.ofMinutes(15));

        assertThat(hold.isExpiredAt(now.plusSeconds(899))).isFalse();
        assertThat(hold.isExpiredAt(now.plusSeconds(900))).isTrue();

        Hold confirmed = hold.withStatus(HoldStatus.CONFIRMED, now.plusSeconds(60));
        assertThat(confirmed.status()).isEqualTo(HoldStatus.CONFIRMED);
        assertThat(confirmed.id()).isEqualTo(hold.id());
        assertThat(confirmed.updatedAt()).isEqualTo(now.plusSeconds(60));
        assertThat(hold.status()).as("bản gốc không bị đổi").isEqualTo(HoldStatus.HELD);
    }
}
