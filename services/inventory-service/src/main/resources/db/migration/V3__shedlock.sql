-- Tuần 6: bảng khóa của ShedLock (cấu trúc do ShedLock quy định cho PostgreSQL).
-- Khi job định kỳ chạy, instance nào ghi được dòng khóa vào đây thì được chạy; instance khác bỏ qua lượt đó.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
