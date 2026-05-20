# Demo Script (Presenter) — "Add Favorite Beneficiary" (Live, 18 min) — **Scenario B / Plan B**

> **เมื่อไหร่ใช้:** Plan B หาก Scenario A (View Transaction History) จบเร็วเกินคาด (< 13 min)
> หรือใช้ demo รอบที่ 2 ที่เน้น **write path + Idempotency-Key + Flyway migration**
>
> **Audience:** ทีม Dev | **Total:** 18 นาที (ยาวกว่า Scenario A 1 นาที เพราะมี migration step)

---

## ทำไม Scenario B แตกต่าง

| Aspect | Scenario A (View History) | Scenario B (Add Favorite) |
|---|---|---|
| HTTP method | GET (read-only) | POST + GET + DELETE |
| Idempotency | ไม่ต้อง | **บังคับ — Idempotency-Key header + Redis 24h TTL** |
| DB migration | ไม่ต้อง | **บังคับ — Flyway V2__add_favorite_beneficiary.sql** |
| Audit | View event (low risk) | **State change — เข้า audit log แน่นอน** |
| Unique constraint | ไม่ต้อง | (customer_id, account_number) UNIQUE |
| FE form | Filter bar | Form + validation + duplicate check |
| Skill exercised | spring-boot-banking (read path) | spring-boot-banking (write + idempotency) + openapi-flyway-standards |

---

## Feature Seed

```text
Feature: Add Favorite Beneficiary (Saved Payee)

ลูกค้าต้องการบันทึก payee ที่โอนบ่อยไว้ใน "รายการโปรด" เพื่อโอนรอบหน้าไม่ต้องพิมพ์เลขบัญชีใหม่

In Scope (v1):
- POST /api/v1/favorites — เพิ่ม payee (account number + display name + bank code)
- GET /api/v1/favorites — list ของลูกค้าคนนั้น (paginated)
- DELETE /api/v1/favorites/{id} — soft delete

Hard constraints:
1. Idempotency-Key header (TTL 24h)
2. Max 50 favorites per customer
3. Unique (customer_id, account_number) — ไม่ซ้ำ
4. Account number ต้อง validate format ก่อน + เรียก account-service เช็คมีจริง
5. Audit ทุก add/delete

NFR:
- p95 < 300ms (POST), < 200ms (GET)
- 99.95% availability
- a11y WCAG 2.1 AA
```

---

## Master Timeline (18 min)

| Act | Time | Agents | Key difference vs Scenario A |
|---|---|---|---|
| Opening | 0:00-2:00 | — | (same) |
| 1. Requirements | 2:00-4:00 | pm → ba | Stories include duplicate-handling, max-50 limit |
| 2. Shift-left trio | 4:00-6:30 | sa + designer + qa (parallel) | SA decides own service vs extend payee-service |
| 3. Tech Lead | 6:30-8:30 | tech-lead | **Includes Flyway V2 migration + Idempotency contract** |
| 4. THE PARALLEL FIRE | 8:30-12:00 | be + fe + devops (parallel) | **BE = Idempotency interceptor + Redis. FE = form validation. DevOps + migration job** |
| 5. Reviewers | 12:00-14:00 | reviewer-be + reviewer-fe | **BE catches: missing unique constraint, idempotency race condition** |
| 6. Security | 14:00-15:30 | security | **STRIDE — Tampering (account validation) + Repudiation (audit)** |
| 7. DoD walkthrough | 15:30-17:00 | qa P2 + devops P2 (pre-rendered) | DB migration verified, idempotency replay test |
| Wrap-up | 17:00-18:00 | — | Timer reveal + comparison vs Scenario A |

---

## Key Prompts (ตัวที่ต่างจาก Scenario A)

### Act 3 — Tech Lead (เพิ่ม Flyway + Idempotency)

```text
Player, ใช้ banking-tech-lead:

Required outputs:
1. OpenAPI 3 spec at docs/scenarios/add-favorite-beneficiary/S4-openapi.yaml
   - POST /api/v1/favorites (require Idempotency-Key header)
   - GET /api/v1/favorites (paginated)
   - DELETE /api/v1/favorites/{id}
   - Problem Detail RFC 7807 for 400/401/403/409/429

2. Flyway migration at docs/scenarios/add-favorite-beneficiary/S4-V2__add_favorite_beneficiary.sql
   - Table: favorite_beneficiary
   - Columns: id (UUID PK), customer_id, account_number, display_name, bank_code,
              version (optimistic lock), created_at, updated_at, deleted_at (soft delete)
   - UNIQUE INDEX on (customer_id, account_number) WHERE deleted_at IS NULL
   - INDEX on (customer_id, created_at DESC)

3. Idempotency contract document at docs/scenarios/add-favorite-beneficiary/S4-idempotency.md
   - Key format, TTL, hash of body, conflict handling

Skill: openapi-flyway-standards
```

