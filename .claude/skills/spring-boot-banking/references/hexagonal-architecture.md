# Hexagonal Architecture & Service Composition

Reference loaded on demand by `spring-boot-banking` skill. Cover service-level architecture, microservice composition, and inter-service communication.

## Layered Layout (per service)

Every microservice in `backend/` follows the same internal layout:

```
backend/<service>/
└── src/main/java/com/bank/<service>/
    ├── <Service>Application.java
    ├── domain/              # Pure domain — no Spring, no JPA
    │   ├── model/           # Entities, value objects, domain events
    │   ├── service/         # Domain services (when behavior spans entities)
    │   └── exception/       # Sealed DomainException hierarchy
    ├── application/         # Use cases / orchestrations
    │   ├── usecase/         # @Service @Transactional, thin
    │   ├── saga/            # Saga coordinators
    │   └── port/            # Port interfaces (DIP)
    │       ├── in/          # Inbound (controllers call these)
    │       └── out/         # Outbound (repos, event publishers — interfaces only)
    ├── infrastructure/      # Adapters implementing application/port
    │   ├── persistence/     # JPA entities, Spring Data repos
    │   ├── messaging/       # Kafka producers/consumers
    │   ├── client/          # REST/gRPC clients
    │   └── config/          # Spring @Configuration classes
    └── interfaces/
        ├── rest/            # @RestController + DTO + MapStruct mappers
        └── events/          # Kafka @KafkaListener
```

**Layering rules:**
- `domain/` depends on nothing in the project (only stdlib + small VO libs)
- `application/` depends on `domain/` only
- `infrastructure/` depends on `application/port/out` interfaces (implements them)
- `interfaces/` depends on `application/port/in` (calls them)
- **No JPA in `domain/` or `application/`** — repos are interfaces in `application/port/out`, JPA impl in `infrastructure/persistence/`
- **No controllers in `application/` or `domain/`**

## Microservice Composition

### Service Discovery & Routing
- **API Gateway**: Spring Cloud Gateway for routing, auth enforcement, rate-limit
- **Service Discovery**: Eureka (Spring Cloud) or Kubernetes Service DNS
- **Centralized Config**: Spring Cloud Config + Vault for secrets

### Resilience
**Resilience4j** everywhere on outbound calls:
- **Circuit breaker** on outgoing HTTP / DB calls
- **Retry** with exponential backoff + jitter
- **Bulkhead** to isolate noisy-neighbor effects
- **Time limiter** on slow calls (default 800ms for sync inter-service)

## Communication Patterns

### Synchronous (HTTP)
- Use for **queries only** (read paths)
- Tooling: Feign or `WebClient` + Resilience4j
- **No chained synchronous calls across > 2 services** — that's a distributed monolith

### Asynchronous (Kafka)
- Use for **events, commands, sagas** (write paths)
- Outbox pattern for reliable publishing — see [idempotency-saga-outbox.md](idempotency-saga-outbox.md)
- At-least-once delivery → consumers must be idempotent
- Schema registry (Confluent or Apicurio) for Avro/Protobuf payloads

### Anti-Pattern: Sync Chain
```
client → service-A → service-B → service-C → service-D
```
This couples deploy cycles, cascades failures, and inflates latency. Refactor to:
- Event-driven (A publishes event, B/C/D consume independently)
- Or consolidate B/C/D back into A if the boundaries were wrong

## Bounded Contexts (DDD)

Service boundaries align with **bounded contexts**, not technical layers:

| ✅ Good | ❌ Bad |
|---|---|
| `transfer-service`, `account-service`, `ledger-service` | `controller-service`, `repo-service`, `kafka-service` |
| `payment-context`, `customer-context` | `read-service`, `write-service` |

Each service owns its DB schema. Other services read via API or events — **never** direct DB access.

## When to Use This Reference

- Designing the package layout for a new service
- Reviewing a PR that adds a Feign client (sync) — verify it's a query, not a write chain
- Refactoring a service that has grown too many responsibilities
- Diagnosing tight coupling between services
