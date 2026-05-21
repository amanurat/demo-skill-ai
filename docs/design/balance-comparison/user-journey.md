# User Journey Map — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Phase:** Phase 1 — LO-FI / Discovery
> **Persona:** Retail customer, multi-account holder (3-5 active accounts typical; up to 10 supported)
> **Device baseline:** Mobile, 375 x 812px (iPhone SE / typical Thai mobile)
> **Language:** UI micro-copy Thai-first (English equivalents specified in HI-FI Phase 2)

---

## 1. Persona snapshot

| Trait | Detail |
|---|---|
| Name | "Khun Ploy" (composite persona) |
| Age band | 28-45 |
| Banking profile | 3-5 active accounts across SAVINGS / CURRENT / FIXED_DEPOSIT |
| Channel | Mobile web (Angular standalone component, lazy route) |
| Goal | "ดูยอดทุกบัญชีที่บัญชีไหนเงินเยอะสุดเพื่อตัดสินใจโยกเงิน" |
| Frequency | 1-3 times per week, often before paying bills or moving savings |
| Tech comfort | Mid — trusts the bank app, skeptical of stale numbers |
| Accessibility | A subset use VoiceOver/TalkBack or 200% font scale |

---

## 2. Entry points

| # | Entry | Trigger | Lands on |
|---|---|---|---|
| E1 | Home tab "บัญชีของฉัน" / "My Accounts" | Tap from authenticated home shelf | Screen 1 (default) or Screen 2 (loading) |
| E2 | Deep link from notification | e.g., "ยอดเงินเปลี่ยนแปลง" push (out of v1 scope — placeholder) | Screen 2 (loading) → Screen 1 |
| E3 | Side-menu "เปรียบเทียบยอดเงิน" | Tap nav | Screen 2 → Screen 1 |
| E4 | Re-entry after backgrounding the app | App resumes; cache may be cold | Screen 2 → Screen 1 (or Screen 4 if stale) |

**Out of scope entry points (v1):** dashboard widget on home, watch face, web-desktop bookmark.

---

## 3. Primary journey (happy path — 3-5 accounts, warm cache)

| Step | Actor | Action | Screen | Goal | Emotion | Pain points | Opportunities |
|---|---|---|---|---|---|---|---|
| 1 | Customer | Authenticated on home, taps "บัญชีของฉัน" | Home (out of scope) → transitions to balance-dashboard | Get a quick overview | Curious / hopeful | Worry that data is stale | Show "Updated X seconds ago" prominently |
| 2 | System | Loads route (lazy-loaded standalone component) | Screen 2 — Loading skeleton | Reassure that data is coming | Patient if < 800ms | Long wait = abandon | Skeleton density should match the real list (avoid layout shift) |
| 3 | System | Renders ranked list (balance DESC, accountId ASC tie-break) | Screen 1 — Default | Show ranking + per-row details | Pleased | Confusion if labels are enum strings | Use friendly Thai labels (BR-008) |
| 4 | Customer | Scans rows top-to-bottom | Screen 1 | Compare balances at a glance | Confident | Crowded layout on small screens | Mobile-first column rhythm (BR-019) |
| 5 | Customer | Reads "Updated: 2 minutes ago" on a row | Screen 1 | Validate freshness | Trust | Ambiguous "updated_at" vs "balance_as_of" | OPEN-002 resolved: surface ledger truth |
| 6 | Customer | Long-presses / hovers "Updated" label | Screen 1 (tooltip) | See absolute timestamp | Validated | Hover not always available on touch | Tap-to-reveal tooltip for touch (HI-FI to confirm) |
| 7 | Customer | Taps a row | Exit — to account detail (OUT OF SCOPE for v1 design) | Drill in | Decisive | N/A (exit) | Hand off to account-detail feature |
| 8 | Customer | Backgrounds the app, returns 35s later | Screen 1 stale → Screen 2 refresh → Screen 1 (cold cache) | Re-validate | Mild impatience | If > 800ms p95, perceived as slow | Pull-to-refresh affordance available |

**Touchpoints across the journey:**

```
Mobile login (existing) → Home shelf → [Balance Dashboard route]
                                          ↓
                              GET /v1/balance-dashboard (Bearer JWT)
                                          ↓
                              Render ranked list w/ live or cached data
                                          ↓
                              Tap row → exit to account-detail (out of scope)
```

---

## 4. Alternate journeys (empty / error / degraded states)

### J-Alt-1 — No active accounts (Screen 3)
- **Trigger:** Customer has 0 ACTIVE SAVINGS/CURRENT/FIXED_DEPOSIT accounts (e.g., new customer, or only loan/credit-card).
- **Path:** E1 → Screen 2 (briefly) → Screen 3.
- **Emotion:** Surprise → confusion ("ทำไมไม่มีบัญชีเลย?").
- **Pain:** Empty screen feels broken if no copy.
- **Opportunity:** Friendly Thai message + clear "what to do next" CTA placeholder (link to product catalog — but OUT OF SCOPE for v1; show static "ยังไม่มีบัญชีที่ใช้งานอยู่").
- **AC mapped:** AC-001-E1 (HTTP 200 + empty array + audit emitted).

