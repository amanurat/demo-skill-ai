# Early Consent-Coverage Review — Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Sprint:** SPRINT-2026-Q2-BC-01
> **Reviewer:** `banking-security` (AppSec + Compliance)
> **Review type:** **Early-look** (D2-D3 shift-left) — NOT the full pre-QA security gate
> **Scope of THIS review:** PDPA + BoT consent / lawful basis, data minimization, audit-trail design, IDOR defense-in-depth
> **Risk under mitigation:** RISK-003 (PDPA consent reuse may not cover dashboard reads)
> **Inputs reviewed:** BA-001 (`nfr.md`, `user-stories.md`, `process-flow.md`), PM-001 (`risk-register.md`, `backlog.md`), prior money-transfer security artifact (`docs/artifacts/S7-security-money-transfer.md`)
> **NOT in scope here:** Full STRIDE model, SAST/DAST/SCA, container scans, OWASP ASVS L2 — those run after code review per workflow.md
> **Verdict:** **APPROVED WITH CONDITIONS** (4 SA/TL conditions, 0 blockers)

---

## 1. Lawful Basis Analysis under PDPA

### 1.1 Existing money-transfer lawful basis (verified)

จากการตรวจสอบ `docs/artifacts/S7-security-money-transfer.md` (line 124-133):

- **Lawful basis ที่ใช้ใน money-transfer:** *"contract performance"* (PDPA §24(3) — necessary for performance of a contract to which the data subject is party)
- **Consent management posture:** *"Out-of-scope for this service — handled by identity-service consent registry (PDPA §19). Operates under existing banking contract."*
- **Customer agreement scope (inferred):** การเปิดบัญชีธนาคารกับสถาบันการเงินใน Thailand จะมี customer agreement ที่ครอบคลุม (a) การให้บริการบัญชี, (b) การ provide statements / balance inquiry, (c) การ execute transactions (transfers, payments) ตามคำสั่งลูกค้า

### 1.2 Recommendation สำหรับ balance-comparison dashboard

> **คำตัดสิน: Reuse contractual basis (PDPA §24(3) — performance of contract) — SAME basis as money-transfer.**

**เหตุผล:**

1. **Balance inquiry คือ core service deliverable ของ deposit account agreement** — ลูกค้ามีสิทธิตามสัญญาที่จะดูยอดเงินของตนเอง (counter-party data right). ไม่ใช่ "new processing purpose" แต่เป็น "existing right under the same contract".
2. **ไม่ใช่ profiling / marketing / secondary use** — feature นี้ display เฉพาะข้อมูลที่ลูกค้าเป็นเจ้าของอยู่แล้ว, ไม่ derive insights ใหม่, ไม่ share ออกนอก data controller, ไม่ enrich ด้วย third-party data.
3. **No new personal data collected** — ตาม BA NFR §4 PDPA: *"No new personal data collection in this feature beyond what money-transfer already processes."* ใช้ `AccountClient` + `AccountInfo` DTO ตัวเดิม.
4. **Fresh consent (§19) is NOT required** เพราะ contractual basis cover; ขอ consent ใหม่จะทำให้ legally confusing ("consent on top of contract") และไม่ใช่ best practice.
5. **Legitimate interest (§24(5)) ไม่จำเป็น** เพราะ §24(3) cover แล้ว — legitimate interest ต้องทำ balancing test และจะอ่อนกว่า contractual basis.

### 1.3 ทางเลือกอื่นที่พิจารณาแล้วไม่เลือก

| ทางเลือก | สถานะ | เหตุผลที่ไม่เลือก |
|---|---|---|
| (a) Fresh §19 consent | REJECTED | ซ้ำซ้อนกับ contractual basis; สร้าง UX friction; legally over-engineered |
| (b) §24(5) legitimate interest | REJECTED | อ่อนกว่า §24(3); ต้องมี LIA (Legitimate Interest Assessment) doc; ไม่จำเป็นเมื่อ contract cover |
| (c) §24(3) contractual basis | **SELECTED** | Aligned กับ money-transfer; covers balance inquiry as inherent right of deposit account holder |

---

## 2. Purpose Limitation Check

### 2.1 Original money-transfer consent / contract purpose scope

