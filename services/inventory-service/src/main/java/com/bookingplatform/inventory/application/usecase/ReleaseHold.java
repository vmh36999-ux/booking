package com.bookingplatform.inventory.application.usecase;

import com.bookingplatform.inventory.domain.exception.HoldConflictException;
import com.bookingplatform.inventory.domain.exception.HoldNotFoundException;
import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldStatus;
import com.bookingplatform.inventory.domain.port.HoldRepository;
import com.bookingplatform.inventory.domain.port.NightInventory;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhả lần giữ chỗ: trả phòng đang giữ về lại. IDEMPOTENT: gọi lại trên hold đã
 * nhả hoặc đã hết hạn
 * thì trả đúng hold đó. Hold đã XÁC NHẬN thì không nhả bằng thao tác này (hủy
 * đơn đã đặt là việc khác, tuần 12).
 */
@Service
public class ReleaseHold {

    private final HoldRepository holds;
    private final NightInventory nights;
    private final Clock clock;

    public ReleaseHold(HoldRepository holds, NightInventory nights, Clock clock) {
        this.holds = holds;
        this.nights = nights;
        this.clock = clock;
    }

    @Transactional
    public Hold execute(UUID holdId) {
        Instant now = clock.instant();
        Hold hold = holds.findById(holdId).orElseThrow(HoldNotFoundException::new);

        switch (hold.status()) {
            case RELEASED:
            case EXPIRED:
                return hold;
            case CONFIRMED:
                throw HoldConflictException.alreadyConfirmed();
            default:
                break;
        }

        if (holds.transition(holdId, HoldStatus.HELD, HoldStatus.RELEASED, now) == 0) {
            Hold current = holds.findById(holdId).orElseThrow(HoldNotFoundException::new);
            if (current.status() == HoldStatus.CONFIRMED) {
                throw HoldConflictException.alreadyConfirmed();
            }
            return current;
        }

        int updated = nights.release(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), hold.qty());
        if (updated != hold.nights()) {
            throw new IllegalStateException("Inconsistent inventory state while releasing hold " + holdId);
        }
        return hold.withStatus(HoldStatus.RELEASED, now);
    }
}
