package com.bookingplatform.inventory.application.usecase;

import com.bookingplatform.inventory.config.InventoryProperties;
import com.bookingplatform.inventory.domain.port.NightInventory;
import com.bookingplatform.inventory.domain.port.ProcessedEventRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo tồn kho khi có loại phòng mới (sự kiện RoomTypeCreated). Là IDEMPOTENT
 * CONSUMER:
 * ghi processed_event và tạo các đêm trong cùng một transaction.
 */
@Service
public class InitializeInventory {

    private final ProcessedEventRepository processedEvents;
    private final NightInventory nights;
    private final InventoryProperties properties;
    private final Clock clock;

    public InitializeInventory(ProcessedEventRepository processedEvents, NightInventory nights,
            InventoryProperties properties, Clock clock) {
        this.processedEvents = processedEvents;
        this.nights = nights;
        this.properties = properties;
        this.clock = clock;
    }

    /** Trả true nếu đã xử lý, false nếu là sự kiện trùng. */
    @Transactional
    public boolean handle(UUID eventId, UUID roomTypeId, int totalRooms) {
        if (!processedEvents.markProcessed(eventId)) {
            return false;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        nights.createNights(roomTypeId, today, properties.horizonDays(), totalRooms);
        return true;
    }
}
