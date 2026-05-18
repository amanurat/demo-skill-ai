---
name: kafka-spring-patterns
description: Spring Kafka 3.x patterns for banking event-driven services — idempotent producers, transactional outbox dispatcher, consumer offset/error handling, DLQ + retry topics, schema evolution (Avro/Protobuf), exactly-once semantics, banking topic conventions, anti-patterns. Use when implementing or reviewing Kafka producers/consumers, outbox dispatchers, or event-driven sagas.
---

# Kafka Spring Patterns — Event-Driven Banking Skill

Reusable patterns for `spring-kafka` 3.x on Java 21 + Spring Boot 3.x in banking/fintech. Loaded by `banking-backend-dev` and `banking-reviewer` agents. Pairs with [`spring-boot-banking`](../spring-boot-banking/SKILL.md) (Outbox + Saga).

## When to Use

- Implementing a new producer (domain event, audit event, outbox dispatch)
- Implementing a new consumer (saga step, projection, downstream side-effect)
- Wiring an Outbox dispatcher (DB → Kafka) — see also [`idempotency-saga-outbox.md`](../spring-boot-banking/references/idempotency-saga-outbox.md)
- Adding DLQ + retry topics
- Reviewing a PR that touches `@KafkaListener` / `KafkaTemplate` / `producerFactory` / `consumerFactory`
- Designing a new topic (naming, partitions, retention, schema)
- Diagnosing lag, duplicate-delivery, or out-of-order issues

## Quick Reference

| Need | Where to Look |
|---|---|
| Producer config (idempotence, acks, transactions, partitioning, headers) | [references/producer-patterns.md](references/producer-patterns.md) |
| Consumer config (groups, offset, concurrency, error handlers, manual ack) | [references/consumer-patterns.md](references/consumer-patterns.md) |
| Outbox → Kafka dispatcher (poll, publish, mark sent, retries) | [references/outbox-dispatcher.md](references/outbox-dispatcher.md) |
| Schema Registry + Avro/Protobuf evolution rules | [references/schema-registry-avro.md](references/schema-registry-avro.md) |
| Anti-patterns to flag in review | [references/kafka-anti-patterns.md](references/kafka-anti-patterns.md) |

---

## Banking Hard Rules (inline — auto-fail in review)

These flip a review verdict to `changes_requested` immediately. See [references/kafka-anti-patterns.md](references/kafka-anti-patterns.md) for "why" and "how to detect".

- **Producer must be idempotent**: `enable.idempotence=true`, `acks=all`, `max.in.flight.requests.per.connection ≤ 5`
- **No `auto.commit`** on consumers — always `enable.auto.commit=false` + manual or batch ack
- **DLQ is mandatory** for every consumer that processes financial / state-changing events (`DeadLetterPublishingRecoverer`)
- **No secrets in topic names or message keys / values** (account masking required for any PAN-like field)
- **Schema must be registered** in Schema Registry before producing — no `String` JSON blobs for financial events
- **Message key must be set** for ordering guarantees (e.g., `accountId`, `transferId`) — never `null` for ordered domains
- **Consumer is idempotent** — process the same message twice without side-effect divergence (use inbox table or natural deduplication on `eventId`)
- **Outbox is the only path** from DB state-change → Kafka (no `kafkaTemplate.send()` inside `@Transactional` business code)
- **Headers must carry traceparent + X-Request-Id** for OTel correlation
- **PII redacted from logs** of producer/consumer (no full message body at INFO/DEBUG)

---

## Topic Naming Convention (inline)

```
<domain>.<aggregate>.<event-type>.v<major>
```

Examples:
- `payments.transfer.initiated.v1`
- `payments.transfer.completed.v1`
- `accounts.account.frozen.v1`
- `audit.event.recorded.v1`
- `payments.transfer.initiated.v1.DLT` (auto-created DLQ — uses `-DLT` suffix per spring-kafka default)
- `payments.transfer.initiated.v1.retry.5s` (retry topic with delay)

Rules:
- All lowercase, dot-separated
- Major version in topic name (`.v1`, `.v2`) — bumped only on breaking schema change
- One event type per topic (no "events" catch-all)
- Compaction (`cleanup.policy=compact`) for snapshot/state topics; `delete` with retention for event streams

---

## Partition / Retention Defaults (inline)

| Topic kind | Partitions | Retention | Replication | min.insync.replicas |
|---|---|---|---|---|
| Financial events (transfers, payments) | 12 (≥ peak parallel consumers) | 7 days | 3 | 2 |
| Audit events | 6 | 90 days (regulatory) | 3 | 2 |
| Notification events | 6 | 1 day | 3 | 2 |
| Compacted state topic | 6 | infinite (compact) | 3 | 2 |
| DLT | same as source | 30 days (must outlive ops triage) | 3 | 2 |

