# Vì sao thiết kế như thế này

Tài liệu giải thích lý do đằng sau từng phần của bộ khung. Mỗi mục trả lời ba câu hỏi:
làm gì, vì sao, và đổi giá nào.

---

## 1. Monorepo thay vì mỗi service một repo

**Làm gì:** toàn bộ service, thư viện, hạ tầng, tài liệu nằm trong một repo.

**Vì sao**
- Một người hoặc nhóm nhỏ làm 12 service: một repo nghĩa là một lần clone, một CI, một lần review.
- Đổi hợp đồng sự kiện trong `event-contracts` và sửa cả producer lẫn consumer nằm trong cùng một commit, nên không bao giờ có lúc hai bên lệch nhau.
- Nâng phiên bản Spring Boot một lần cho tất cả.

**Đổi giá nào**
- CI phải biết service nào thay đổi để không build lại tất cả (xem mục 13).
- Khi nhóm lớn lên, quyền truy cập theo repo khó hơn. Lúc đó có thể tách repo, và cấu trúc hiện tại vẫn cho phép tách dễ vì mỗi service đã tự đóng gói.

---

## 2. Một root `pom.xml` làm parent và BOM

**Làm gì:** root `pom.xml` kế thừa `spring-boot-starter-parent`, import BOM của Spring Cloud và AWS SDK, khai báo phiên bản của hai thư viện nội bộ. Các module con kế thừa từ root.

**Vì sao**
- Phiên bản Spring Boot, Spring Cloud, Java nằm ở **một chỗ**. Spring Boot 4.1 và Spring Cloud phải đi cùng nhau (Spring Cloud 2025.1.x), nếu để mỗi service tự chọn thì sớm muộn sẽ có service lệch bản và lỗi chỉ lộ ra lúc chạy.
- Root chỉ khai báo trong `dependencyManagement`, không tự thêm dependency vào module nào. Nhờ vậy mỗi service vẫn tự quyết định mình dùng gì.
- Spring Boot parent đã cấu hình sẵn compiler, `-parameters`, encoding, và plugin đóng gói jar.

**Đổi giá nào:** mọi service buộc dùng chung một phiên bản Spring Boot. Với một nền tảng do một nhóm làm, đây là ràng buộc có lợi.

---

## 3. Hai thư viện dùng chung, và cố ý để rất mỏng

### `event-contracts`

**Làm gì:** chứa `EventEnvelope`, tên topic, tên sự kiện và các record payload.

**Vì sao**
- Producer và consumer cần cùng hiểu một định dạng. Đặt hợp đồng ở một nơi, có kiểm tra khi biên dịch.
- Thuần Java record, **không phụ thuộc framework nào**, nên service nào cũng dùng được mà không kéo theo phiên bản Spring hay Jackson.
- Envelope có `eventId` (cho idempotent consumer), `correlationId` (truy vết xuyên service), `version` (tiến hóa hợp đồng).

### `common-lib`

**Làm gì:** hiện chỉ có `CorrelationIdFilter` và một auto-configuration đăng ký nó.

**Vì sao mỏng:** thư viện dùng chung là chỗ dễ biến thành "monolith phân tán". Nếu nhét entity, repository, logic nghiệp vụ vào đây thì mọi service bị buộc chặt với nhau, mỗi lần sửa phải build và deploy lại tất cả.
Quy tắc: chỉ đưa vào thứ thật sự cắt ngang (log, truy vết, định dạng lỗi). Logic nghiệp vụ thì **chép** còn hơn là chia sẻ.

Các dependency của `common-lib` đặt scope `provided`: thư viện không ép phiên bản framework lên service, service tự mang framework của mình.

---

## 4. Mỗi service có `pom.xml` riêng với dependency tối thiểu

**Làm gì:** pom của từng service chỉ khai báo những gì service đó cần. Ví dụ `search-service` có Elasticsearch nhưng không có JPA; `notification-service` có mail và Thymeleaf; `api-gateway` không có database.

