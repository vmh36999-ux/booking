
package com.bookingplatform.inventory.domain.port;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tồn kho theo đêm. Mỗi hàm trả về SỐ ĐÊM (số dòng) bị thay đổi để nơi gọi kiểm
 * tra "đủ hết các đêm chưa".
 */
public interface NightInventory {

    /**
     * Giữ qty phòng cho mọi đêm trong [checkIn, checkOut). Chỉ đêm nào còn đủ phòng
     * mới được giữ.
     */
    int hold(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int qty);

    /** Chuyển qty phòng từ "đang giữ" sang "đã đặt". */
    int confirm(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int qty);

    /** Trả qty phòng đang giữ về lại. */
    int release(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int qty);

    /** Có bao nhiêu đêm đã được tạo tồn kho trong khoảng này. */
    int countNights(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut);

    /**
     * Tạo tồn kho cho "days" đêm kể từ "from". Đêm nào đã có thì bỏ qua (không ghi
     * đè).
     */
    void createNights(UUID roomTypeId, LocalDate from, int days, int totalRooms);
}
