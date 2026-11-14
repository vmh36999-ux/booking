# Demo mốc M1: đăng ký, đăng nhập, host tạo khách sạn, qua gateway

Kịch bản 10 phút cho cuối giai đoạn 1 (tuần 4). Mọi lệnh chạy ở PowerShell, thư mục gốc repo.
Dùng chữ **không dấu** trong JSON gửi từ PowerShell 5 để tránh lỗi mã hóa.

## 0. Chuẩn bị

Hạ tầng đang chạy (`docker compose -f infra/docker-compose.yml up -d`) và 4 service chạy ở 4 cửa sổ riêng, **không** dùng profile `local`:

```powershell
mvn -B -ntp -q -pl libs/event-contracts,libs/common-lib install -DskipTests
mvn -B -ntp -q -pl services/auth-service,services/user-service,services/property-service,services/api-gateway -am -DskipTests install

mvn -pl services/auth-service spring-boot:run       # cửa sổ 1, cổng 8081
mvn -pl services/user-service spring-boot:run       # cửa sổ 2, cổng 8082
mvn -pl services/property-service spring-boot:run   # cửa sổ 3, cổng 8084
mvn -pl services/api-gateway spring-boot:run        # cửa sổ 4, cổng 8080
```

Cửa sổ thứ năm để gõ lệnh demo:

```powershell
$gw = "http://localhost:8080"
function Login($email) {
  $b = @{ email = $email; password = "Passw0rd!2026" } | ConvertTo-Json
  (Invoke-RestMethod -Method Post -Uri "$gw/api/v1/auth/login" -ContentType "application/json" -Body $b).accessToken
}
```

## 1. Hai host đăng ký và đăng nhập

```powershell
foreach ($e in "host.a@example.com", "host.b@example.com") {
  $b = @{ email = $e; password = "Passw0rd!2026"; accountType = "HOST" } | ConvertTo-Json
  Invoke-RestMethod -Method Post -Uri "$gw/api/v1/auth/register" -ContentType "application/json" -Body $b
}
$tokenA = Login "host.a@example.com"
$tokenB = Login "host.b@example.com"
```

**Điểm cần chỉ ra:** phản hồi đăng ký có `roles: HOST`; user-service tự tạo hồ sơ cho cả hai (tuần 3).

## 2. Host A tạo khách sạn, thêm phòng, đăng

```powershell
$hA = @{ Authorization = "Bearer $tokenA" }
$prop = @{ name = "Sunrise Da Nang Hotel"; description = "Khach san gan bien"; city = "Da Nang"; country = "VN";
           address = "1 Vo Nguyen Giap"; latitude = 16.0544; longitude = 108.2022; currency = "VND";
           amenities = @("WIFI", "POOL", "BREAKFAST") } | ConvertTo-Json
$p = Invoke-RestMethod -Method Post -Uri "$gw/api/v1/host/properties" -Headers $hA -ContentType "application/json" -Body $prop
$p.status        # DRAFT

$room = @{ name = "Deluxe Twin"; maxGuests = 2; bedConfig = "2 giuong don"; totalRooms = 10; basePriceMinor = 1200000 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$gw/api/v1/host/properties/$($p.id)/room-types" -Headers $hA -ContentType "application/json" -Body $room

(Invoke-RestMethod -Method Post -Uri "$gw/api/v1/host/properties/$($p.id)/publish" -Headers $hA).status   # PUBLISHED
```

## 3. Khách (không đăng nhập) xem được khách sạn

```powershell
Invoke-RestMethod -Uri "$gw/api/v1/properties/$($p.id)"
```

**Điểm cần chỉ ra:** không có `hostId`, không có trạng thái. Trước khi đăng thì cùng lệnh này trả 404.

## 4. Host B không sửa được khách sạn của host A

```powershell
$hB = @{ Authorization = "Bearer $tokenB" }
$hack = @{ name = "Chiem quyen"; city = "Da Nang"; country = "VN"; latitude = 16.05; longitude = 108.2; currency = "VND" } | ConvertTo-Json
try { Invoke-RestMethod -Method Put -Uri "$gw/api/v1/host/properties/$($p.id)" -Headers $hB -ContentType "application/json" -Body $hack }
catch { [int]$_.Exception.Response.StatusCode; $_.ErrorDetails.Message }
```

**Phải thấy:** `403` và `NOT_PROPERTY_OWNER`.

## 5. Sự kiện xuất hiện trên Kafka

```powershell
docker compose -f infra/docker-compose.yml exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic property.events --from-beginning --max-messages 2
```

**Phải thấy** hai message: `RoomTypeCreated` và `PropertyPublished` (kèm tên khách sạn, loại phòng, hostId). Ctrl+C nếu lệnh không tự thoát.

## 6. Mười nghìn khách sạn

Tắt property-service ở cửa sổ 3 (Ctrl+C), rồi nạp dữ liệu mẫu:

```powershell
mvn -pl services/property-service spring-boot:run "-Dspring-boot.run.profiles=seed"
docker compose -f infra/docker-compose.yml exec postgres psql -U platform -d property_db -c "select count(*) as so_khach_san from property;"
docker compose -f infra/docker-compose.yml exec postgres psql -U platform -d property_db -c "select city, count(*) from property group by city order by city;"
```

**Phải thấy:** `10001` (10.000 khách sạn mẫu cộng 1 khách sạn vừa tạo tay), và 20 thành phố, mỗi thành phố khoảng 500 khách sạn.

## 7. (Tùy chọn) Admin khóa khách sạn

Cần một tài khoản ADMIN. Vai trò này không đăng ký được qua API, nên cấp bằng SQL:

```powershell
$b = @{ email = "admin.demo@example.com"; password = "Passw0rd!2026"; accountType = "GUEST" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$gw/api/v1/auth/register" -ContentType "application/json" -Body $b
docker compose -f infra/docker-compose.yml exec postgres psql -U platform -d auth_db -c "insert into user_role (user_id, role) select user_id, 'ADMIN' from credential where email = 'admin.demo@example.com';"

$tokenAdmin = Login "admin.demo@example.com"
Invoke-RestMethod -Method Post -Uri "$gw/api/v1/admin/properties/$($p.id)/suspend" -Headers @{ Authorization = "Bearer $tokenAdmin" }
```

Khách sạn chuyển `SUSPENDED`, khách xem lại thì 404, host đăng lại thì bị 409.

## 8. Gắn thẻ

```powershell
git tag -a v0.1.0 -m "Giai doan 1: dinh danh va danh muc"
git push origin v0.1.0
```
