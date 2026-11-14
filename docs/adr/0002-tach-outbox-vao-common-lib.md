# ADR 0002: Tách outbox vào common-lib

- Trạng thái: chấp nhận
- Ngày: 16/11/2026 (tuần 4)

## Bối cảnh

Ở tuần 3, auth-service tự có `OutboxWriter`, `JdbcOutboxWriter`, `OutboxPublisher` và `SchedulingConfig`.
Tuần 4, property-service cũng cần phát sự kiện (`PropertyPublished`, `RoomTypeCreated`...) với đúng cơ chế đó.
Từ tuần 5, inventory, pricing, booking, payment, review đều cần outbox. Sao chép mã sang từng service
nghĩa là sửa một lỗi phải sửa ở nhiều nơi, và các bản sao sẽ lệch nhau theo thời gian.

## Quyết định

Chuyển cơ chế outbox vào `libs/common-lib`, dưới dạng auto-configuration của Spring Boot:

- `OutboxWriter` (interface), `JdbcOutboxWriter`, `OutboxPublisher`, `OutboxAutoConfiguration`.
- **Công tắc `app.outbox.enabled=true`** trong `application.yml` của service nào cần. Không đặt thì không tạo gì.
- Công tắc phụ `app.outbox.publisher.enabled=false`: chỉ ghi outbox, không gửi lên Kafka
  (dùng khi seed dữ liệu và trong test không có Kafka).
- Mỗi service vẫn giữ bảng `outbox` riêng trong database riêng (từ `V1__baseline.sql`). Chỉ **mã** được dùng chung, **dữ liệu** thì không.
- Khai báo các thư viện cần thiết (`spring-jdbc`, `spring-kafka`, `spring-tx`, Jackson) với scope `provided`:
  `common-lib` không ép phiên bản framework lên service.

## Hệ quả

Tốt:
- Một bản duy nhất để sửa lỗi và cải tiến (ví dụ đổi chiến lược retry).
- Service mới chỉ cần thêm 4 dòng cấu hình để có outbox.
- Cơ chế đã được kiểm chứng ở auth-service (tuần 3) trước khi tách, nên hợp đồng ổn định.

Đánh đổi:
- `common-lib` lớn hơn và phụ thuộc vào nhiều thư viện hơn (đều `provided`). Nếu nó phình tiếp, cân nhắc tách thành `outbox-starter` riêng.
- Mọi service dùng `common-lib` phải cài lại thư viện khi sửa (`mvn install` cho `libs/`).
- Cơ chế gửi bằng "poller" có độ trễ khoảng 0,4 giây và đảm bảo "ít nhất một lần". Consumer phải tự chống trùng bằng `eventId`.

## Phương án đã cân nhắc

1. **Giữ bản sao ở từng service.** Đơn giản lúc đầu, nhưng nhân bản mã và dễ lệch. Bị loại.
2. **Dùng Debezium Outbox Router thay cho poller.** Giảm tải cho database và độ trễ thấp hơn, nhưng cần hạ tầng Kafka Connect.
   Để dành cho giai đoạn 5 (data platform), khi đã có Debezium.
3. **Tạo module `outbox-starter` riêng.** Sạch hơn về ranh giới, nhưng thêm một module khi chưa cần.
   Sẽ xem lại nếu `common-lib` phình to.
