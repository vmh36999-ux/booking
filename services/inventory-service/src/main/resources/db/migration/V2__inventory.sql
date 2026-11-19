-- Tuần 5: tồn kho theo đêm và các lần giữ chỗ.
-- Dùng TIMESTAMPTZ cho thời điểm (hold hết hạn lúc nào) vì kiểu này không phụ thuộc múi giờ của máy chủ.

-- Mỗi dòng = số phòng của MỘT loại phòng trong MỘT đêm.
--   total  : tổng số phòng
--   held   : đang được giữ (chưa xác nhận)
--   booked : đã đặt chắc chắn
--   còn trống = total - held - booked
CREATE TABLE room_night (
    room_type_id UUID NOT NULL,
    night        DATE NOT NULL,
    total        INT  NOT NULL CHECK (total >= 0),
    held         INT  NOT NULL DEFAULT 0,
    booked       INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (room_type_id, night),
    -- LƯỚI AN TOÀN CUỐI CÙNG: dù code có lỗi, database không bao giờ cho bán quá số phòng hay có số âm.
    CONSTRAINT ck_room_night_counts CHECK (held >= 0 AND booked >= 0 AND held + booked <= total)
);

-- Mỗi lần giữ chỗ của một đơn đặt phòng.
CREATE TABLE room_hold (
    id           UUID        PRIMARY KEY,
    booking_id   UUID        NOT NULL,
    room_type_id UUID        NOT NULL,
    check_in     DATE        NOT NULL,
    check_out    DATE        NOT NULL,                   -- ngày trả phòng: đêm cuối là check_out - 1
    qty          INT         NOT NULL CHECK (qty > 0),
    status       VARCHAR(12) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Mỗi đơn đặt phòng chỉ có MỘT lần giữ chỗ: gọi lại cùng bookingId không giữ thêm lần nữa.
    CONSTRAINT uq_room_hold_booking UNIQUE (booking_id),
    CONSTRAINT ck_room_hold_status  CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_room_hold_dates   CHECK (check_out > check_in)
);

-- Dùng ở tuần 6: job quét các hold quá hạn. Chỉ đánh chỉ mục các dòng còn HELD cho nhỏ gọn.
CREATE INDEX idx_room_hold_active_expiry ON room_hold (expires_at) WHERE status = 'HELD';
