# LO-FI Wireframes — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Phase:** Phase 1 — LO-FI / Discovery
> **Reference viewport:** 375 x 812px (mobile-first; iPhone SE / typical Thai mobile)
> **Notation:** ASCII text wireframes. Boxes / labels / hierarchy only. No colour, no typography, no icons (deferred to Phase 2 HI-FI).
> **Thai UI copy is illustrative — final micro-copy + i18n keys land in Phase 2.**

Legend:
- `[ ... ]` = component / region
- `*XXXX` = masked account number last-4
- `(skeleton)` = grey placeholder bar
- `<--- 375px --->` = viewport width marker

---

## Screen index

| # | Screen ID | Title | State | AC anchors |
|---|---|---|---|---|
| 1 | `balance-dashboard-default` | Balance Dashboard — default | 3-5 accounts, warm/cold load OK | AC-001-H1/H2/H4, AC-002-H1/H2/H4 |
| 2 | `balance-dashboard-loading` | Loading skeleton | First load / pull-to-refresh | NFR perf, AC-005-H1 |
| 3 | `balance-dashboard-empty` | Empty — no in-scope active accounts | 0 accounts | AC-001-E1 |
| 4 | `balance-dashboard-stale` | Stale data warning (per-row + optional global) | Snapshot returned / `balance_as_of` > threshold | AC-002-E1, AC-003-E1, AC-003-H4 |
| 5 | `balance-dashboard-error` | Hard error — retry | HTTP 5xx after fail-open exhausted | AC-001 fall-back; resilience NFR §2 |
| 6 | `balance-dashboard-single` | Single account | 1 in-scope active account | Edge of AC-001-H1 (N=1) |
| 7 | `balance-dashboard-dense` | 10-account density | Max practical fixture | AC-005-H1 + scroll behaviour |

---

## Screen 1 — Default state (3-5 accounts)

```
<------------------- 375px ------------------->
+----------------------------------------------+
| <-- Status bar (OS-owned, not part of UI) -->|
+----------------------------------------------+
| [PageHeader]                                 |
|  บัญชีของฉัน                                 |
|  Updated just now  ·  3 accounts             |
+----------------------------------------------+
| [PullToRefreshAffordance]  (hint, slim row)  |
+----------------------------------------------+
| [AccountList role="list" aria-label="ranked  |
|  list of accounts, highest balance first"]   |
|                                              |
|  +----------------------------------------+  |
|  | [AccountRow role="listitem"  rank=1]   |  |
|  | [TypeIcon]  Savings · *7890            |  |
|  |             ฿128,540.25  THB           |  |
|  |             Updated 2 minutes ago      |  |
|  +----------------------------------------+  |
|                                              |
|  +----------------------------------------+  |
|  | [AccountRow rank=2]                    |  |
|  | [TypeIcon]  Fixed Deposit · *4421      |  |
|  |             ฿84,012.00  THB            |  |
|  |             Updated 3 hours ago        |  |
|  +----------------------------------------+  |
|                                              |
|  +----------------------------------------+  |
|  | [AccountRow rank=3]                    |  |
|  | [TypeIcon]  Current · *0033            |  |
|  |             ฿12,005.55  THB            |  |
|  |             Updated 12 seconds ago     |  |
|  +----------------------------------------+  |
|                                              |
+----------------------------------------------+
| [FooterSpacer]                               |
+----------------------------------------------+
```

**Annotations (Screen 1):**

| Region | Component | Notes |
|---|---|---|
| Header | `PageHeader` | Title ("บัญชีของฉัน"), subtitle = freshest `balance_as_of` of the list + count. Subtitle reflects the MOST RECENT row's freshness so customer trusts the top of the page. |
| Pull-to-refresh | `PullToRefreshAffordance` | Recommended for v1. Slim hint area; on pull triggers refetch (audit re-emitted per BR-014). |
| List | `AccountList` | Semantic `role="list"`; rows are children with `role="listitem"`. |
| Row | `AccountRow` | Tap target ≥ 44×44dp. Anatomy below. |
| Row anatomy | `[TypeIcon]` + label + masked-number + balance + currency + lastUpdated | TypeIcon is a placeholder square in LO-FI; final iconography deferred to HI-FI Phase 2. |
| Account label | Thai-first, e.g. "บัญชีออมทรัพย์" (BR-008); LO-FI box shows English for orientation; HI-FI swaps to Thai-primary with English fallback per locale. |
| Masked number | Always `*XXXX` (BR-007, AC-002-H3). Never any longer prefix in the DOM. |
| Balance | Formatted (BR-011): `฿128,540.25`. `aria-label="128,540 บาท"` (AC-005-E2, BR-022). |
| Currency code | Adjacent ISO code "THB" — even when only THB exists today (forward-compatible). |
| Last updated | Relative time derived from `balance_as_of` (NOT `updated_at` per OPEN-002). Long-press / hover reveals absolute "DD MMM YYYY HH:mm ICT". |
| Tie-break visualisation | When two adjacent rows have identical balance, no extra visual is added in LO-FI; HI-FI may add a subtle tie indicator (e.g., same rank label). The deterministic accountId ASC sort is invisible by design — customers see consistent order across reloads. |

