package com.bookingplatform.inventory.application.usecase;

import com.bookingplatform.inventory.config.InventoryProperties;
import com.bookingplatform.inventory.domain.port.NightInventory;
import com.bookingplatform.inventory.domain.port.ProcessedEventRepository;
import com.bookingplatform.inventory.domain.port.RoomTypeOwners;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo tồn kho khi có loại phòng mới (sự kiện RoomTypeCreated). Là IDEMPOTENT
 * CONSUMER:
 * ghi processed_event, tạo các đêm và ghi nhớ chủ của loại phòng trong cùng một
 * transaction.
 */
@Service
public class InitializeInventory {

    private final ProcessedEventRepository processedEvents;
    private final NightInventory nights;
    private final RoomTypeOwners owners;
    private final InventoryProperties properties;
    private final Clock clock;

    public InitializeInventory(ProcessedEventRepository processedEvents, NightInventory nights,
            RoomTypeOwners owners, InventoryProperties properties, Clock clock) {
        this.processedEvents = processedEvents;
        this.nights = nights;
        this.owners = owners;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Không biết chủ loại phòng (cách gọi của tuần 5). Trả true nếu đã xử lý, false
     * nếu là sự kiện trùng.
     */
    @Transactional
    public boolean handle(UUID eventId, UUID roomTypeId, int totalRooms) {
        return handle(eventId, roomTypeId, totalRooms, null, null);
    }

    /**
     * propertyId và hostId có thể null (sự kiện cũ, phát trước khi RoomTypeCreated
     * có hostId):
     * khi đó chỉ tạo tồn kho, chưa ghi nhớ chủ, và host chưa chỉnh được số phòng
     * của loại phòng đó.
     */
    @Transactional
    public boolean handle(UUID eventId, UUID roomTypeId, int totalRooms, UUID propertyId, UUID hostId) {
        if (!processedEvents.markProcessed(eventId)) {
            return false;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        nights.createNights(roomTypeId, today, properties.horizonDays(), totalRooms);
        if (propertyId != null && hostId != null) {
            owners.register(roomTypeId, propertyId, hostId);
        }
        return true;
    }
}