ตาม money-transfer artifacts:
- **Documented purpose (money-transfer):** "execute funds transfers ตามคำสั่งลูกค้า" + "view account information necessary to complete transfers" (เห็น balance ก่อนยืนยัน transfer)
- **Customer agreement scope (typical Thai retail bank):** "operate deposit accounts, provide statements + balance inquiry + transaction execution + audit trail"

### 2.2 Dashboard read purpose

- **BA-stated purpose:** `purpose = 'balance-inquiry'` (audit event tag, BA NFR §4 + AC-001-H3)
- **User-facing description:** "customer self-service inquiry — view own deposit account balances ranked"

### 2.3 Verdict

> **Purpose limitation: PASS.** Both `"execute transfers"` (money-transfer) และ `"balance-inquiry"` (dashboard) อยู่ภายใต้ same overarching purpose: *"operate the customer's deposit account relationship"*. ทั้งสอง use เฉพาะ data ของ data subject เอง (ไม่ cross-customer), ไม่ derive new insights, ไม่ share นอก data controller.

**Caveat (minor):** BA NFR §4 ระบุ *"data is used solely for the 'balance inquiry' purpose"* — แนะนำให้ SA ยืนยันว่า **privacy notice ที่ลูกค้าเห็นตอน onboarding ครอบคลุม "balance inquiry via digital channels"** อย่างชัดเจน. ถ้า notice เก่าใช้ภาษาว่า "transaction execution" อย่างเดียว → ต้อง **update privacy notice ก่อน GA** (ไม่ใช่ blocker สำหรับ demo / synthetic users) — see Condition C-1.

---

## 3. BoT IT-Risk Audit Alignment

### 3.1 BoT requirements (data access logging)

BoT IT-Risk Guidelines + ธปท. หนังสือเวียน เรื่องการบริหารความเสี่ยงด้าน IT:
- ต้อง log ทุก access ของ customer data (read หรือ write)
- Audit records ต้อง immutable + append-only
- Retention 7 ปี
- Segregation of duties: ops staff ไม่สามารถ modify audit records

### 3.2 BA's audit design (verified)

ตรวจสอบ BA artifacts:

| BoT requirement | BA design (location) | Status |
|---|---|---|
| Audit on every read | NFR §4 + BR-005 + BR-014 + AC-001-H3 + AC-003-H3 + process-flow.md "audit-emit on every retrieval" | ✅ COVERED |
| **Audit on cache hit (not bypassed)** | BR-014 explicit: *"audit event MUST be emitted on every dashboard retrieval, regardless of whether the response was served from Redis cache or from a live AccountClient call. The audit path is never short-circuited by the cache."* | ✅ **EXPLICITLY COVERED** (this was the highest risk area — confirmed safe) |
| Audit on FORBIDDEN (IDOR attempt) | process-flow.md alt-path + AC-001-E2: *"audit event is emitted with result = FORBIDDEN and actorId = customerId A"* | ✅ COVERED |
| Audit on ERROR (upstream failure) | process-flow.md alt-path: *"BDS-)Audit: async emit BALANCE_INQUIRY result=ERROR errorReason=UPSTREAM_UNAVAILABLE"* | ✅ COVERED |
| Audit on empty result | AC-001-E1: *"audit event still emitted with result = SUCCESS and accountCount = 0"* | ✅ COVERED |
| Audit on excluded accounts (dormant filtered) | NFR §4 "PDPA + BoT Intersection — Audit Trail for Excluded Accounts": separate observability counter + main audit event records access attempt | ✅ COVERED |
| Audit emission must not block response | NFR §1 *"Audit event emission latency: < 200ms added overhead; async fire-and-forget; must not block response"* + process-flow.md `-)` async arrow | ✅ COVERED |
| Audit retention 7y | NFR §4 *"7 years, consistent with money-transfer. No new retention configuration required"* | ✅ COVERED |
| Append-only / SoD | NFR §4 *"Segregation of duties: Operations staff MUST NOT have the ability to delete or modify audit records (append-only constraint inherited from audit-service design)"* | ✅ INHERITED from existing audit-service |
| Incident reporting SLA | NFR §4 *"existing 1-hour BoT incident reporting SLA applies"* | ✅ COVERED |