**Vì sao**
- Mỗi dependency kéo theo auto-configuration. Có `starter-data-jpa` mà không có database thì service không khởi động được; có Redis mà không dùng thì thêm một health check có thể báo DOWN.
- Ít dependency thì khởi động nhanh hơn, ít RAM hơn (rất quan trọng khi chạy 12 JVM trên một máy), bề mặt tấn công nhỏ hơn.
- Nhìn vào pom là biết service dựa vào hạ tầng nào.

**Chi tiết đáng chú ý**
- `<finalName>app</finalName>`: jar luôn tên `app.jar`, Dockerfile không phải đoán `booking-service-0.0.1-SNAPSHOT.jar`.
- Test dependency dùng starter theo công nghệ của Spring Boot 4 (`webmvc-test`, `data-jpa-test`, `security-test`), kèm Testcontainers để test với Postgres và Kafka thật thay vì mock.
- Starter `web` đã đổi tên thành `webmvc` trong Boot 4. OAuth2 resource server nằm dưới tên `security-oauth2-resource-server`.

---

## 5. Cấu trúc package theo hexagonal (ports and adapters)

```
api/             controller, dto, exception      <- lớp vào (HTTP)
application/     usecase, saga, job              <- điều phối nghiệp vụ
domain/          model, event, port              <- lõi nghiệp vụ, không dính framework
infrastructure/  persistence, messaging, client  <- lớp ra (DB, Kafka, HTTP client)
config/          cấu hình Spring
```

**Vì sao**
- `domain` không import Spring hay JPA annotation vượt quá mức cần thiết, nên máy trạng thái của booking, quy tắc giá, chính sách hoàn tiền test được bằng unit test thuần, chạy trong mili-giây.
- `domain/port` là interface (ví dụ `InventoryPort`). `infrastructure/client` cài đặt bằng HTTP. Đổi cách gọi (HTTP sang gRPC, hay mock khi test) không đụng tới nghiệp vụ.
- Các lớp có tên rõ ràng giúp người mới biết đặt code ở đâu, và giúp review: controller chứa logic nghiệp vụ là dấu hiệu sai.
- Thư mục `application/saga` và `application/job` chỉ có ở service cần (booking, inventory, payment).

**Đổi giá nào:** nhiều thư mục và interface hơn cho service CRUD đơn giản. Với `user-service` hay `review-service`, có thể gộp bớt lớp cho gọn, miễn là `domain` vẫn sạch.

`api-gateway` không dùng cấu trúc này vì nó chỉ định tuyến, không có nghiệp vụ.

---

## 6. Database per service, Flyway quản lý schema

**Làm gì**
- `infra/postgres/init/01-create-databases.sql` tạo một database cho mỗi service.
- `spring.jpa.hibernate.ddl-auto: validate`: Hibernate chỉ kiểm tra, không tự sửa schema.
- Schema thay đổi bằng migration Flyway trong `db/migration`.

**Vì sao**
- Mỗi service sở hữu dữ liệu của mình. Service khác không được `SELECT` trực tiếp, chỉ gọi API hoặc nghe sự kiện. Nhờ vậy đổi cấu trúc bảng không làm hỏng service khác, và có thể scale hay đổi công nghệ lưu trữ riêng từng service.
- Dùng chung **một server** Postgres nhưng **nhiều database** là cách tiết kiệm cho môi trường dev, mà vẫn giữ ranh giới dữ liệu. Production có thể tách thành nhiều server không cần sửa code, chỉ đổi `DB_HOST`.
- `ddl-auto: update` nguy hiểm: tự đổi schema ở production, không có lịch sử, không rollback. Flyway cho lịch sử thay đổi có phiên bản, review được trong pull request.
- `validate` bắt lỗi sớm: nếu entity lệch với schema, ứng dụng không khởi động.

---

## 7. `V1__baseline.sql` có sẵn bảng `outbox` và `processed_event`

**Vì sao:** hai mẫu này giải quyết hai vấn đề căn bản của hệ thống dựa trên sự kiện.

- **Outbox:** ghi dữ liệu nghiệp vụ và ghi sự kiện cần phát vào **cùng một transaction**. Nếu phát thẳng lên Kafka sau khi commit, process chết giữa chừng sẽ mất sự kiện; nếu phát trước, lỡ rollback sẽ có sự kiện "ma". Outbox loại bỏ khoảng hở đó.
- **processed_event:** Kafka giao hàng "ít nhất một lần", nên consumer có thể nhận trùng. Ghi `eventId` đã xử lý để bỏ qua bản trùng.

