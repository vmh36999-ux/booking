-- Tuần 6: inventory ghi nhớ loại phòng nào thuộc host nào.
-- Dữ liệu lấy từ sự kiện RoomTypeCreated (nay có thêm hostId). Dùng để kiểm tra quyền khi host chỉnh số phòng.
-- Đây là "bản sao đọc" (read model): inventory không gọi ngược sang property-service.
CREATE TABLE room_type_owner (
    room_type_id UUID        PRIMARY KEY,
    property_id  UUID        NOT NULL,
    host_id      UUID        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