### 3.3 Verdict

> **BoT IT-Risk audit alignment: PASS.** Design ของ BA ครอบคลุมทุก BoT audit requirement. **The "audit on cache hit" rule is the single most important banking control here, and BA explicitly nailed it** (BR-014). ไม่มี gap.

### 3.4 Open item for SA/TL (Condition C-2)

- **Audit event schema** — ต้องยืนยันว่า existing `audit-service` schema (จาก money-transfer) รับ field `eventType = BALANCE_INQUIRY` และ `cacheHit: boolean` ได้, หรือต้องการ schema migration. (DEP-003 ใน risk-register.md ระบุไว้แล้ว — SA ต้อง decide.)
- **Audit payload data minimization** — see Section 4.3 below (do NOT include raw balance value in audit event — record metadata only).

---

## 4. Data Minimization Findings

### 4.1 Proposed response shape (from BA NFR §8)

| Field | Status | Assessment |
|---|---|---|
| `accountId` (UUID) | ✅ ACCEPTABLE | Opaque internal ID, not displayed in UI; needed for client-side keying. Low PII risk. |
| `accountNumber` (masked `****XXXX`) | ✅ ACCEPTABLE | Last 4 digits only, consistent with money-transfer convention (BR-007). |
| `accountType` (human-readable label) | ✅ ACCEPTABLE | Non-PII; needed for user comprehension. |
| `balance` (BigDecimal, formatted) | ✅ ACCEPTABLE | Financial data, the actual subject of the feature; cannot minimize further while meeting requirement. |
| `currency` (ISO code) | ✅ ACCEPTABLE | Non-PII. |
| `balance_as_of` (ISO 8601) | ✅ ACCEPTABLE | Non-PII; needed for staleness indicator. |
| `rank` (integer) | ✅ ACCEPTABLE | Derived; non-PII. |
| `status` (filtered to ACTIVE only) | ✅ ACCEPTABLE | Only ACTIVE accounts returned (BR-003); status not exposed in response, only used as filter. |

### 4.2 Explicitly NOT returned (verified safe)

BA NFR §8 enumerates exclusions:
- ❌ Full account number → masked ✅
- ❌ Customer name → not returned ✅
- ❌ Customer address / contact → not returned ✅
- ❌ Account opening date → not returned ✅
- ❌ Balance history → not returned ✅
- ❌ Transaction details → not returned ✅
- ❌ Loan balance, credit limit, credit card balance → filtered out (OPEN-004 + BR-004) ✅

### 4.3 Data minimization findings (3 items)

#### Finding S-EARLY-001 (low) — Audit event payload should record METADATA only, not raw balance

- **Severity:** low (info-grade — BA has not specified yet, but easy to slip wrong way)
- **Location:** Audit event schema (process-flow.md "Audit-Emit Path" + NFR §4)
- **Issue:** BA's audit event currently lists: `eventType, actorId, customerId, purpose, channel, correlationId, timestamp, result, accountCount, cacheHit`. **The balance VALUE is NOT in the audit event** — but this is implicit, not explicit. Without an explicit prohibition, an implementer might include `accounts: [...]` in the audit payload "for traceability".
- **Why it matters:** Audit logs are retained 7y; if audit payload contained raw balances, every balance snapshot ever viewed would be retained 7y. That's a **purpose-stretch** (audit purpose ≠ balance retention purpose) and creates a large PII honeypot.
- **Remediation (for SA/TL):** Make it EXPLICIT in audit event schema doc that the payload contains ONLY metadata (eventType, actorId, customerId, purpose, channel, correlationId, timestamp, result, accountCount, cacheHit) — and NOT raw account numbers, NOT balance values, NOT balance_as_of per-account. The audit trail records *that a balance inquiry occurred*, not *what the balances were*. See Condition C-2.
- **Reference:** PDPA §22 (data minimization) + BoT audit retention proportionality

#### Finding S-EARLY-002 (low) — `customerId` in structured logs should be hashed or pseudonymized

