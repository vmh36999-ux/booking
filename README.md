# Booking Platform

Bộ khung monorepo microservice cho nền tảng đặt phòng kiểu Booking.com.
Java 21, Spring Boot 4.1.x, Spring Cloud 2025.1.x, Maven.

Lý do cho từng quyết định thiết kế nằm trong [DESIGN.md](DESIGN.md).

## Yêu cầu

- JDK 21
- Maven 3.9+ (hoặc sinh Maven Wrapper, xem bên dưới)
- Docker Desktop (bản có BuildKit và Compose v2)

## Chạy nhanh

```bash
# 1. Bật hạ tầng: Postgres, Redis, Kafka, Elasticsearch, MinIO, Mailpit
make infra-up

# 2. Build toàn bộ module (lần đầu sẽ tải dependency)
make build

# 3. Chạy một service ở máy (profile local: không cần token)
make run SVC=booking-service

# 4. Kiểm tra
curl localhost:8083/actuator/health
```

Chạy toàn bộ service trong Docker (cần khoảng 8 GB RAM cho Docker):

```bash
make apps-up
make apps-down
```

Không có `make`: xem các lệnh trong `Makefile` và chạy trực tiếp.

### Sinh Maven Wrapper (tùy chọn)

```bash
mvn -N wrapper:wrapper
```

Dockerfile dùng image Maven nên không cần wrapper để build image.

## Cổng và database

| Service | Cổng | Database |
|---|---|---|
| api-gateway | 8080 | không |
| auth-service | 8081 | auth_db |
| user-service | 8082 | user_db |
| booking-service | 8083 | booking_db |
| property-service | 8084 | property_db |
| search-service | 8085 | Elasticsearch |
| inventory-service | 8086 | inventory_db |
| pricing-service | 8087 | pricing_db |
| payment-service | 8088 | payment_db |
| review-service | 8089 | review_db |
| notification-service | 8090 | notification_db |
| media-service | 8091 | media_db (+ MinIO) |

Giao diện công cụ khi chạy hạ tầng:
- Mailpit (xem email test): http://localhost:8025
- MinIO console: http://localhost:9001 (minioadmin / minioadmin)
- Kafka UI: `docker compose -f infra/docker-compose.yml --profile tools up -d`, mở http://localhost:8099

## Cấu trúc thư mục

```
booking-platform/
├── pom.xml                      # parent + BOM: khóa phiên bản cho cả repo
├── Makefile
├── libs/
│   ├── event-contracts/         # envelope, topic, payload sự kiện (thuần Java)
│   └── common-lib/              # correlation-id filter (auto-configuration)
├── services/
│   └── <service>/
│       ├── pom.xml              # chỉ dependency service đó cần
│       ├── Dockerfile
│       └── src/main/
│           ├── java/com/bookingplatform/<pkg>/
│           │   ├── api/         # controller, dto, exception
│           │   ├── application/ # usecase (saga, job nếu có)
│           │   ├── domain/      # model, event, port
│           │   ├── infrastructure/  # persistence, messaging, client
│           │   └── config/      # SecurityConfig, ...
│           └── resources/
│               ├── application.yml
│               ├── application-local.yml
│               └── db/migration/V1__baseline.sql
├── infra/
│   ├── docker-compose.yml       # hạ tầng
│   ├── docker-compose.apps.yml  # các service
│   ├── postgres/init/           # tạo database cho từng service
│   ├── k8s/  terraform/
├── data-platform/               # cdc, ingestion, dbt (giai đoạn 5)
├── frontend/                    # web, mobile, extranet
├── docs/                        # adr, api
└── .github/workflows/ci.yml
```

## Cấu hình qua biến môi trường

Mọi giá trị kết nối có mặc định cho máy local và ghi đè được bằng biến môi trường:
`DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `KAFKA_BOOTSTRAP`,
`ES_URI`, `S3_ENDPOINT`, `MAIL_HOST`, `AUTH_JWKS_URI`, `INVENTORY_URL`, `PRICING_URL`, `PORT`.

## Việc cần làm trước khi build lần đầu

Bộ khung này chưa được build với Maven Central trong môi trường tạo ra nó.
Nếu `mvn package` báo không tìm thấy một artifact, hãy đối chiếu tên artifact với
`pom.xml` mà start.spring.io sinh ra cho Spring Boot 4.1.x. Các điểm nên kiểm tra đầu tiên
nằm ở mục "Những điểm chưa kiểm chứng" trong [DESIGN.md](DESIGN.md).

## Bước tiếp theo

1. Làm `auth-service` (JWT RS256 và `/.well-known/jwks.json`), sau đó `api-gateway`.
2. Thêm migration `V2__...sql` cho bảng nghiệp vụ đầu tiên.
3. Thêm outbox poller dùng chung khi service đầu tiên cần phát sự kiện.
4. Viết test tích hợp bằng Testcontainers cho `inventory-service`.