### J-Alt-2 — Single-account customer (Screen 6)
- **Trigger:** Customer has exactly 1 active in-scope account.
- **Path:** E1 → Screen 2 → Screen 6.
- **Emotion:** Neutral.
- **Pain:** "Ranking" visual implies multiple rows; one row alone can feel awkward.
- **Opportunity:** Drop "Account 1 of 1" rank affordance for screen reader (use simpler announcement: "Your account"); visually omit rank number.

### J-Alt-3 — 10-account power user (Screen 7)
- **Trigger:** Customer has many accounts (max 10 for v1 fixture).
- **Path:** E1 → Screen 2 → Screen 7.
- **Emotion:** Overwhelmed at first.
- **Pain:** Density on 375px viewport; scrolling fatigue.
- **Opportunity:** Verify scroll is smooth; consider visual rhythm (subtle row separator); reaffirm "no horizontal scroll" rule (BR-019).

### J-Alt-4 — Stale data warning (Screen 4)
- **Trigger:** Any account's `balance_as_of` is older than the staleness threshold (default 60s; configurable per NFR §2). Often a `FIXED_DEPOSIT` that has not been touched in days, OR Redis/AccountClient degradation snapshot.
- **Path:** E1 → Screen 2 → Screen 4 (banner + flagged row).
- **Emotion:** Concerned — wants reassurance the number is "good enough".
- **Pain:** Customer might think the app is broken.
- **Opportunity:** Inline "อาจไม่ใช่ยอดล่าสุด" badge per stale row + global banner if AccountClient is degraded. AC-002-E1.

### J-Alt-5 — Hard error / retry (Screen 5)
- **Trigger:** HTTP 503 (AccountClient unavailable after retries) or unexpected error after fail-open. Note: Redis-only failure does NOT reach this screen (fail-open behaviour per BR-015 / AC-003-E1).
- **Path:** E1 → Screen 2 → Screen 5.
- **Emotion:** Frustrated / worried.
- **Pain:** Generic "error" copy erodes trust.
- **Opportunity:** Specific, friendly Thai message + manual retry button + correlation id for support.

### J-Alt-6 — Unauthenticated (HTTP 401)
- **Trigger:** Expired JWT (AC-001-E3).
- **Path:** Auto-redirect to login flow (owned by existing auth feature — OUT OF SCOPE for balance-dashboard).
- **Note for design:** Dashboard component must defer to auth interceptor; no in-screen error here.

### J-Alt-7 — IDOR / 403 attempt
- **Trigger:** Customer attempts to supply someone else's `customerId` (AC-001-E2). Realistically only reachable via tampering — not from official UI.
- **Path:** Cannot reach via normal UI; if surfaced, treat as generic Screen 5 (do NOT reveal the existence of other customers).

---

## 5. Emotion curve (textual)

```
Trust ───────────────────────────────────────────────
       (high) E1 ─── Skel ─── Render ──── Tap row ▶ exit
                                       \
                                        + Stale row ── slight dip
                                       /
       (mid)  ──────────────────── Pull-to-refresh recover
       (low)  ─────────────── Screen 5 error (rare)
```

---

## 6. Pain → Opportunity summary (top 5)

| Pain | Opportunity (Phase 1 design intent) | AC anchor |
|---|---|---|
| "Is this number current?" | Always show ledger `balance_as_of` (relative time) + tap for absolute | AC-002-H1, AC-003-H4, OPEN-002 |
| "Where's my account number?" | Always masked `****XXXX`; never full | BR-007, AC-002-H3 |
| "Why are these ordered like this?" | Visual rank position + deterministic tie-break note for screen readers | AC-001-H2, BR-002 |
| "Layout breaks on my phone / large font" | Mobile-first 375px; 200% font reflow tested | AC-005-H1, AC-005-E1, BR-019 |
| "I can't tell if data is fresh after switching apps" | Pull-to-refresh + stale badge when threshold exceeded | AC-002-E1, AC-003-H4 |

---

## 7. Exits / hand-off boundaries

- **In-scope exits:** Tap row → navigate to account detail (out-of-scope feature). Pull-to-refresh stays within the dashboard.
- **Out-of-scope exits:** Transaction CTAs, transfer-from-account shortcuts, history/sparkline drill-downs, filter/sort overrides — all explicitly excluded per BA out-of-scope list.

---

## 8. Cross-references

- BA stories: `docs/ba/balance-comparison/user-stories.md` (US-BC-001 / 002 / 003 / 005)
- BA NFR (a11y, mobile, staleness, fail-open): `docs/ba/balance-comparison/nfr.md`
- BA glossary (warm/cold cache, staleness threshold): `docs/ba/balance-comparison/glossary.md`
- Wireframes for each screen: `docs/design/balance-comparison/wireframes-lofi.md`
- Interaction details: `docs/design/balance-comparison/interaction-spec-lofi.md`
- Accessibility checklist: `docs/design/balance-comparison/accessibility-notes.md`
