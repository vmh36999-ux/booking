-- Tuần 4: dữ liệu khách sạn của property-service.
-- Dùng TIMESTAMPTZ để khớp kiểu java.time.Instant của Hibernate (ddl-auto: validate).

CREATE TABLE property (
    id           UUID             PRIMARY KEY,
    host_id      UUID             NOT NULL,                 -- id người dùng (claim "sub" của JWT), không phải khóa ngoại: auth_db là DB khác
    name         VARCHAR(200)     NOT NULL,
    description  TEXT,
    city         VARCHAR(100)     NOT NULL,
    country      VARCHAR(2)       NOT NULL,
    address      VARCHAR(300),
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    currency     VARCHAR(3)       NOT NULL,
    status       VARCHAR(20)      NOT NULL,
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    CONSTRAINT ck_property_latitude  CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT ck_property_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_property_status    CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUSPENDED'))
);
CREATE INDEX idx_property_host        ON property (host_id, id);
CREATE INDEX idx_property_status_city ON property (status, city);

CREATE TABLE room_type (
    id               UUID         PRIMARY KEY,
    property_id      UUID         NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    max_guests       INT          NOT NULL CHECK (max_guests > 0),
    bed_config       VARCHAR(100),
    total_rooms      INT          NOT NULL CHECK (total_rooms > 0),
    base_price_minor BIGINT       NOT NULL CHECK (base_price_minor >= 0),   -- giá tham khảo, giá thật do pricing-service quản lý
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_room_type_property ON room_type (property_id, created_at, id);

-- Danh mục tiện nghi cố định. Thêm tiện nghi mới bằng một migration mới.
CREATE TABLE amenity (
    code VARCHAR(50)  PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);
INSERT INTO amenity (code, name) VALUES
    ('WIFI',             'Wi-Fi miễn phí'),
    ('POOL',             'Hồ bơi'),
    ('PARKING',          'Bãi đỗ xe'),
    ('BREAKFAST',        'Bữa sáng'),
    ('AIR_CONDITIONING', 'Điều hòa'),
    ('GYM',              'Phòng tập gym'),
    ('SPA',              'Spa'),
    ('RESTAURANT',       'Nhà hàng'),
    ('AIRPORT_SHUTTLE',  'Đưa đón sân bay'),
    ('PET_FRIENDLY',     'Cho phép thú cưng'),
    ('BEACH_ACCESS',     'Gần bãi biển'),
    ('KITCHEN',          'Bếp riêng'),
    ('BALCONY',          'Ban công'),
    ('FAMILY_ROOMS',     'Phòng gia đình'),
    ('NON_SMOKING',      'Không hút thuốc');

CREATE TABLE property_amenity (
    property_id  UUID        NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    amenity_code VARCHAR(50) NOT NULL REFERENCES amenity (code),
    PRIMARY KEY (property_id, amenity_code)
);

-- Dùng ở tuần 17 (media-service). Tạo sớm để schema ổn định, chưa có entity.
CREATE TABLE property_photo (
    property_id UUID NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    media_id    UUID NOT NULL,
    sort_order  INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (property_id, media_id)
); 