- **Severity:** low
- **Location:** NFR §6 Observability — structured log fields include `customerId`
- **Issue:** BA states logs include `customerId` (NOT full account data — ✅ good). But raw `customerId` (UUID) appearing in JSON logs at INFO level for every request creates a long-lived linkable identifier in log aggregation systems (e.g., Elasticsearch, Splunk).
- **Why it matters:** PDPA §22 + money-transfer Finding S-08 (per S7-security-money-transfer.md) already flagged the same pattern for outbox events. Consistent pseudonymization across logs is BoT IT-Risk hygiene.
- **Remediation (for SA/TL):** Either (a) hash `customerId` for log appearance (HMAC with rotation key), keeping raw `customerId` only in audit events where it's needed for compliance traceability, OR (b) document that `customerId` UUID is "internal pseudonym" already (not direct identifier) and is acceptable in logs — but explicitly NOT in any external-facing log (only internal ELK / Loki). Pick one and document.
- **Reference:** PDPA §22; consistency with money-transfer S-08 finding

#### Finding S-EARLY-003 (info) — Add explicit "no PII in URL / query string / metric labels" check

- **Severity:** info
- **Location:** NFR §3 Security + §6 Observability
- **Issue:** BA doesn't explicitly prohibit `customerId` in URL path, query string, or Prometheus metric label values. Process-flow shows `Gateway → BDS: Forward request with X-Customer-Id: {customerId}` (header, ✅ good) but the endpoint URL is `GET /v1/balance-dashboard` (no path-param ✅). Still worth making the rule explicit.
- **Why it matters:** URL/query string ends up in access logs, proxy logs, browser history. Prometheus metric labels with high cardinality (customerId) blow up TSDB cost and create per-customer label pivot opportunity (re-identification risk).
- **Remediation (for SA/TL):** Add to OpenAPI spec + Prometheus metric design: (a) `customerId` MUST NOT appear in URL path or query string — derive only from JWT `sub`; (b) Prometheus metric labels MUST be low-cardinality only (e.g., `status`, `cache_hit`, `cache_miss_reason`) — NEVER `customerId` or `accountId` as a label value.
- **Reference:** banking-hard-rules.md #2 (PII in logs); OWASP ASVS V8.3

---

## 5. IDOR Guard Adequacy (Defense-in-Depth)

### 5.1 BA's IDOR design (from NFR §3 + AC-001-E2 + process-flow.md)

BA states multiple layers:
1. **API Gateway:** JWT validated; `X-Customer-Id` header injected from JWT `sub` (process-flow.md step 8: *"Gateway→BDS: Forward request with X-Customer-Id: {customerId from JWT sub}"*).
2. **balance-dashboard-service (BDS):** *"Guard: assert X-Customer-Id == JWT sub (IDOR check)"* (process-flow.md step 9).
3. **NFR §3 explicit:** *"JWT `sub` claim MUST be used as `customerId` filter at service level. Service MUST reject any request where the caller attempts to supply a different `customerId`. HTTP 403 returned."*
4. **BR-001:** *"Only accounts belonging to the authenticated customer (JWT `sub` claim = `customerId`) are returned. Cross-customer access is forbidden at service level."*
5. **AC-001-E2:** explicit test case for cross-customer attempt → 403 + audit event with `result=FORBIDDEN`.

### 5.2 Verdict

> **IDOR defense-in-depth: PASS with one strengthening recommendation.**

Three independent enforcement points:
- (a) Gateway JWT validation (signature + expiry + scope)
- (b) BDS-side assertion (`X-Customer-Id == JWT sub`)
- (c) Service-level filter (`AccountClient.listAccountsByCustomer(customerId)` derives only from JWT sub)

ความครอบคลุม **NOT gateway-only** — BA correctly designed it.

### 5.3 Strengthening recommendation (Condition C-3)

**The BDS-side guard at step 9** *"assert X-Customer-Id == JWT sub"* — must be implemented by **trusting JWT sub, NOT the X-Customer-Id header**. The correct implementation:

```
customerId = jwt.getClaim("sub")    // SOURCE OF TRUTH
if (request.getHeader("X-Customer-Id") != null
    && !request.getHeader("X-Customer-Id").equals(customerId)) {
    audit.emit(FORBIDDEN);
    return 403;
}
// then ALWAYS use customerId from JWT sub for downstream calls
```