**Banking notes:**
- No CTA on this screen aside from row tap (exit) and pull-to-refresh.
- No "transfer from this account" shortcut (explicitly out of scope).
- No total / aggregate amount displayed (OPEN-001 — native currency only).
- IDOR is enforced at API; UI never shows a `customerId` field at all.

---

## Screen 2 — Loading skeleton

```
<------------------- 375px ------------------->
+----------------------------------------------+
| [PageHeader]                                 |
|  บัญชีของฉัน                                 |
|  (skeleton subtitle bar)                     |
+----------------------------------------------+
| [AccountList aria-busy="true"]               |
|                                              |
|  +----------------------------------------+  |
|  | (skeleton row)                         |  |
|  |  ████████                              |  |
|  |  ██████████████                        |  |
|  |  ██████                                |  |
|  +----------------------------------------+  |
|                                              |
|  +----------------------------------------+  |
|  | (skeleton row)                         |  |
|  |  ████████                              |  |
|  |  ██████████████                        |  |
|  |  ██████                                |  |
|  +----------------------------------------+  |
|                                              |
|  +----------------------------------------+  |
|  | (skeleton row)                         |  |
|  |  ████████                              |  |
|  |  ██████████████                        |  |
|  |  ██████                                |  |
|  +----------------------------------------+  |
|                                              |
+----------------------------------------------+
```

**Annotations (Screen 2):**

- Skeleton row count = 3 (smart default; do NOT echo cached count to avoid leaking PII via timing).
- `aria-busy="true"` on the list region; screen readers announce loading.
- Skeleton shape mirrors final row anatomy to avoid layout shift (CLS).
- Respect `prefers-reduced-motion` — no shimmer animation when set.
- If load > 800ms (cold-cache p95 ceiling per BR-018), keep skeleton (do not flicker to error).

---

## Screen 3 — Empty state (0 in-scope active accounts)

```
<------------------- 375px ------------------->
+----------------------------------------------+
| [PageHeader]                                 |
|  บัญชีของฉัน                                 |
+----------------------------------------------+
|                                              |
|                                              |
|         +-----------------------+            |
|         |  [EmptyIllustration]  |            |
|         |     (placeholder)     |            |
|         +-----------------------+            |
|                                              |
|         ยังไม่มีบัญชีที่ใช้งานอยู่               |
|                                              |
|         บัญชีที่ปิดหรือไม่เคลื่อนไหว           |
|         จะไม่แสดงในหน้านี้                    |
|                                              |
|         [CTA placeholder — disabled in v1]   |
|         "เปิดบัญชีใหม่"  (out of scope)       |
|                                              |
|                                              |
+----------------------------------------------+
```

**Annotations (Screen 3):**

- Trigger: AC-001-E1 (HTTP 200 + empty `accounts` array).
- Audit event STILL emitted with `accountCount=0` (BA decision; UI does not block this).
- Copy explicitly references OPEN-003 ("ปิดหรือไม่เคลื่อนไหวจะไม่แสดง") to set expectations.
- "เปิดบัญชีใหม่" CTA is a v1 placeholder — disabled / link-only — full open-account flow is OUT OF SCOPE for this sprint.
- Illustration is a placeholder; final asset chosen in Phase 2 HI-FI.

---

## Screen 4 — Stale data warning

There are two stale variants. LO-FI consolidates them in one screen with both regions illustrated.

```
<------------------- 375px ------------------->
+----------------------------------------------+
| [PageHeader]                                 |
|  บัญชีของฉัน                                 |
+----------------------------------------------+
| [StalenessBanner role="status" aria-live=    |
|   "polite"]                                  |
|  ⚠  ยอดเงินอาจไม่เป็นปัจจุบัน                |
|  ระบบใช้ข้อมูลล่าสุดที่บันทึกไว้ ลองรีเฟรช    |
|  อีกครั้งในอีกสักครู่                         |
|  [RetrySoftButton "ลองอีกครั้ง"]              |
+----------------------------------------------+
| [AccountList]                                |
|                                              |
|  +----------------------------------------+  |
|  | [AccountRow rank=1]                    |  |
|  | [TypeIcon] Savings · *7890             |  |
|  |            ฿128,540.25  THB            |  |
|  |            Updated 2 minutes ago       |  |
|  +----------------------------------------+  |
|                                              |
|  +----------------------------------------+  |
|  | [AccountRow rank=2  STALE]             |  |
|  | [TypeIcon] Fixed Deposit · *4421       |  |
|  |            ฿84,012.00  THB             |  |
|  |            Updated 1 day ago           |  |
|  |            [StaleBadge "อาจไม่ใช่ยอด    |  |
|  |             ล่าสุด"]                    |  |
|  +----------------------------------------+  |
|                                              |
|  +----------------------------------------+  |
|  | [AccountRow rank=3]                    |  |
|  | [TypeIcon] Current · *0033             |  |
|  |            ฿12,005.55  THB             |  |
|  |            Updated 12 seconds ago      |  |
|  +----------------------------------------+  |
|                                              |
+----------------------------------------------+
```

