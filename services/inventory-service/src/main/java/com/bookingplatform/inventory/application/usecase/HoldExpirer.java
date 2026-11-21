package com.bookingplatform.inventory.application.usecase;

import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldStatus;
import com.bookingplatform.inventory.domain.port.HoldRepository;
import com.bookingplatform.inventory.domain.port.NightInventory;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cho hết hạn MỘT hold: đổi HELD sang EXPIRED và trả phòng về, trong MỘT
 * transaction riêng.
 * Mỗi hold một transaction để một hold lỗi không kéo theo các hold khác bị hoàn
 * tác.
 */
@Service
public class HoldExpirer {
    private final HoldRepository holds;
    private final NightInventory nights;
    private final Clock clock;

    public HoldExpirer(HoldRepository holds, NightInventory nights, Clock clock) {
        this.holds = holds;
        this.nights = nights;
        this.clock = clock;
    }

    /**
     * Trả true nếu vừa cho hết hạn hold này; false nếu không cần (chưa đến hạn, đã
     * xử lý rồi, không tồn tại).
     */
    @Transactional
    public boolean expire(UUID holdId) {
        Instant now = clock.instant();
        Optional<Hold> found = holds.findById(holdId);
        if (found.isEmpty()) {
            return false;
        }
        Hold hold = found.get();
        if (hold.status() != HoldStatus.HELD || !hold.isExpiredAt(now)) {
            return false;
        }
        // Đổi trạng thái có điều kiện: nếu một thao tác khác (xác nhận, nhả) vừa thắng
        // thì ta không trả phòng lần hai
        if (holds.transition(holdId, HoldStatus.HELD, HoldStatus.EXPIRED, now) == 0) {
            return false;
        }
        int updated = nights.release(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), hold.qty());
        if (updated != hold.nights()) {
            throw new IllegalStateException("Inconsistent inventory state when expiring hold " + holdId);
        }
        return true;
    }
}