Cột `payload` là `JSONB` để sau này truy vấn và debug dễ hơn. Index một phần (`WHERE published_at IS NULL`) giúp poller chỉ quét các dòng chưa gửi, nên bảng lớn lên vẫn nhanh.

Service không có database (`api-gateway`, `search-service`) không có hai bảng này. Search dùng upsert idempotent theo `propertyId` thay cho bảng dedupe.

---

## 8. Cấu hình Kafka: chuỗi ký tự, không dùng serializer theo kiểu Java

**Làm gì:** producer và consumer đều dùng `StringSerializer`/`StringDeserializer`. `acks: all`, `enable-auto-commit: false`, `ack-mode: record`.

**Vì sao**
- Gửi JSON dạng chuỗi (envelope đã có `type` và `version`) thay vì serialize cả tên class Java. Như vậy producer và consumer **không cần cùng một class**, và service viết bằng ngôn ngữ khác vẫn đọc được. Đây là điều kiện để các service tiến hóa độc lập.
- `acks: all`: không mất message khi broker lỗi, đánh đổi bằng một chút độ trễ. Với đặt phòng và thanh toán, tính đúng quan trọng hơn tốc độ.
- Tắt auto-commit và dùng `ack-mode: record`: chỉ xác nhận offset sau khi xử lý xong. Xử lý lỗi giữa chừng thì sẽ nhận lại, và `processed_event` đảm bảo không làm hai lần.
- `group-id` lấy từ tên ứng dụng: mỗi service là một consumer group riêng, nên mỗi service đều nhận đủ sự kiện.

---

## 9. Bảo mật: mỗi service tự xác thực JWT

**Làm gì:** mọi service là OAuth2 resource server, kiểm tra JWT bằng JWKS của auth-service. Gateway cũng kiểm tra. Mỗi service có `SecurityConfig` riêng, cho phép công khai các đường dẫn health và những đường dẫn nghiệp vụ công khai (ví dụ `/api/v1/search/**`).

**Vì sao**
- Không tin vào header như `X-User-Id` chỉ vì nó đến từ gateway. Nếu một service bị lộ port ra ngoài hoặc một service nội bộ bị chiếm quyền, header giả mạo sẽ vượt qua. Kiểm tra JWT ở từng service (phòng thủ nhiều lớp) tốn thêm vài mili-giây nhưng an toàn hơn nhiều.
- Dùng `jwk-set-uri` thay vì `issuer-uri`: `issuer-uri` bắt buộc auth-service có endpoint discovery chuẩn OIDC, còn JWKS thì auth-service tự viết chỉ cần một endpoint.
- **Profile `local`** đặt `app.security.permit-all=true`. Lý do thực tế: khi bạn đang làm `booking-service` mà chưa có `auth-service`, vẫn gọi được API bằng `curl`. Mặc định là `false`, nên không thể vô tình tắt bảo mật ở production nếu không bật profile `local`.

**Lưu ý:** `/actuator/prometheus` đang cho truy cập công khai để Prometheus scrape. Trên production hãy chặn ở tầng mạng (NetworkPolicy, ingress) hoặc đặt actuator ở cổng quản trị riêng.

---

## 10. Cấu hình qua biến môi trường, mặc định cho máy local

**Làm gì:** trong `application.yml` mọi địa chỉ đều có dạng `${DB_HOST:localhost}`.

**Vì sao**
- Cùng một image chạy ở local, staging, production, chỉ khác biến môi trường (nguyên tắc 12-factor). Không build lại image cho từng môi trường.
- Mặc định `localhost` nghĩa là clone về là chạy được ngay khi đã bật `make infra-up`.
- Mật khẩu mặc định (`platform`) chỉ dành cho dev. Production phải đặt qua secret của Kubernetes hoặc secret manager, không ghi vào file.

Địa chỉ gọi giữa các service (`INVENTORY_URL`, `PRICING_URL`...) cũng là biến môi trường: trong Docker là tên container, trong Kubernetes là tên Service.

---

## 11. Dockerfile

```
build context = thư mục gốc repo
docker build -f services/<tên>/Dockerfile .
```

