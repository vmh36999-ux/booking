
package com.bookingplatform.inventory.domain.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bookingplatform.inventory.domain.model.NightSummary;

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

    /**
     * Tóm tắt nhiều loại phòng một lượt (chỉ đọc, không khóa). Loại phòng chưa có
     * đêm nào thì không có trong kết quả.
     */
    Map<UUID, NightSummary> summarize(Collection<UUID> roomTypeIds, LocalDate checkIn, LocalDate checkOut);

    /**
     * Đặt tổng số phòng cho các đêm trong [from, to). Chỉ đêm nào số giữ + số đặt
     * KHÔNG vượt mức mới mới được đổi.
     * Trả về số đêm đã đổi.
     */
    int setTotal(UUID roomTypeId, LocalDate from, LocalDate to, int totalRooms);

    /** Các đêm (tối đa 10) mà số giữ + số đặt đang vượt mức totalRooms. */
    List<LocalDate> findNightsAboveTotal(UUID roomTypeId, LocalDate from, LocalDate to, int totalRooms);
}