**Anti-pattern to forbid:**
```
customerId = request.getHeader("X-Customer-Id");  // WRONG — trusts header
// even if you also "validate" it, downstream code paths can drift
```

SA/TL must spec this clearly: **JWT sub is the only source of truth for customerId on the entire request path inside BDS. The X-Customer-Id header is treated as untrusted input and only used for IDOR detection (mismatch → 403) — never as the value of customerId for any business logic or downstream call.**

This is critical because Spring's `@AuthenticationPrincipal Jwt jwt` and a header bound to a method parameter are easy to mix up; one PR could quietly switch the source from JWT to header and bypass IDOR. **Make it impossible to get the wrong source.**

See Condition C-3.

---

## 6. Hard-Rule Check (banking-security-patterns)

Going through all 11 auto-fail rules + the inline list from SKILL.md, evaluated against the BA artifacts:

| # | Hard Rule | Status | Notes |
|---|---|---|---|
| 1 | Secret hardcoded / in VCS | ✅ N/A | No code yet — DEFER to full security gate (post-code). DevOps P1 CI must wire gitleaks. |
| 2 | PII / card number in logs | ⚠️ MITIGATED | Findings S-EARLY-001/002/003 cover audit-payload, customerId-in-logs, metric-labels. Conditions C-2 + C-3. |
| 3 | JWT in `localStorage` | ⏳ DEFER to FE review | Designer + FE Dev concern; not visible in BA artifacts. Add to FE reviewer checklist. |
| 4 | Plaintext password in storage | ✅ N/A | No password handling in this feature. |
| 5 | Money in `float` / `double` | ⏳ DEFER to TL/BE review | BA says "BigDecimal" implicit via existing `AccountInfo` DTO (reused from money-transfer — already verified `BigDecimal`). TL must confirm. |
| 6 | Missing audit event for state-changing op | ✅ N/A — but BA WENT FURTHER | This is a read-only endpoint (no state change), so the hard rule does not literally apply. BA still emits audit on every read (BR-005, BR-014) — exceeds requirement. ✅ |
| 7 | Missing idempotency on financial POST/PUT | ✅ N/A | Read-only GET endpoint. No idempotency-key required. |
| 8 | HS256 JWT in prod | ⏳ INHERITED from platform | NFR §3: "Reuse existing auth filter chain from money-transfer". S7-security-money-transfer ITEM-2 requires RS256 + Vault issuer-uri. Inherit. |
| 9 | TLS < 1.2 | ✅ ADDRESSED | NFR §3: *"TLS 1.2+ enforced at WAF / API Gateway"*. ✅ |
| 10 | Unauthenticated endpoint exposing customer data | ✅ ADDRESSED | NFR §3 + AC-001-E3 (401 on unauth) + feature flag default false. ✅ |
| 11 | Mass-assignment | ✅ N/A | Read-only GET; no request body. |

### Additional banking-specific rules (from SKILL.md inline + agent gotchas)

| Rule | Status | Notes |
|---|---|---|
| No PII in URL/logs/metrics | ⚠️ See Finding S-EARLY-003 → Condition C-2 |
| No customer data leak via error messages | ✅ ADDRESSED | NFR §3 + process-flow alt-paths use generic "Unable to load accounts" message, no upstream error detail leaked |
| No secrets/tokens in artifacts | ✅ VERIFIED | grep across BA + PM docs — no secrets, no tokens, no API keys in artifact text |
| No bypass of audit trail | ✅ EXPLICITLY ADDRESSED | BR-014 — cache does NOT short-circuit audit. This is the #1 finding I would have raised if missing, but BA pre-empted it. |
| Redis cache encryption at rest | ⚠️ STATED | NFR §3: *"Redis cache payload ... MUST be stored with encryption at rest (AES-256-GCM, consistent with account-service at-rest encryption)"* → SA/TL must verify shared Redis cluster supports this. Condition C-4. |
| Kafka audit topic TLS + ACL | ⏳ INHERITED from money-transfer | S7-security-money-transfer ITEM-8 covers Kafka topic ACLs. Inherit. |

### 6.1 Hard-rule check verdict

