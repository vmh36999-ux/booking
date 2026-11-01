# ADR-0001: Booking Platform Architecture

- **Status:** Accepted
- **Date:** 2026-10-27

## 1. Context

The Booking Platform is a hotel booking system designed to separate
business capabilities into independently maintainable services.

The system needs to support booking, inventory management, pricing,
payments, property information, searching, authentication, and
notifications. It must also handle concurrent booking requests,
reliable event delivery, and failures across service boundaries.

## 2. Decision

### 2.1. Monorepo and Maven Multi-Module

Use a single Git repository with a Maven multi-module structure.

The root `pom.xml` manages common build configuration and module
relationships. Each service remains a separate application that can
be built, run, and deployed independently.

### 2.2. Microservices and API Gateway

Separate the application into domain-focused services, including:

- API Gateway
- Auth and User
- Booking and Inventory
- Property, Search, and Pricing
- Payment
- Review, Notification, and Media

The API Gateway provides a common entry point for client requests and
routes requests to the appropriate backend service.

### 2.3. Shared Libraries

Use `libs/event-contracts` for shared event definitions and
`libs/common-lib` for genuinely reusable technical utilities.

Shared libraries must not become a place to put all business logic.
Each service owns its business rules and internal implementation.

### 2.4. Data Ownership

Use PostgreSQL for relational business data. Each service should
own its data and access it through its own application logic instead
of querying another service's tables directly.

Redis, Elasticsearch, and MinIO serve supporting infrastructure
needs such as caching, search indexing, and media storage.

### 2.5. Asynchronous Communication

Use Kafka for asynchronous communication between services.

Use versioned or backward-compatible event contracts where possible.
Consumers must be designed to handle duplicate event delivery safely.

### 2.6. Reliable Event Publishing

For booking-related database changes, use the transactional outbox
pattern: persist the business change and its outbox event in the same
database transaction.

Publish outbox events to Kafka and mark them as published after
successful delivery. Because delivery can be repeated, consumers
must process events idempotently.

The `processed_event` table can be used to record processed event
identifiers and help prevent duplicate business effects.

### 2.7. Booking and Payment Consistency

Treat a booking that spans inventory and payment services as a
distributed workflow. Use a Saga-style process with appropriate
compensating actions when a later step fails.

Enforce inventory availability and booking constraints in the
authoritative data store. Do not rely only on search results or
client-side checks to prevent double booking.

### 2.8. Local Development Infrastructure

Use Docker Compose to run supporting infrastructure locally,
including PostgreSQL, Redis, Kafka, Elasticsearch, MinIO, and Mailpit.

Keep infrastructure configuration separate from application source
code under `infra/`.

## 3. Consequences

### Benefits

- Services have clearer business boundaries.
- Services can be maintained and deployed independently.
- Kafka reduces direct runtime dependencies between services.
- The outbox pattern improves reliability between database
  transactions and event publishing.
- Docker Compose provides a repeatable local development environment.

### Trade-offs

- Distributed workflows and eventual consistency increase complexity.
- Event consumers need idempotency and retry/error-handling strategies.
- Multiple services and infrastructure components require more
  resources and operational effort than a monolith.
- Shared libraries must be kept small to avoid unnecessary coupling.
- Search indexes may lag behind authoritative transactional data.

## 4. Alternatives Considered

### Modular Monolith

A modular monolith would reduce deployment and infrastructure
complexity, but it would not provide the same service-level
deployment boundaries.

### Separate Git Repository for Every Service

Separate repositories would isolate source code further, but a
monorepo simplifies coordinated changes to shared contracts,
libraries, and build configuration during this project's development.

### Direct Database Access Between Services

Directly querying another service's database would tightly couple
services to its schema and ownership. Service-to-service APIs and
events are preferred instead.

## 5. Notes

This ADR documents the intended architecture. Individual
infrastructure settings, service integrations, failure handling,
and deployment behavior must still be verified through implementation
and testing.