### Act 4 — Parallel Fire (เพิ่ม Idempotency + Migration)

```text
Player, 3 Task tool calls parallel:

1. banking-backend-dev — implement at backend/favorite-service/
   Required:
   - Controller with @IdempotentRequest interceptor (Redis-backed, 24h TTL)
   - Service @Transactional with optimistic lock retry
   - Validate account exists (call account-service via Resilience4j CircuitBreaker)
   - Outbox event FAVORITE_ADDED / FAVORITE_REMOVED
   - JPA entity with @Version + soft delete query method
   Skill: spring-boot-banking + resilience4j-patterns + kafka-spring-patterns

2. banking-frontend-dev — at frontend/src/app/features/favorites/
   Required:
   - Reactive Form with async validator (account number format + uniqueness check)
   - HttpClient interceptor generates Idempotency-Key (UUID v4)
   - List + add modal + delete confirmation
   - Toast notification
   Skill: angular-banking-ui

3. banking-devops Phase 1
   - Dockerfile + Helm + Flyway migration job
   - Grafana panel: favorite_add_duration + favorite_count_per_customer histogram
   - Prometheus alert: max_favorites_exceeded
```

---

## Magic Moment Changes

### Act 5 — Reviewers — Expected catches เปลี่ยน

**BE Reviewer ควรจับ:**
- 🔴 Idempotency-Key check race condition (TOCTOU) → ใช้ Redis `SETNX` atomic
- 🔴 Missing unique constraint enforcement at app level (rely on DB only — race)
- 🟠 No retry on optimistic lock (`OptimisticLockingFailureException`)
- 🟠 Validate account ไม่มี timeout → hang ถ้า account-service ล่ม
- 🟡 Hardcoded TTL — ควร config

**FE Reviewer ควรจับ:**
- 🟠 Form ไม่มี duplicate check ก่อน submit → user confused เมื่อ 409
- 🟠 Idempotency-Key gen ใน component (ควรอยู่ใน interceptor)
- 🟡 Confirm dialog ไม่มี focus trap

### Act 6 — Security — STRIDE focus เปลี่ยน

| STRIDE | View History | Favorite |
|---|---|---|
| Spoofing | JWT validation | (same) |
| **Tampering** | Date range injection | **Account number forge → favorite ที่ไม่ใช่ของจริง** |
| **Repudiation** | View log (low) | **Add/Remove audit — ต้องครบ** |
| Information Disclosure | Counterparty mask | (same — list ไม่ leak) |
| DoS | Rate limit | Max 50 favorites + add rate limit |
| Elevation | (n/a) | (n/a) |

---

## Q&A Anchor Points (เพิ่มจาก Scenario A)

| คำถาม | คำตอบ |
|---|---|
| "Idempotency-Key ทำงานยังไง?" | Client gen UUID → BE hash body + key → Redis `SETNX` 24h. ถ้า key เคยใช้แล้ว body ต่าง → 409 Conflict. ถ้า body เหมือน → return cached response |
| "ทำไม unique constraint อย่างเดียวไม่พอ?" | Race condition — 2 requests พร้อมกัน DB constraint จะ reject 1 ตัวด้วย exception แต่ user เห็น 500. ดีกว่าเช็คใน app + return 409 clean |
| "Flyway migration ทำตอน deploy ยังไง?" | Init container ก่อน app start. ใช้ Helm hook `pre-install` + `pre-upgrade` |

---

## ⚠ Decision Point — เลือก Scenario A หรือ B?

| ถ้าผู้ฟัง... | เลือก |
|---|---|
| ส่วนใหญ่เป็น dev ใหม่ / junior | **A** (read-only เข้าใจง่าย) |
| ส่วนใหญ่เป็น senior / domain ธนาคาร | **B** (Idempotency + migration น่าสนใจกว่า) |
| Mixed | **A** then **B** ถ้าเวลาเหลือ |
| มีคำถามเรื่อง write safety / race condition | **B** |
| มีคำถามเรื่อง data privacy / IDOR | **A** |

---

## Note สำหรับการเขียนแบบเต็ม

> เอกสาร Scenario B ตัวนี้เป็น **skeleton / Plan B** — ถ้าทีมตัดสินใจใช้ Scenario B เป็น demo หลัก ให้ขยายเป็น 7-act script เต็มแบบเดียวกับ `view-transaction-history/DEMO-SCRIPT-PRESENTER.md`:
> 1. Copy structure
> 2. แทนที่ feature-specific narration
> 3. เพิ่ม Flyway migration narration ใน Act 3
> 4. เพิ่ม Idempotency-Key narration ใน Act 4
> 5. ปรับ Q&A section
>
> สร้าง `prompts-cheatsheet.md` + `requirements.md` + `EXPECTED-OUTPUTS.md` คู่ขนาน
