# ADR 0003: Giữ chỗ bằng một câu UPDATE nguyên tử trong PostgreSQL

- Trạng thái: chấp nhận
- Ngày: 23/11/2026 (tuần 5)

## Bối cảnh

Hai khách cùng đặt phòng cuối cùng cùng lúc thì chỉ một người được giữ. Bán quá số phòng (overbooking) là lỗi nghiêm trọng nhất của hệ thống đặt phòng:
khách đến nơi mà không còn phòng.

Sơ đồ kiến trúc ban đầu đề xuất khóa phân tán bằng Redis. Khi thiết kế chi tiết, ta thấy khóa Redis không thể là chốt chặn cuối cùng:
khóa có thể hết hạn khi tiến trình bị treo, Redis có thể mất khóa khi chuyển nút, và dữ liệu thật nằm ở PostgreSQL nên hai nơi có thể lệch nhau.

## Quyết định

1. Tồn kho lưu theo **đêm** ở bảng `room_night(total, held, booked)`.
2. Giữ chỗ là **một câu UPDATE** duy nhất vừa kiểm tra vừa ghi:
   `UPDATE room_night SET held = held + :qty WHERE ... AND total - held - booked >= :qty`.
   Số dòng được cập nhật phải bằng số đêm yêu cầu; nếu khác thì **rollback toàn bộ** và trả `SOLD_OUT`.
3. Ràng buộc **`CHECK (held >= 0 AND booked >= 0 AND held + booked <= total)`** ở database là lưới an toàn cuối cùng:
   dù code có lỗi, database từ chối mọi thay đổi gây bán quá số phòng hoặc số âm.
4. Mỗi `booking_id` chỉ có một lần giữ chỗ (`UNIQUE`), và `INSERT ... ON CONFLICT DO NOTHING` làm cho việc gọi lại an toàn (idempotent).
5. Đổi trạng thái hold bằng **câu UPDATE có điều kiện** (`WHERE status = 'HELD'`), nên xác nhận và nhả đua nhau thì chỉ một bên thắng.
6. Redis (nếu dùng sau này) chỉ để giảm tải cho phòng "nóng", **không** phải nơi đảm bảo tính đúng.

## Hệ quả

Tốt:
- Tính đúng đắn nằm ở một nơi duy nhất (database) và được kiểm tra bằng test song song với Postgres thật.
- Không cần hạ tầng khóa phân tán.
- Dễ giải thích và dễ gỡ lỗi: nhìn vào bảng là biết trạng thái.

Đánh đổi:
- Các giao dịch cùng tranh một dòng phải **xếp hàng** (khóa dòng). Phòng rất "nóng" có thể thành điểm nghẽn.
  Nếu gặp, thêm hàng đợi hoặc bộ nhớ đệm phía trước, nhưng vẫn giữ database làm nơi quyết định cuối cùng.
- Hai giao dịch giữ các khoảng ngày chồng lấn có thể hiếm khi gây deadlock; PostgreSQL tự hủy một bên.
  Tuần 6 bổ sung thử lại có kiểm soát và test song song 100 luồng.
- Chỉ tạo tồn kho cho 365 ngày kể từ khi loại phòng được tạo; cần job kéo dài khung thời gian (sẽ làm sau).

## Phương án đã cân nhắc

1. **Khóa Redis rồi đọc, kiểm tra, ghi.** Bị loại làm chốt chặn chính vì lý do ở phần bối cảnh.
2. **`SELECT ... FOR UPDATE` rồi `UPDATE`.** Đúng nhưng cần hai câu lệnh và giữ khóa lâu hơn. Câu UPDATE có điều kiện đạt cùng kết quả gọn hơn.
3. **Khóa lạc quan (cột version) và thử lại.** Phức tạp hơn và dễ thử lại nhiều khi tranh chấp cao.
