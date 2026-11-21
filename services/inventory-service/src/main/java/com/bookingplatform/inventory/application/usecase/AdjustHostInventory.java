package com.bookingplatform.inventory.application.usecase;

import com.bookingplatform.inventory.domain.exception.BelowCommitmentException;
import com.bookingplatform.inventory.domain.exception.InvalidStayException;
import com.bookingplatform.inventory.domain.exception.NoInventoryException;
import com.bookingplatform.inventory.domain.exception.NotRoomTypeOwnerException;
import com.bookingplatform.inventory.domain.exception.RoomTypeNotFoundException;
import com.bookingplatform.inventory.domain.port.NightInventory;
import com.bookingplatform.inventory.domain.port.RoomTypeOwners;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Host chỉnh TỔNG SỐ PHÒNG cho một khoảng ngày.
 *
 * Hai luật: (1) chỉ chủ của loại phòng mới được chỉnh; (2) KHÔNG được giảm
 * xuống dưới số đã giữ và đã đặt
 * (đã có khách thì không thể "rút" phòng của họ). All-or-nothing: một đêm vướng
 * thì không đêm nào được đổi.
 */
@Service
public class AdjustHostInventory {
    private static final int MAX_RANGE_DAYS = 366;

    private final RoomTypeOwners owners;
    private final NightInventory nights;
    private final Clock clock;

    public AdjustHostInventory(RoomTypeOwners owners, NightInventory nights, Clock clock) {
        this.owners = owners;
        this.nights = nights;
        this.clock = clock;
    }

    /** Trả về số đêm đã đổi. */
    @Transactional
    public int execute(UUID hostId, UUID roomTypeId, LocalDate from, LocalDate to, int totalRooms) {
        // Kiểm tra quyền TRƯỚC mọi thay đổi: 404 nếu không biết loại phòng, 403 nếu
        // không phải của host này
        UUID owner = owners.findHostId(roomTypeId)
                .orElseThrow(RoomTypeNotFoundException::new);
        if (!owner.equals(hostId)) {
            throw new NotRoomTypeOwnerException();
        }
        validate(from, to);
        int nightCount = (int) ChronoUnit.DAYS.between(from, to);
        int updated = nights.setTotal(roomTypeId, from, to, totalRooms);
        if (updated != nightCount) {
            // Có đêm không đổi được: chưa có tồn kho, hay đang vướng số giữ và đặt?
            if (nights.countNights(roomTypeId, from, to) != nightCount) {
                throw new NoInventoryException();
            }
            throw new BelowCommitmentException(nights.findNightsAboveTotal(roomTypeId, from, to, totalRooms));
        }
        return nightCount;
    }

    private void validate(LocalDate from, LocalDate to) {
        if (!to.isAfter(from)) {
            throw new InvalidStayException("End date must be after start date");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new InvalidStayException("Maximum of " + MAX_RANGE_DAYS + " days per update");
        }
        if (from.isBefore(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))) {
            throw new InvalidStayException("Cannot modify past dates");
        }
    }
}
