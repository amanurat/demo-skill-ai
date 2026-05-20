# Fallback — Reviewer Findings (Pre-baked for Wow Moment)

> **Use only if both reviewers fail to catch findings in Act 5**
> Purpose: รักษา "anti-pattern catch" magic moment โดยใช้ findings สำเร็จรูป

---

## Backend Review Findings (banking-reviewer-be)

**Reviewed:** `backend/transaction-query-service/`
**Verdict:** `changes_requested` (1 blocker, 2 major, 3 minor)

### Findings

| # | Severity | File | Issue | Fix |
|---|---|---|---|---|
| 1 | 🔴 Blocker | `TransactionController.java` | Customer ID ดึงจาก `@RequestParam` แทน JWT → **IDOR** (ลูกค้า A เห็น transaction ลูกค้า B ได้) | `@AuthenticationPrincipal Jwt jwt` แล้วใช้ `jwt.getSubject()` |
| 2 | 🟠 Major | `TransactionQueryService.java` | ขาด `@Transactional(readOnly = true)` → ใช้ write replica + lock ใหญ่กว่าจำเป็น | Add annotation |
| 3 | 🟠 Major | `TransactionRepository.java` | Lazy fetch counterparty + map ใน loop → **N+1 query** ที่ pagination ใหญ่ | ใช้ JPA Specification + JOIN FETCH หรือ projection |
| 4 | 🟡 Minor | `TransactionController.java` | Log `customerId` ดิบ → PII leak ใน access log | Hash ก่อน log หรือ ใช้ MDC tag |
| 5 | 🟡 Minor | `TransactionFilterRequest.java` | ไม่มี `@Max(100)` ที่ `size` → ลูกค้าขอ 10,000 records ได้ | Add validation |
| 6 | 🟢 Nit | `TransactionDto.java` | ใช้ `Date` แทน `Instant` | Modernize to `java.time.Instant` |

### Quote สำหรับ presenter

> "ดูข้อ 1 — IDOR. agent ดู controller signature แล้วเห็นทันทีว่า customer_id มาจาก request param ไม่ใช่ JWT. นี่คือ blocker ระดับ data breach"
>
> "ข้อ 3 — N+1 query. มันอ่าน repository layer แล้วคำนวณ access pattern ตอน pagination ใหญ่"

---

## Frontend Review Findings (banking-reviewer-fe)

**Reviewed:** `frontend/src/app/features/transactions/`
**Verdict:** `changes_requested` (0 blocker, 2 major, 4 minor)

### Findings

| # | Severity | File | Issue | Fix |
|---|---|---|---|---|
| 1 | 🟠 Major | `transaction-list.component.html` | ไม่มี `trackBy` ใน `*ngFor` (หรือ `track` ใน @for) → re-render ทั้ง list ทุก state change | Add `trackBy: trackByTxnId` |
| 2 | 🟠 Major | `transaction.service.ts` | `.subscribe()` ใน component ไม่มี cleanup → memory leak ตอน navigation | ใช้ `takeUntilDestroyed()` หรือ async pipe |
| 3 | 🟡 Minor | `transaction-list.component.html` | ใช้ `[innerHTML]="txn.memo"` → XSS risk ถ้า server return HTML | Switch to `{{ txn.memo }}` (text binding) |
| 4 | 🟡 Minor | `transaction-filter.component.ts` | Date picker ไม่มี `aria-label` → screen reader อ่านไม่รู้เรื่อง | Add `aria-label="From date"` etc |
| 5 | 🟡 Minor | `transaction-list.component.css` | Text contrast ratio 3.8:1 → ต่ำกว่า WCAG AA (4.5:1) | Darken text color |
| 6 | 🟢 Nit | `transaction.model.ts` | `type: any` ใน 2 ที่ → ไม่ type-safe | Replace with literal union |

### Quote สำหรับ presenter

> "ดูข้อ 1 — ขาด trackBy. agent รู้ว่า Angular re-render ทั้ง list ถ้าไม่มี — ทำให้ scroll กระตุก animation พัง"
>
> "ข้อ 3 — XSS. มันเห็น innerHTML กับ user-controlled string (memo) แล้วเตือนทันที"

---

## Combined Quality Gate Verdict

| Gate | Result |
|---|---|
| Both reviewers ran | ✅ |
| No blocker outstanding | ❌ — BE #1 (IDOR) ต้องแก้ก่อน |
| Coverage ≥ 80% | (TBD ตอน QA Phase 2) |
| Build green | ✅ |
| Lint pass | ✅ |
| **Overall** | **changes_requested** → loop back to dev |

---

## Use during demo (60 sec narration)

> "Reviewer ออกผลแล้ว — มี 1 blocker, 4 major"
>
> "Blocker คือ IDOR — ลูกค้าเห็น transaction ของลูกค้าคนอื่นได้ ถ้าไม่จับ ขึ้น prod = ข่าว"
>
> "Major อีก 4 — N+1, missing readOnly, missing trackBy, subscription leak"
>
> "ในทีมจริง — reviewer คนต้องอ่านทั้ง PR. agent จับให้แทนใน 30 วินาที"
>
> "Player จะ loop กลับให้ Dev agent แก้ blocker ก่อน — สูงสุด 3 รอบ ถ้ายังไม่ผ่าน escalate ให้คนช่วย"
