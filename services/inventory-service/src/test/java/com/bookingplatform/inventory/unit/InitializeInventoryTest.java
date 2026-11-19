package com.bookingplatform.inventory.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookingplatform.inventory.application.usecase.InitializeInventory;
import com.bookingplatform.inventory.config.InventoryProperties;
import com.bookingplatform.inventory.domain.port.NightInventory;
import com.bookingplatform.inventory.domain.port.ProcessedEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra tạo tồn kho: lần đầu tạo đủ 365 đêm từ hôm nay, sự kiện trùng bị bỏ
 * qua (2 test).
 * InitializeInventoryTest
 */
class InitializeInventoryTest {

    private final ProcessedEventRepository processedEvents = mock(ProcessedEventRepository.class);
    private final NightInventory nights = mock(NightInventory.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private final InitializeInventory service = new InitializeInventory(
            processedEvents, nights, new InventoryProperties(Duration.ofMinutes(15), 365, 30), clock);

    private final UUID eventId = UUID.randomUUID();
    private final UUID roomType = UUID.randomUUID();

    @Test
    void firstTimeCreatesNightsForTheWholeHorizonStartingToday() {
        when(processedEvents.markProcessed(eventId)).thenReturn(true);

        boolean processed = service.handle(eventId, roomType, 12);

        assertThat(processed).isTrue();
        verify(nights).createNights(roomType, LocalDate.of(2026, 11, 23), 365, 12);
    }

    @Test
    void duplicateEventIsIgnored() {
        when(processedEvents.markProcessed(eventId)).thenReturn(false);

        boolean processed = service.handle(eventId, roomType, 12);

        assertThat(processed).isFalse();
        verifyNoInteractions(nights);
        org.mockito.Mockito.verify(nights, org.mockito.Mockito.never()).createNights(any(), any(), anyInt(), anyInt());
    }
}
