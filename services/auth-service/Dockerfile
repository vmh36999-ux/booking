# syntax=docker/dockerfile:1.7
# Build context = THƯ MỤC GỐC monorepo (vì service cần libs/ và root pom.xml):
#   docker build -f services/auth-service/Dockerfile -t auth-service .

############################ Giai đoạn 1: build ############################
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy toàn bộ pom trước: Maven cần thấy đủ module của reactor
COPY pom.xml pom.xml
COPY libs/event-contracts/pom.xml libs/event-contracts/pom.xml
COPY libs/common-lib/pom.xml libs/common-lib/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/user-service/pom.xml services/user-service/pom.xml
COPY services/booking-service/pom.xml services/booking-service/pom.xml
COPY services/property-service/pom.xml services/property-service/pom.xml
COPY services/search-service/pom.xml services/search-service/pom.xml
COPY services/inventory-service/pom.xml services/inventory-service/pom.xml
COPY services/pricing-service/pom.xml services/pricing-service/pom.xml
COPY services/payment-service/pom.xml services/payment-service/pom.xml
COPY services/review-service/pom.xml services/review-service/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml
COPY services/media-service/pom.xml services/media-service/pom.xml

# Chỉ copy mã nguồn cần cho service này (libs + chính nó)
COPY libs libs
COPY services/auth-service services/auth-service

# Cache thư mục ~/.m2 giữa các lần build (cần BuildKit, bật mặc định ở Docker mới)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -q -pl services/auth-service -am -DskipTests package

############################ Giai đoạn 2: runtime ##########################
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Chạy bằng user thường, không dùng root
RUN groupadd --system app && useradd --system --gid app --no-create-home app
USER app

COPY --from=build --chown=app:app /workspace/services/auth-service/target/app.jar app.jar

# JVM tự nhận giới hạn RAM của container
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENV PORT=8081
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
