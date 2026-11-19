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
 * Xác nhận lần giữ chỗ: chuyển phòng từ "đang giữ" sang "đã đặt". IDEMPOTENT:
 * gọi lại trên hold đã xác nhận
 * thì trả đúng hold đó, không đổi gì thêm.
 */
@Service
public class ConfirmHold {
    private final HoldRepository holds;
    private final NightInventory nights;
    private final Clock clock;

    public ConfirmHold(HoldRepository holds, NightInventory nights, Clock clock) {
        this.holds = holds;
        this.nights = nights;
        this.clock = clock;
    }

    /**
     * noRollbackFor: khi hold đã quá hạn, ta TRẢ PHÒNG VỀ rồi mới báo lỗi
     * HOLD_EXPIRED.
     * Nếu transaction rollback theo lỗi thì việc trả phòng bị hủy theo, phòng kẹt ở
     * "đang giữ" mãi.
     */
    @Transactional(noRollbackFor = HoldConflictException.class)
    public Hold execute(UUID holdId) {
        Instant now = clock.instant();
        Hold hold = holds.findById(holdId).orElseThrow(HoldNotFoundException::new);
        switch (hold.status()) {
            case CONFIRMED:
                return hold;
            case RELEASED:
            case EXPIRED:
                throw HoldConflictException.notActive(hold.status());
            default:
                break;
        }
        if (hold.isExpiredAt(now)) {
            if (holds.transition(holdId, HoldStatus.HELD, HoldStatus.EXPIRED, now) == 1) {
                nights.release(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), hold.qty());
            }
            throw HoldConflictException.expired();
        }
        // Đổi trạng thái trước: khóa dòng hold, nên xác nhận và nhả đua nhau thì chỉ
        // một bên thắng
        if (holds.transition(holdId, HoldStatus.HELD, HoldStatus.CONFIRMED, now) == 0) {
            Hold current = holds.findById(holdId).orElseThrow(HoldNotFoundException::new);
            if (current.status() == HoldStatus.CONFIRMED) {
                return current;
            }
            throw HoldConflictException.notActive(current.status());
        }
        int updated = nights.confirm(hold.roomTypeId(), hold.checkIn(), hold.checkOut(), hold.qty());
        if (updated != hold.nights()) {
            throw new IllegalStateException("Inconsistent inventory state while confirming hold " + holdId);
        }
        return hold.withStatus(HoldStatus.CONFIRMED, now);
    }

}
