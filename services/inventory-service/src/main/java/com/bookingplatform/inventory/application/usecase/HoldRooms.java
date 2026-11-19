package com.bookingplatform.inventory.application.usecase;

import com.bookingplatform.inventory.config.InventoryProperties;
import com.bookingplatform.inventory.domain.exception.HoldConflictException;
import com.bookingplatform.inventory.domain.exception.InvalidStayException;
import com.bookingplatform.inventory.domain.exception.NoInventoryException;
import com.bookingplatform.inventory.domain.exception.SoldOutException;
import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldCommand;
import com.bookingplatform.inventory.domain.model.HoldResult;
import com.bookingplatform.inventory.domain.port.HoldRepository;
import com.bookingplatform.inventory.domain.port.NightInventory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giữ chỗ. TOÀN BỘ nằm trong MỘT transaction: hoặc giữ đủ mọi đêm, hoặc không
 * đêm nào.
 *
 * Các bước: (1) bookingId đã giữ chỗ rồi thì trả lại lần giữ cũ; (2) kiểm tra
 * khoảng ngày;
 * (3) ghi lần giữ chỗ; (4) MỘT câu UPDATE tăng "held" cho mọi đêm, chỉ những
 * đêm còn đủ phòng;
 * (5) số đêm được cập nhật khác số đêm yêu cầu nghĩa là có đêm không đủ phòng:
 * ném lỗi, transaction rollback
 * (bước 3 và các đêm đã tăng được hoàn lại).
 */
@Service
public class HoldRooms {
    private final HoldRepository holds;
    private final NightInventory nights;
    private final InventoryProperties properties;
    private final Clock clock;

    public HoldRooms(HoldRepository holds, NightInventory nights, InventoryProperties properties, Clock clock) {
        this.holds = holds;
        this.nights = nights;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public HoldResult execute(HoldCommand command) {
        Optional<Hold> existing = holds.findByBookingId(command.bookingId());
        if (existing.isPresent()) {
            return replay(existing.get(), command);
        }
        Instant now = clock.instant();
        validate(command, now);
        Hold candidate = Hold.create(command, now, properties.holdTtl());
        if (holds.insertIfAbsent(candidate).isEmpty()) {
            // Có request khác cùng bookingId vừa chen vào trước: coi như gọi lại
            Hold winner = holds.findByBookingId(command.bookingId()).orElseThrow(IllegalStateException::new);
            return replay(winner, command);
        }
        int updated = nights.hold(command.roomTypeId(), command.checkIn(), command.checkOut(), command.qty());
        if (updated != candidate.nights()) {
            // Phân biệt "hết phòng" với "chưa có tồn kho cho các ngày này"
            int existingNights = nights.countNights(command.roomTypeId(), command.checkIn(), command.checkOut());
            if (existingNights != candidate.nights()) {
                throw new NoInventoryException();
            }
            throw new SoldOutException();
        }
        return new HoldResult(candidate, true);
    }

    /**
     * Gọi lại cùng bookingId: trả đúng lần giữ cũ (kể cả khi nó đã được nhả hay hết
     * hạn). Khác nội dung thì từ chối.
     */
    private HoldResult replay(Hold existing, HoldCommand command) {
        if (!existing.matches(command)) {
            throw HoldConflictException.mismatch();
        }
        return new HoldResult(existing, false);
    }

    private void validate(HoldCommand command, Instant now) {
        if (!command.checkOut().isAfter(command.checkIn())) {
            throw new InvalidStayException("Check-out date must be after check-in date");
        }
        long nightCount = ChronoUnit.DAYS.between(command.checkIn(), command.checkOut());
        if (nightCount > properties.maxNights()) {
            throw new InvalidStayException("Maximum " + properties.maxNights() + " nights allowed per hold");
        }
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (command.checkIn().isBefore(today)) {
            throw new InvalidStayException("Check-in date cannot be in the past");
        }
    }
}
