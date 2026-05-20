# Feature Seed — View Transaction History

> **Source:** Demo scenario seed สำหรับ live run ใน 17 นาที
> **Why this feature:** Read-only / no Saga / scope เล็กพอจบใน demo แต่ยังเห็นทุก phase ของ SDLC

---

## Business Context

ลูกค้า Internet Banking ที่ login แล้ว (OAuth2/JWT) ต้องการเปิดดูประวัติรายการ transaction ที่ตัวเองทำ — เช่น เพื่อเช็คยอดที่โอนไปเมื่อสัปดาห์ที่แล้ว, ดู refund ที่ค้างอยู่, หรือยืนยันก่อนสอบถาม call center

ตอน v1 ของ Money Transfer service เก็บ transaction ลง DB อยู่แล้ว แต่ยังไม่มี endpoint ให้ลูกค้าดึงดูเองจาก mobile/web ต้องเพิ่ม feature นี้ใน sprint นี้

---

## In Scope (v1)

- **Endpoint:** `GET /api/v1/transactions`
- **Filter:**
  - `from` (ISO-8601 date, optional — default = 30 วันย้อนหลัง)
  - `to` (ISO-8601 date, optional — default = today)
  - `type` (`TRANSFER_OUT` | `TRANSFER_IN` | `ALL` — default `ALL`)
- **Pagination:** `page` (0-based), `size` (default 20, max 100)
- **Sort:** `executed_at` descending เป็น default (newest first)
- **Response fields ที่ลูกค้าเห็น:**
  - `transactionId`, `executedAt`, `type`, `amount`, `currency`
  - `counterpartyAccountMasked` (เช่น `xxx-x-x1234-x`)
  - `counterpartyName`
  - `status` (`SUCCESS` | `FAILED` | `PENDING`)
  - `memo`
- **Frontend:** Angular list view + filter bar + pagination control + empty state + loading skeleton

## Out of Scope (v2+)

- Cross-customer search (admin/ops)
- Export to CSV/PDF
- Full-text search ใน memo
- Group by date (UI grouping)
- Statement download

## Hard Constraints

1. **Authorization:** ลูกค้าเห็นได้เฉพาะ transaction ที่ `customer_id = JWT.sub` เท่านั้น (no IDOR)
2. **Rate limit:** ≤ 60 requests/นาที/customer (anti-scraping)
3. **PII masking:** Account number ของ counterparty ต้อง mask ตาม PDPA
4. **Audit:** Log "view" event (who, when, filter used) — append-only
5. **Performance:** p95 latency < 500ms ที่ dataset 1M rows
6. **Compliance:** ไม่ออกข้อมูล card number / CVV / PIN ในรูปแบบใดๆ

## Non-Functional Requirements

| NFR | Target |
|---|---|
| Availability | 99.9% (read path) |
| p95 latency | < 500ms |
| p99 latency | < 1000ms |
| Throughput | 200 RPS sustained |
| Data retention | 7 ปี (regulatory) |
| Accessibility (FE) | WCAG 2.1 AA |
| i18n (FE) | TH / EN |

---

## Open Questions (BA ควรเก็บ flag ไว้)

- Date range สูงสุดให้ดูย้อนหลังได้กี่เดือน? (สมมติ 6 เดือนสำหรับ v1 — รอ compliance ยืนยัน)
- ลูกค้าเห็น transaction ของ joint account ได้ไหม? (defer to v2)
- กรณี downstream (Audit Service) ล่ม จะ degrade aviator ยังไง? (default: log warn, ไม่ block response)

---

## Why "small but full SDLC"

| Phase | สิ่งที่เคสนี้ครอบคลุม |
|---|---|
| BA | INVEST stories + AC + NFR + open questions |
| SA | ตัดสินใจ "reuse account-service" vs "new transaction-query-service" |
| Designer | List + filter + pagination + empty/loading/error states |
| Tech Lead | OpenAPI 3 spec + Pageable contract + RFC 7807 errors |
| BE | JPA Specification + Pageable + readOnly tx + masking |
| FE | List component + filter form + i18n + a11y |
| Reviewer | จับ N+1, missing trackBy, JWT not enforced, etc. |
| Security | STRIDE — โดยเฉพาะ Information Disclosure + IDOR |
| QA | Unit + integration (Testcontainers) + E2E + perf k6 |
| DevOps | Dockerfile + Helm + Grafana panel + alert rules |