**Annotations (Screen 4):**

- **Global banner** appears when the WHOLE response came from a degraded path (e.g., AccountClient downstream snapshot served, or fail-open with stale Redis blob beyond TTL — defensive). Decision rule (proposed for SA/TL confirmation): server sets a response header `X-Data-Freshness: snapshot|stale`, OR includes `meta.freshness="snapshot"` in payload. **Flagged as open question for HI-FI / SA** in `open-questions-for-hifi.md`.
- **Per-row badge** appears when `now() - balance_as_of > 60s` (default threshold per BR-013). Independent of the global banner — a single row can be stale while others are fresh (e.g., FIXED_DEPOSIT, AC-002-E1).
- Banner is dismissible (closes for the session) but reappears on next fetch if condition persists. `aria-live="polite"` so screen readers announce without interrupting.
- `RetrySoftButton` triggers the same refetch as pull-to-refresh.
- `balance_as_of` null → row shows `—` (em dash, AC-002-E2). Out of focus for this screen but documented as a variant of the row component.

---

## Screen 5 — Hard error / retry

```
<------------------- 375px ------------------->
+----------------------------------------------+
| [PageHeader]                                 |
|  บัญชีของฉัน                                 |
+----------------------------------------------+
|                                              |
|         +-----------------------+            |
|         |   [ErrorIllustration] |            |
|         |     (placeholder)     |            |
|         +-----------------------+            |
|                                              |
|         เกิดข้อผิดพลาดในการโหลดข้อมูล         |
|                                              |
|         กรุณาลองใหม่อีกครั้ง                  |
|         หรือกลับมาภายในไม่กี่นาที             |
|                                              |
|         [PrimaryButton "ลองใหม่"]            |
|                                              |
|         [SmallText]                          |
|         รหัสอ้างอิง: <correlationId>          |
|         (สำหรับติดต่อทีมงาน)                  |
|                                              |
+----------------------------------------------+
```

**Annotations (Screen 5):**

- Triggers: HTTP 503 from balance-dashboard-service after retries exhausted (AccountClient down). Also catches unexpected 5xx + network-offline (FE concern).
- `correlationId` (OTel trace) shown small-font so customer can quote it to support; it does NOT contain PII.
- Button announces busy state when retrying (`aria-busy="true"` for the period).
- No technical jargon ("Redis", "503") in customer-facing copy.
- 401 does NOT land here — auth interceptor handles redirect to login (AC-001-E3; out of dashboard scope).
- 403 (IDOR attempt) is not reachable via normal UI; if it occurs, fall through to this generic screen — DO NOT mention that another customer was attempted.

---

## Screen 6 — Single-account state

```
<------------------- 375px ------------------->
+----------------------------------------------+
| [PageHeader]                                 |
|  บัญชีของฉัน                                 |
|  1 account                                   |
+----------------------------------------------+
| [PullToRefreshAffordance]                    |
+----------------------------------------------+
| [AccountList role="list" aria-label=         |
|  "your account"]                             |
|                                              |
|  +----------------------------------------+  |
|  | [AccountRow rank=1 of 1  (rank hidden  |  |
|  |   visually but announced for SR)]      |  |
|  | [TypeIcon]  Savings · *7890            |  |
|  |             ฿45,000.00  THB            |  |
|  |             Updated just now           |  |
|  +----------------------------------------+  |
|                                              |
+----------------------------------------------+
```

**Annotations (Screen 6):**

- No "ranking" visual chrome (rank numeral, rank badge) — it would feel awkward with one row.
- Screen reader announcement softened: "Your savings account, ending 7890, balance forty-five thousand baht, updated just now." (instead of "Account 1 of 1").
- AccountList still uses `role="list"` for consistency with assistive-tech mental model.
- Row remains tappable (exit to account detail).
- Header subtitle says "1 account" (not "1 of 1").

---

## Screen 7 — 10-account density