> **No hard-rule violation. Three rules deferred to later phases (FE review, TL/BE design, full security gate). All deferrals are tracked and have explicit owners.**

---

## 7. Open Compliance Items for SA + TL (Conditions)

These are NOT blockers for sprint start — they are conditions SA/TL must address **before code starts / in their ADR**. Tracked as `metadata.conditions` in handoff JSON.

### Condition C-1: Privacy Notice Verification (before GA, not blocker for demo)

- **Owner:** `banking-solution-architect` (to coordinate with compliance team — out of agent scope for synthesis)
- **Action:** Verify that the **customer-facing privacy notice** (used at deposit account onboarding) explicitly covers "balance inquiry via digital / mobile banking channels". If not, schedule a notice update **before GA** (production launch).
- **Demo impact:** **NONE** — demo will run on **synthetic test users**, not real customer data. Notice update is not blocking for the sprint demo.
- **Documentation:** Record SA's verification result in ADR; if notice update needed, file as follow-up sprint ticket.
- **Reference:** PDPA §23 (data subject must be informed of processing purposes)

### Condition C-2: Audit Event Schema — Metadata Only

- **Owner:** `banking-tech-lead` (audit-event schema decision) + `banking-solution-architect` (cross-check with audit-service consumer)
- **Action:** Make it **EXPLICIT in audit event JSON schema** that payload contains ONLY: `eventType, actorId, customerId, purpose, channel, correlationId, timestamp, result, accountCount, cacheHit`. **MUST NOT** include: `accounts[]`, `balance`, `accountNumber` (even masked), `balance_as_of` per-account, `currency`. Audit records *that* a balance inquiry occurred, not *what* the balances were.
- **Rationale:** Finding S-EARLY-001 — prevents 7y retention of an unintended balance-history honeypot.
- **Acceptance:** Schema documented + code-review checklist item + integration test asserting audit-event payload does NOT contain balance/accountNumber fields.

### Condition C-3: customerId Source-of-Truth = JWT sub Only

- **Owner:** `banking-tech-lead` (define implementation pattern) + `banking-backend-dev` (enforce)
- **Action:** In OpenAPI spec + implementation notes, **explicitly forbid** using `X-Customer-Id` header as the value of `customerId` for any business logic or downstream call. Header is for IDOR-mismatch detection only. The single source of truth is `jwt.getClaim("sub")` extracted via `@AuthenticationPrincipal Jwt`.
- **Pattern:** Provide a `CustomerIdResolver` bean or static helper so that there is **one** code path to derive `customerId`. Static analysis rule (Spotbugs/PMD) or unit test to detect direct `request.getHeader("X-Customer-Id")` usage outside the IDOR-check filter.
- **Rationale:** Finding 5.3 — defense-in-depth requires not just "validating" the header but making it structurally impossible to use the wrong source.
- **Acceptance:** ADR documents pattern; code review checklist; integration test simulates header-tampering attempt → 403.

### Condition C-4: Redis Encryption at Rest (verify cluster capability)

- **Owner:** `banking-solution-architect` + `banking-devops` (verify shared Redis cluster supports it)
- **Action:** Verify the shared Redis cluster (ASSUME-004) has **encryption at rest enabled** with AES-256-GCM (or equivalent) — consistent with account-service at-rest encryption. If not enabled, either (a) enable on shared cluster, OR (b) provision dedicated balance-dashboard Redis namespace with encryption.
- **Rationale:** NFR §3 already states the requirement; this condition is to verify *infrastructure capability* matches BA's stated intent. If cluster cannot meet this, BA NFR §3 needs a documented exception OR ASSUME-004 is invalidated.
- **Acceptance:** DevOps confirms in CI / infra-as-code; ADR records cluster encryption posture; failure → escalate.

---

## 8. Hard-Rule Auto-Fail Verification (final check)

Going through SKILL.md inline list ONE MORE TIME with eyes on BA artifacts only (no code yet):