### Vì sao context là thư mục gốc
Service cần `libs/` và root `pom.xml` để Maven tìm được parent và thư viện nội bộ. Nếu context chỉ là thư mục service thì không có những thứ này. Đây là điều đặc thù của monorepo Maven.

### Vì sao copy toàn bộ `pom.xml` trước, rồi mới copy mã nguồn
Docker cache theo từng lớp. Lớp copy pom ít thay đổi, mã nguồn thay đổi liên tục. Đặt pom trước thì khi chỉ sửa code, các lớp trước vẫn dùng cache. Ở multi-module Maven phải copy **đủ** pom của mọi module trong `<modules>`, vì Maven đọc cả reactor kể cả khi chỉ build một module bằng `-pl`.

### Vì sao `--mount=type=cache,target=/root/.m2`
Thư mục `.m2` (hàng trăm MB dependency) được giữ giữa các lần build bằng BuildKit cache. Sửa một dòng code không phải tải lại toàn bộ dependency. Cách này đơn giản hơn `mvn dependency:go-offline`, vốn hay lỗi với module anh em trong reactor.

### Vì sao build bằng `-pl services/<tên> -am`
Chỉ build service đó và những module nó phụ thuộc (`-am` = also make: `event-contracts`, `common-lib`). Không build 11 service còn lại.

### Vì sao hai giai đoạn (multi-stage)
- Giai đoạn build có JDK, Maven, mã nguồn (hơn 700 MB).
- Giai đoạn runtime chỉ có JRE và `app.jar`. Image nhỏ hơn, ít thành phần hơn nên ít lỗ hổng hơn, kéo về nhanh hơn.

### Các chi tiết nhỏ
- **User không phải root:** nếu ứng dụng bị khai thác, kẻ tấn công không có quyền root trong container.
- **`-XX:MaxRAMPercentage=70`:** JVM tính heap theo giới hạn RAM của container thay vì RAM của máy chủ. Nếu không, JVM có thể chiếm quá giới hạn và bị OOM-kill.
- **`-XX:+ExitOnOutOfMemoryError`:** hết bộ nhớ thì thoát hẳn để orchestrator khởi động lại, thay vì sống dở chết dở.
- **Không có `HEALTHCHECK` bằng curl:** image JRE không có curl. Kubernetes dùng probe HTTP trực tiếp vào `/actuator/health/liveness` và `/readiness`. Đã bật `management.endpoint.health.probes.enabled`.
- **`server.shutdown: graceful`:** khi nhận tín hiệu dừng, service hoàn tất request đang chạy (tối đa 20 giây) rồi mới tắt, tránh mất request lúc triển khai bản mới.

### Có thể cải tiến sau
Dùng `java -Djarmode=tools -jar app.jar extract --layers` để tách jar thành các lớp (dependency, application). Khi đó sửa code chỉ đẩy lên lớp "application" vài trăm KB. Tôi chưa đưa vào mặc định để giữ Dockerfile đơn giản và dễ debug.

---

## 12. Hạ tầng tách hai file compose

- `docker-compose.yml`: hạ tầng (Postgres, Redis, Kafka, Elasticsearch, MinIO, Mailpit).
- `docker-compose.apps.yml`: 12 service.

**Vì sao tách:** khi phát triển một service, bạn chạy hạ tầng bằng Docker và chạy service trong IDE để debug và hot reload. Chỉ khi muốn thử cả hệ thống mới bật file thứ hai. Gộp chung thì mỗi lần bật hạ tầng là build 12 image.

**Chi tiết**
- Kafka có **hai listener**: `INTERNAL` (`kafka:29092`) cho container và `EXTERNAL` (`localhost:9092`) cho ứng dụng chạy ngoài Docker. Nếu chỉ có một listener thì một trong hai phía sẽ không kết nối được. Đây là lỗi phổ biến nhất khi dựng Kafka local.
- Postgres có `healthcheck` và các service dùng `depends_on: condition: service_healthy`, để service không khởi động khi database chưa sẵn sàng.
- Mỗi service giới hạn `mem_limit: 512m` để 12 JVM không ăn hết RAM máy.
- Mailpit bắt mọi email gửi ra, không bao giờ gửi thật, an toàn khi test notification.
- Kafka UI nằm sau profile `tools`, chỉ chạy khi cần.
- Elasticsearch tắt bảo mật (`xpack.security.enabled=false`) chỉ để tiện dev. Production phải bật.

