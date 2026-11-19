package com.bookingplatform.inventory.domain.port;

import com.bookingplatform.inventory.domain.model.Hold;
import com.bookingplatform.inventory.domain.model.HoldStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface HoldRepository {

    /**
     * Lưu lần giữ chỗ mới. Trả về rỗng nếu bookingId đã có lần giữ chỗ (không ghi
     * đè).
     */
    Optional<Hold> insertIfAbsent(Hold hold);

    Optional<Hold> findById(UUID id);

    Optional<Hold> findByBookingId(UUID bookingId);

    /**
     * Đổi trạng thái CÓ ĐIỀU KIỆN: chỉ đổi nếu trạng thái hiện tại đúng là "from".
     * Trả 1 nếu đổi được, 0 nếu không.
     * Nhờ vậy hai thao tác đua nhau trên cùng một lần giữ chỗ (xác nhận và nhả) thì
     * chỉ một bên thắng.
     */
    int transition(UUID id, HoldStatus from, HoldStatus to, Instant now);
}