Document any deviation as an ADR.

---

## Order of Operations — Outbox + Producer (inline)

```
@Transactional                         // single DB tx
  1. Apply domain change (e.g. debit account)
  2. INSERT INTO outbox (event_id, aggregate_id, type, payload, status='PENDING')
COMMIT
                                       // separate scheduled poller / CDC
  3. SELECT FROM outbox WHERE status='PENDING' LIMIT N FOR UPDATE SKIP LOCKED
  4. kafkaTemplate.send(topic, key, payload).get()  // sync wait or use callback
  5. UPDATE outbox SET status='SENT', sent_at=now() WHERE event_id=?
```

**Never** call `kafkaTemplate.send()` inside the business `@Transactional` — the message would be published even if the tx rolls back. Use the Outbox table as the durable record. See [references/outbox-dispatcher.md](references/outbox-dispatcher.md).

---

## Consumer Error Handling Tiers (inline)

| Error class | Action | Example |
|---|---|---|
| Transient (network, DB unavailable) | Retry with backoff (retry topic or in-memory) | Kafka broker timeout |
| Poison message (deserialization, schema mismatch) | Send to DLT immediately, no retry | Bad Avro payload |
| Business validation fail | Send to DLT + emit compensating event | Account closed |
| Unknown / unhandled | Send to DLT, alert oncall | NPE in handler |

Default: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` with `FixedBackOff(1000L, 3)` then DLT. Use `ExponentialBackOffWithMaxRetries` for transient errors.

---

## Testing Policy (inline)

| Type | Tool | Coverage |
|---|---|---|
| Unit | JUnit 5 + Mockito on handler logic | ≥ 80% |
| Slice | `@EmbeddedKafka` for fast topology tests | All listeners + DLT routing |
| Integration | **Testcontainers `KafkaContainer`** for end-to-end producer↔consumer | Outbox dispatch + retry + DLT happy/sad paths |
| Contract | Spring Cloud Contract (message verification) | Every published event type |

**Do not** use H2 + mocked Kafka for outbox tests — use real Postgres + real Kafka via Testcontainers (mock divergence trap).

---

## Pre-Handoff Self-Checks

Before emitting handoff to `banking-reviewer`:

- [ ] Producer factory has `enable.idempotence=true`, `acks=all`
- [ ] Consumer factory has `enable.auto.commit=false`
- [ ] Every `@KafkaListener` has a configured error handler + DLT
- [ ] Topic created with correct partitions / replication / retention (Terraform or admin script)
- [ ] Avro/Protobuf schema registered, backward-compatible
- [ ] No `kafkaTemplate.send()` inside business `@Transactional`
- [ ] Outbox table migration present + scheduled dispatcher wired
- [ ] Headers propagate `traceparent` (OTel) + `X-Request-Id`
- [ ] Consumer is idempotent (inbox table or natural dedup on `eventId`)
- [ ] Tests cover happy-path + DLT routing + retry exhaustion
- [ ] Metrics emitted: `kafka.producer.record-send-rate`, `kafka.consumer.records-lag-max`, `outbox.dispatch.failed`
- [ ] Runbook updated: how to drain DLT, how to replay events

---

## Reference Index

- [producer-patterns.md](references/producer-patterns.md) — KafkaTemplate config, idempotence, transactions, partitioning, headers, error callbacks
- [consumer-patterns.md](references/consumer-patterns.md) — `@KafkaListener`, container factories, manual ack, concurrency, batch consumers
- [outbox-dispatcher.md](references/outbox-dispatcher.md) — Outbox table schema, dispatcher loop, idempotent publish, cleanup
- [schema-registry-avro.md](references/schema-registry-avro.md) — Schema Registry, Avro / Protobuf, compatibility rules, evolution playbook
- [kafka-anti-patterns.md](references/kafka-anti-patterns.md) — concrete smells with severity + how to detect

---

## How This Skill is Loaded

Referenced (not auto-injected) by:
- [`.claude/agents/banking-backend-dev.md`](../../agents/banking-backend-dev.md) — Read before any task touching producers, consumers, or outbox
- [`.claude/agents/banking-reviewer.md`](../../agents/banking-reviewer.md) — Read when reviewing PRs that touch `spring-kafka`

Subagents have `Read` tool access — they must read this SKILL.md (and any relevant `references/*.md`) explicitly before starting work.
