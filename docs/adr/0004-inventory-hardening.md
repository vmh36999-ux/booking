# ADR 0004: Củng cố inventory-service (khóa theo thứ tự, ShedLock, bản sao quyền sở hữu)

- Trạng thái: chấp nhận
- Ngày: 30/11/2026 (tuần 6)

## Bối cảnh

Tuần 5 giữ chỗ bằng một câu UPDATE nguyên tử (ADR 0003). Tuần 6 củng cố cho môi trường thật: nhiều instance, nhiều người cùng đặt,
host chỉnh số phòng, search hỏi hàng loạt.

## Quyết định

### 1. Khóa các đêm theo THỨ TỰ trước khi cập nhật

Trước mỗi câu UPDATE nhiều đêm, chạy `SELECT night ... ORDER BY night FOR UPDATE` để khóa các dòng theo ngày tăng dần.

Lý do: câu UPDATE nhiều dòng khóa từng dòng khi quét tới nó. Thứ tự quét phụ thuộc vào kế hoạch của PostgreSQL (quét chỉ mục hay quét tuần tự theo vị trí vật lý).
Với bảng nhỏ và nhiều lần cập nhật, vị trí vật lý của các dòng có thể đảo thứ tự, nên hai giao dịch giữ các khoảng ngày chồng lấn (1-3 và 2-4)
có thể khóa theo hai chiều ngược nhau và **chờ nhau theo vòng tròn (deadlock)**. Khóa theo một thứ tự cố định loại bỏ khả năng này.

Đánh đổi: thêm một câu SELECT cho mỗi lần thao tác (tối đa 30 dòng), và giữ khóa lâu hơn một chút.

### 2. Job hết hạn dùng ShedLock

Job quét hold quá hạn chạy mỗi phút. Khi chạy nhiều instance, `@SchedulerLock` (ShedLock, khóa lưu trong bảng `shedlock` của PostgreSQL)
đảm bảo mỗi lượt chỉ một instance chạy. Mỗi hold được cho hết hạn trong **transaction riêng** và đổi trạng thái bằng câu UPDATE có điều kiện,
nên ngay cả khi hai instance chạy chồng nhau (ShedLock lỗi, khóa hết hạn sớm), kết quả vẫn đúng: ShedLock chỉ để **tránh làm việc thừa**, không phải để đảm bảo tính đúng.

### 3. Bản sao quyền sở hữu loại phòng (read model)

Inventory cần biết loại phòng thuộc host nào để kiểm tra quyền khi host chỉnh số phòng. Thay vì gọi đồng bộ sang property-service,
sự kiện `RoomTypeCreated` được bổ sung `hostId` và inventory ghi vào bảng `room_type_owner`.

Hệ quả: inventory không phụ thuộc property-service lúc chạy; đổi lại là dữ liệu nhất quán sau một khoảng ngắn, và các loại phòng
tạo **trước** khi có `hostId` trong sự kiện sẽ chưa chỉnh được số phòng (cần phát lại sự kiện hoặc nạp bù).

### 4. Tìm phòng trống chỉ đọc, không khóa

`POST /availability/search` là ảnh chụp tại thời điểm hỏi. Quyết định cuối cùng luôn là lệnh giữ chỗ. Nhờ không khóa nên search nhanh và không cản đường đặt phòng.

## Phương án đã cân nhắc

1. **Thử lại khi gặp deadlock.** Che được triệu chứng nhưng không loại bỏ nguyên nhân và làm tăng độ trễ. Khóa theo thứ tự tốt hơn; có thể thêm thử lại như lớp phòng thủ phụ nếu thật sự cần.
2. **Khóa cố vấn (`pg_advisory_lock`) theo từng loại phòng.** Đơn giản nhưng chặn cả các khoảng ngày không chồng lấn nhau.
3. **Gọi property-service để kiểm tra quyền sở hữu.** Chính xác tức thời nhưng thêm một phụ thuộc đồng bộ vào đường thao tác của host.