- [x] No secret hardcoded — no secrets in artifacts
- [x] No PII in logs (planned design verified — Findings S-EARLY-001/002/003 strengthen, no blockers)
- [x] No JWT in localStorage (deferred to FE review — not present in BA scope)
- [x] No plaintext password (N/A — read-only)
- [x] No money in float/double (BigDecimal inherited from money-transfer AccountInfo)
- [x] No missing audit event (audit on every read, including cache hit — BR-014, gold-standard)
- [x] No missing idempotency (N/A read-only GET)
- [x] No HS256 in prod (inherited from money-transfer fix ITEM-2 — RS256 + Vault)
- [x] No TLS < 1.2 (NFR §3 explicit TLS 1.2+)
- [x] No unauthenticated endpoint (NFR §3 + AC-001-E3 + feature flag default false)
- [x] No mass-assignment (N/A — read-only GET, no body)

> **ZERO auto-fail triggered. ZERO blockers.**

---

## 9. RISK-003 Mitigation Status

| Sub-check | Status | Evidence |
|---|---|---|
| Lawful basis verified for dashboard read | ✅ MITIGATED | §1.2 — Reuse §24(3) contractual basis. Same as money-transfer. |
| Purpose limitation verified | ✅ MITIGATED (with C-1 verification before GA) | §2.3 — Both purposes within same customer relationship scope |
| Audit-trail design covers BoT requirements | ✅ MITIGATED | §3.3 — BA design includes audit on cache hit (BR-014) — exceeds requirement |
| Data minimization on response | ✅ MITIGATED | §4 — Response shape passes; 3 minor findings strengthen audit / log / metric posture |
| IDOR defense-in-depth | ✅ MITIGATED (with C-3 implementation pattern) | §5 — three independent enforcement points; C-3 makes it structurally enforced |

> **RISK-003 status: MITIGATED.** Subject to the 4 conditions (C-1..C-4) for SA/TL during design phase. **Demo can proceed on synthetic test users. GA requires C-1 privacy-notice verification.**

PM may **downgrade RISK-003 score from 10 (HIGH) to 4 (LOW)** in next risk register update — likelihood drops from 2 to 1 (verified, not assumed), impact remains 4 (regulatory severity unchanged but mitigation in place).

---

## 10. Summary Table — Findings

| ID | Severity | OWASP / Reg | Title | Owner | Condition |
|---|---|---|---|---|---|
| S-EARLY-001 | low | PDPA §22 | Audit payload should be metadata-only (no raw balance) | TL + SA | C-2 |
| S-EARLY-002 | low | PDPA §22 / banking hard-rule #2 | `customerId` in logs — pseudonymize or document as internal-only | SA | (advisory, not strict condition) |
| S-EARLY-003 | info | OWASP ASVS V8.3 | Make "no PII in URL/query/metric-labels" explicit in OpenAPI + metric spec | TL | (advisory) |
| F-5.3 (IDOR strengthening) | medium-mitigated | OWASP A01 | JWT sub = single source of truth; header only for mismatch detection | TL | C-3 |
| F-7.1 (Privacy notice) | low (GA-gated) | PDPA §23 | Verify privacy notice covers "balance inquiry via digital channel" | SA → compliance | C-1 |
| F-6.x (Redis at-rest enc) | medium-mitigated | PDPA / BoT IT-Risk | Verify Redis cluster encryption-at-rest capability | SA + DevOps | C-4 |

**Severity totals:** 0 critical · 0 high · 0 medium-unmitigated · 3 low · 1 info · 2 mitigated-by-conditions

---

## 11. Verdict

> **APPROVED WITH CONDITIONS** — 4 conditions for SA/TL to address before / during design phase. No blockers. Sprint may proceed in parallel with these conditions tracked.

**RISK-003 status: MITIGATED.** Recommend PM downgrade score 10 → 4 in next register update.

**Demo posture (2026-06-04):** ACCEPTABLE on synthetic test users with current BA design + conditions met during SA/TL phase. Privacy-notice update (C-1) is **post-demo / pre-GA** work, not sprint-blocking.

**Next security touchpoint:** Full SAST/DAST/STRIDE gate after `banking-reviewer-be` and `banking-reviewer-fe` both approve (per workflow.md D9). At that gate I will re-run the full hard-rules + OWASP Top 10 + STRIDE per endpoint against real code.

---

*Reviewer: `banking-security` · Early-look · 2026-05-21*