---

## 13. CI

`mvn verify` trên Java 21, có cache Maven. Test tích hợp dùng Testcontainers nên cần Docker, runner của GitHub có sẵn.

**Bước nâng cấp:** khi repo lớn, thêm lọc theo đường dẫn (`paths:` hoặc action `dorny/paths-filter`) để chỉ build service có thay đổi, kèm các service phụ thuộc vào `libs/`.

---

## 14. Những thứ cố ý chưa đưa vào

| Thành phần | Vì sao chưa có | Khi nào thêm |
|---|---|---|
| ShedLock | Cần khi chạy nhiều instance cùng một job định kỳ | Khi triển khai booking, inventory với job timeout |
| springdoc-openapi | Phiên bản phải khớp Spring Boot 4, nên chọn lúc cần | Khi có API đầu tiên |
| Eureka, Spring Cloud Config | Kubernetes đã có service discovery và ConfigMap/Secret | Chỉ nếu không chạy trên Kubernetes |
| OpenFeign | Spring 7 có `RestClient` và `@HttpExchange` sẵn | Không cần |
| MapStruct, Lombok | Dùng record và constructor thường cho đơn giản | Khi mapping nhiều làm code rườm rà |
| Controller mẫu, entity mẫu | Mỗi service sẽ khác, ví dụ mẫu dễ bị sao chép sai chỗ | Khi bạn làm service đầu tiên |
| Maven Wrapper | Cần mạng để sinh, và image Maven đã đủ cho Docker | `mvn -N wrapper:wrapper` |

---

## 15. Những điểm chưa kiểm chứng

Bộ khung này **chưa được build** với Maven Central trong môi trường tạo ra nó (không có truy cập mạng tới repo Maven). Những gì đã kiểm tra:

- Toàn bộ `pom.xml` và file YAML đúng cú pháp.
- `event-contracts` biên dịch được với JDK 21.
- Tên starter chính của Spring Boot 4 (`webmvc`, `flyway`, `kafka`, `mail`, `security-oauth2-resource-server`, `data-jpa-test`, `security-test`) khớp với hướng dẫn nâng cấp của Spring Boot.

Những điểm **nên kiểm tra đầu tiên** nếu `mvn package` báo lỗi:

1. **Tên artifact Testcontainers** (`testcontainers-junit-jupiter`, `testcontainers-postgresql`, `testcontainers-kafka`). Từ Testcontainers 2.x các module đổi tên có tiền tố `testcontainers-`. Nếu Boot 4.1 của bạn dùng bản 1.x thì đổi lại thành `junit-jupiter`, `postgresql`, `kafka`.
2. **`spring-boot-starter-data-elasticsearch`** (search-service) và **`micrometer-registry-prometheus`** (mọi service): hãy đối chiếu với start.spring.io.
3. **Phiên bản patch của Spring Boot** (`4.1.0` trong root pom): đổi sang 4.1.x mới nhất. Spring Cloud giữ `2025.1.2` trở lên cho 4.1.
4. **Phiên bản AWS SDK** (`aws-sdk.version` trong root pom): `2.31.0` chỉ là mốc an toàn đã tồn tại, nên nâng lên bản mới.
5. **Tên thuộc tính route của Spring Cloud Gateway** (`spring.cloud.gateway.server.webmvc.routes`): đối chiếu tài liệu Spring Cloud Gateway Server WebMVC của bản bạn dùng.
6. **`@AutoConfiguration`, `OncePerRequestFilter`** trong `common-lib`: nếu Boot 4 đổi package, lỗi sẽ hiện ở bước biên dịch `common-lib`.
7. **Image Elasticsearch 9.1.0** và client của Spring Data Elasticsearch trong Boot 4.1: client và server phải cùng dòng chính.

Cách nhanh nhất để đối chiếu: tạo một project trống trên start.spring.io với Boot 4.1.x, thêm đúng các starter của từng service, rồi so với `pom.xml` ở đây.
