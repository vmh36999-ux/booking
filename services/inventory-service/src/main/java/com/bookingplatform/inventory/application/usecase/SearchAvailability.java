package com.bookingplatform.inventory.application.usecase;

import com.bookingplatform.inventory.config.InventoryProperties;
import com.bookingplatform.inventory.domain.exception.InvalidStayException;
import com.bookingplatform.inventory.domain.model.NightSummary;
import com.bookingplatform.inventory.domain.model.RoomTypeAvailability;
import com.bookingplatform.inventory.domain.port.NightInventory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tìm phòng trống hàng loạt cho search-service: nhiều loại phòng, một khoảng
 * ngày, một số phòng cần.
 *
 * LƯU Ý: kết quả chỉ là ẢNH CHỤP tại thời điểm hỏi. Không giữ chỗ và không khóa
 * gì.
 * Người khác có thể giữ mất phòng ngay sau đó, nên quyết định cuối cùng luôn là
 * lệnh giữ chỗ (HoldRooms).
 */
@Service
public class SearchAvailability {

    private final NightInventory nights;
    private final InventoryProperties properties;
    private final Clock clock;

    public SearchAvailability(NightInventory nights, InventoryProperties properties, Clock clock) {
        this.nights = nights;
        this.properties = properties;
        this.clock = clock;
    }

    /** Kết quả theo ĐÚNG thứ tự yêu cầu, bỏ các id trùng. */
    @Transactional(readOnly = true)
    public List<RoomTypeAvailability> execute(Collection<UUID> roomTypeIds, LocalDate checkIn, LocalDate checkOut,
            int qty) {
        validate(checkIn, checkOut);
        int expectedNights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);

        Set<UUID> ids = new LinkedHashSet<>(roomTypeIds);
        Map<UUID, NightSummary> summaries = nights.summarize(ids, checkIn, checkOut);

        List<RoomTypeAvailability> results = new ArrayList<>();
        for (UUID id : ids) {
            NightSummary summary = summaries.get(id);
            if (summary == null || summary.nights() != expectedNights) {
                results.add(new RoomTypeAvailability(id, false, summary == null ? 0 : summary.nights(), 0, false));
            } else {
                int free = Math.max(0, summary.minFree());
                results.add(new RoomTypeAvailability(id, true, expectedNights, free, free >= qty));
            }
        }
        return results;
    }

    private void validate(LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidStayException("Check-out date must be after check-in date");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > properties.maxNights()) {
            throw new InvalidStayException("Maximum of " + properties.maxNights() + " nights per search");
        }
        if (checkIn.isBefore(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))) {
            throw new InvalidStayException("Check-in date cannot be in the past");
        }
    }
}