```
<------------------- 375px ------------------->
+----------------------------------------------+
| [PageHeader]                                 |
|  บัญชีของฉัน                                 |
|  10 accounts                                 |
+----------------------------------------------+
| [PullToRefreshAffordance]                    |
+----------------------------------------------+
| [AccountList role="list"]                    |
|                                              |
|  [AccountRow #1   ฿1,200,000.00  Just now ]  |
|  [AccountRow #2   ฿  860,500.00  2 min ago]  |
|  [AccountRow #3   ฿  500,000.00  5 min ago]  |
|  [AccountRow #4   ฿  420,000.00  1 hr ago ]  |
|  [AccountRow #5   ฿  180,000.00  Just now ]  |
|  [AccountRow #6   ฿   95,200.00  3 hr ago ]  |
|  [AccountRow #7   ฿   42,000.00  1 day ago]  |
|     (per-row STALE badge if > 60s threshold) |
|  --- scroll boundary; below the fold ---     |
|  [AccountRow #8   ฿   18,000.00  Just now ]  |
|  [AccountRow #9   ฿    2,500.00  5 min ago]  |
|  [AccountRow #10  ฿       12.55  10 min   ]  |
|                                              |
+----------------------------------------------+
```

**Annotations (Screen 7):**

- Rows scroll vertically. No sticky header in v1 (deferred — see interaction-spec).
- Row anatomy is identical to Screen 1 — no compression / truncation tricks that hide masked-number, balance, or last-updated.
- Confirm AC-005-H1 (no horizontal scrolling) at 375px even with 10 rows.
- Confirm AC-005-E1 (200% font reflow) — row-internal stack wraps gracefully; tested in Phase 2 HI-FI on a real component.
- If list exceeds memory budget (unlikely at N=10), Angular `@for` track-by-`accountId` keeps rendering performant.

---

## Component inventory (Phase 1 LO-FI — names will lock in HI-FI Phase 2)

| Component | Used in | LO-FI role | HI-FI to add |
|---|---|---|---|
| `PageHeader` | 1, 2, 3, 4, 5, 6, 7 | Title + subtitle | Final typography tokens |
| `PullToRefreshAffordance` | 1, 4, 6, 7 | Hint area + gesture handler | Animation, reduced-motion variant |
| `AccountList` | 1, 4, 6, 7 | `role="list"` semantic wrapper | (none structural) |
| `AccountRow` | 1, 4, 6, 7 | `role="listitem"`; rank, type, masked number, balance, last-updated | Type icon, colour, typography, focus ring |
| `StaleBadge` | 4 | Inline per-row stale flag | Colour + icon |
| `StalenessBanner` | 4 | Top-of-list global banner (snapshot served) | Colour + dismiss control |
| `EmptyState` | 3 | Illustration + Thai copy + CTA placeholder | Illustration asset |
| `ErrorState` | 5 | Illustration + retry button + correlationId | Illustration asset |
| `SkeletonRow` | 2 | Shimmerless placeholder mirroring AccountRow | Tokenised colour |
| `RetrySoftButton` | 4 | Inline secondary action | Button tokens |
| `PrimaryButton` | 5 | "ลองใหม่" | Button tokens |
| `LastUpdatedLabel` | 1, 4, 6, 7 | Relative time + reveal absolute on long-press/hover | Pop-over micro-interaction |
| `MaskedAccountLabel` | 1, 4, 6, 7 | `*XXXX` rendering primitive | Typography |
| `BalanceAmount` | 1, 4, 6, 7 | Formatted decimal + `aria-label` full spoken form | Currency-aware formatter |
| `AccountTypeIcon` | 1, 4, 6, 7 | Placeholder square in LO-FI | Final icons per type |

---

## Cross-screen rules baked in

1. **Masking everywhere:** No screen, no state, no skeleton ever displays a non-masked account number.
2. **No total / aggregate:** No screen shows a sum across accounts (OPEN-001).
3. **Audit is invisible:** UI doesn't display audit status; the customer doesn't need to know. BA's BR-014 is a backend concern.
4. **`balance_as_of` truth:** Every "Updated …" label is sourced from `balance_as_of`, not `updated_at` (OPEN-002). No exception.
5. **Dormant/closed hidden:** No screen shows greyed-out / hidden indicator for excluded accounts (OPEN-003). Empty-state copy explains the rule once.
6. **Three account types only:** No row template for Loan or Credit Card (OPEN-004).
7. **Deterministic ranking:** Same input ⇒ same order across reloads (AC-001-H2).
8. **Mobile-first:** Every screen designed at 375px first; tablet/desktop is a follow-up sprint concern.

---

## Cross-references

- Journey map: `user-journey.md`
- Interaction details: `interaction-spec-lofi.md`
- Accessibility checklist: `accessibility-notes.md`
- Open questions to resolve in HI-FI: `open-questions-for-hifi.md`
- BA AC source: `docs/ba/balance-comparison/user-stories.md`
