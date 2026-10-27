.PHONY: infra-up infra-down build apps-up apps-down run clean

COMPOSE_INFRA = docker compose -f infra/docker-compose.yml
COMPOSE_ALL   = docker compose -f infra/docker-compose.yml -f infra/docker-compose.apps.yml

infra-up:      ## Bật hạ tầng (Postgres, Redis, Kafka, ES, MinIO, Mailpit)
	$(COMPOSE_INFRA) up -d

infra-down:    ## Tắt hạ tầng (giữ dữ liệu)
	$(COMPOSE_INFRA) down

build:         ## Build toàn bộ module, bỏ test
	mvn -B -ntp -DskipTests package

test:          ## Chạy toàn bộ test
	mvn -B -ntp verify

run:           ## Chạy 1 service ở máy: make run SVC=booking-service
	SPRING_PROFILES_ACTIVE=local mvn -pl services/$(SVC) -am spring-boot:run

apps-up:       ## Build và chạy toàn bộ service trong Docker
	$(COMPOSE_ALL) up -d --build

apps-down:
	$(COMPOSE_ALL) down

clean:
	mvn -B -ntp clean
