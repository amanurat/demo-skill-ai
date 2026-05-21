# Test Cases — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Phase:** QA P1 (test case catalog — no automation code yet)
> **Total cases:** 71 (22 unit, 18 integration, 4 contract, 8 E2E, 5 perf, 8 security, 6 a11y)
> **Input:** BA-001 (`c4e9f1b2-7a3d-4c85-b6f0-1e8d2a5c9047`)

---

## Priority Legend

| Priority | Meaning |
|---|---|
| P0 | Demo blocker — must be automated and green before sprint demo |
| P1 | High value — must be automated; failure blocks QA sign-off |
| P2 | Nice-to-have in this sprint; carry forward if time-boxed |

---

## Story: US-BC-001 — List My Accounts Ranked by Balance

### TC-UNIT-001
**Priority:** P0
**Type:** Unit
**AC covered:** AC-001-H1
**Title:** `should_rank_accounts_by_balance_desc_when_multiple_active_accounts`
**Preconditions:** 3 AccountInfo objects with distinct balances (50000, 30000, 10000 THB), all ACTIVE, in-scope types
**Steps:**
1. Construct `BalanceDashboardService` with mocked AccountClient returning the 3 accounts
2. Call `rankAccounts(List<AccountInfo>)`
3. Assert result ordering
**Expected:** Accounts returned in order: [50000, 30000, 10000]; HTTP 200 with non-empty `accounts` array
**Notes:** No Spring context; pure logic test

### TC-UNIT-002
**Priority:** P0
**Type:** Unit
**AC covered:** AC-001-E1
**Title:** `should_return_empty_list_when_customer_has_no_active_in_scope_accounts`
**Preconditions:** AccountClient returns empty list for customerId
**Steps:**
1. Mock AccountClient to return `[]`
2. Call service layer
3. Assert response
**Expected:** `accounts = []`; no exception thrown; downstream audit still triggered (verify mock called)

### TC-UNIT-003
**Priority:** P0
**Type:** Unit
**AC covered:** AC-002-H2, SUBDEC-001
**Title:** `should_display_balance_from_account_info_regardless_of_fixed_deposit_composition`
**Preconditions:** AccountInfo for FIXED_DEPOSIT with balance = 125000.00 (whatever AccountClient returns)
**Steps:**
1. Feed AccountInfo with FIXED_DEPOSIT type and balance 125000.00 to response mapper
2. Assert mapped response
**Expected:** Response balance = 125000.00; label = "Fixed Deposit Account" / "บัญชีเงินฝากประจำ"; no raw enum "FIXED_DEPOSIT"
**Notes:** Does not assert composition (principal vs principal+accrued) — SA to decide; test asserts passthrough

### TC-UNIT-004
**Priority:** P0
**Type:** Unit
**AC covered:** BR-004, OPEN-004
**Title:** `should_exclude_loan_and_credit_accounts_from_ranked_list`
**Preconditions:** AccountClient returns SAVINGS + CURRENT + FIXED_DEPOSIT + LOAN + CREDIT_CARD accounts
**Steps:**
1. Feed 5-account list through `AccountTypeFilter.filter()`
2. Assert output
**Expected:** Output contains only SAVINGS, CURRENT, FIXED_DEPOSIT; LOAN and CREDIT_CARD are absent; `balance_dashboard_excluded_accounts_total` metric incremented

### TC-UNIT-005
**Priority:** P0
**Type:** Unit
**AC covered:** AC-001-H2
**Title:** `should_break_ties_by_accountId_asc_when_balances_are_equal`
**Preconditions:** 3 accounts: accountId="aaaaa", "ccccc", "bbbbb", all with balance = 45000.00
**Steps:**
1. Call `RankingService.rank(accounts)`
2. Assert order
**Expected:** Order is ["aaaaa", "bbbbb", "ccccc"] — ascending UUID lexicographic order when balances equal

### TC-UNIT-006
**Priority:** P1
**Type:** Unit
**AC covered:** AC-002-H1, BR-011
**Title:** `should_format_balance_as_thai_baht_with_symbol_and_2_decimal_places`
**Preconditions:** balance = 45000.00, currency = THB
**Steps:**
1. Call `BalanceFormatter.format(45000.00, "THB")`
2. Assert
**Expected:** Returns "฿45,000.00"

### TC-UNIT-007
**Priority:** P0
**Type:** Unit
**AC covered:** AC-002-H3, BR-007
**Title:** `should_mask_account_number_to_last_4_digits_in_all_surfaces`
**Preconditions:** Full accountNumber = "1234567890"
**Steps:**
1. Call `AccountNumberMasker.mask("1234567890")`
2. Assert
**Expected:** Returns "******7890"; full number not present in output string

### TC-UNIT-008
**Priority:** P0
**Type:** Unit
**AC covered:** AC-002-H4, BR-008
**Title:** `should_map_account_type_enum_to_human_readable_label_thai_and_english`
**Preconditions:** accountType values: SAVINGS, CURRENT, FIXED_DEPOSIT
**Steps:**
1. Call `AccountTypeLabel.getLabel(accountType, locale)` for each type + each locale (TH, EN)
2. Assert
**Expected:**
- SAVINGS → "บัญชีออมทรัพย์" (TH) / "Savings Account" (EN)
- CURRENT → "บัญชีกระแสรายวัน" (TH) / "Current Account" (EN)
- FIXED_DEPOSIT → "บัญชีเงินฝากประจำ" (TH) / "Fixed Deposit Account" (EN)
- No raw enum string visible in any output

### TC-UNIT-009
**Priority:** P1
**Type:** Unit
**AC covered:** AC-002-E1, BR-009, BR-013
**Title:** `should_show_stale_indicator_when_balance_as_of_exceeds_staleness_threshold`
**Preconditions:** balance_as_of = 24h ago; staleness threshold = 60s
**Steps:**
1. Call `StalenessIndicator.isStale(balance_as_of, Instant.now(), threshold=60s)`
2. Assert
**Expected:** Returns `true` (stale); "Last updated" is derived from balance_as_of value, not current timestamp

### TC-UNIT-010
**Priority:** P1
**Type:** Unit
**AC covered:** AC-002-E2
**Title:** `should_show_dash_for_last_updated_when_balance_as_of_is_null`
**Preconditions:** AccountInfo with balance_as_of = null
**Steps:**
1. Map AccountInfo through response mapper with null balance_as_of
2. Assert
**Expected:** `lastUpdated` field = "–" or null (mapped to "Not available" in UI); account still included in list; warning log emitted with accountId

### TC-UNIT-011
**Priority:** P1
**Type:** Unit (Frontend — Angular pipe)
**AC covered:** AC-002-H1
**Title:** `should_format_relative_time_from_balance_as_of_timestamp`
**Preconditions:** balance_as_of = 3 hours before current time
**Steps:**
1. Invoke Angular `RelativeTimePipe.transform(balance_as_of)`
2. Assert displayed string
**Expected:** Returns "3 hours ago" (or locale equivalent); absolute ISO timestamp available on hover

### TC-UNIT-012
**Priority:** P1
**Type:** Unit (Frontend — Angular pipe)
**AC covered:** AC-001-H4
**Title:** `should_display_native_currency_code_without_fx_conversion`
**Preconditions:** AccountInfo with currency = "THB"; no FX rate configured
**Steps:**
1. Render `<balance-row [account]="account">` with THB account
2. Assert DOM
**Expected:** "THB" currency code displayed; no total-in-home-currency element present; no FX conversion values

### TC-UNIT-013
**Priority:** P1
**Type:** Unit
**AC covered:** BR-003, OPEN-003
**Title:** `should_exclude_dormant_and_closed_accounts_from_ranked_list`
**Preconditions:** AccountInfo list with ACTIVE + DORMANT + CLOSED + FROZEN accounts
**Steps:**
1. Call `AccountStatusFilter.filter(accounts)`
2. Assert
**Expected:** Only ACTIVE accounts remain; DORMANT, CLOSED, FROZEN excluded; filter count metric incremented

### TC-UNIT-014
**Priority:** P1
**Type:** Unit
**AC covered:** AC-003-H4, BR-013
**Title:** `should_preserve_balance_as_of_from_cache_payload_not_cache_hit_timestamp`
**Preconditions:** Cached AccountInfo has balance_as_of = T-5min; current time = T
**Steps:**
1. Deserialize cache payload
2. Map to response DTO
3. Assert balance_as_of value
**Expected:** balance_as_of in response = T-5min (from cache); not the current time T

### TC-UNIT-015 (Frontend)
**Priority:** P1
**Type:** Unit (Angular component)
**AC covered:** AC-005-E2, BR-022
**Title:** `should_set_aria_label_to_full_spoken_amount_on_balance_element`
**Preconditions:** balance = 45000.00, currency = THB
**Steps:**
1. Render balance element in Angular test
2. Query `aria-label` attribute
**Expected:** `aria-label="45,000 baht"` (full spoken form); not "4", "5", "0"... individual characters

### TC-UNIT-016 (Frontend)
**Priority:** P1
**Type:** Unit (Angular component)
**AC covered:** AC-005-H3, BR-020
**Title:** `should_set_tabindex_on_each_account_row_for_keyboard_navigation`
**Preconditions:** 3 account rows rendered
**Steps:**
1. Render `<balance-dashboard>` with 3 accounts
2. Assert tabindex and DOM order
**Expected:** Each row has tabindex="0"; DOM order matches rank order 1 → 2 → 3

### TC-UNIT-017
**Priority:** P2
**Type:** Unit
**AC covered:** BR-005
**Title:** `should_emit_audit_event_even_when_accounts_list_is_empty`
**Preconditions:** Customer has no active accounts
**Steps:**
1. Call service with empty AccountClient response
2. Verify audit event publisher called
**Expected:** `auditEventPublisher.publish()` called once with accountCount = 0

### TC-UNIT-018
**Priority:** P2
**Type:** Unit (Frontend)
**AC covered:** AC-002-H1
**Title:** `should_display_hover_tooltip_with_absolute_timestamp_on_last_updated`
**Preconditions:** balance_as_of = "2026-05-21T08:00:00Z" (UTC)
**Steps:**
1. Render last-updated element in Angular test
2. Assert tooltip/title attribute
**Expected:** Tooltip = "21 May 2026 15:00 ICT" (UTC+7 conversion); hover attribute present

---

## Story: US-BC-001 — Integration Tests

### TC-INT-001
**Priority:** P0
**Type:** Integration
**AC covered:** AC-001-H1
**Title:** `should_return_200_with_ranked_accounts_for_authenticated_customer_with_3_accounts`
**Preconditions:** CUST-T03 seeded in AccountClient mock (WireMock); Redis empty; valid JWT for CUST-T03
**Steps:**
1. `GET /v1/balance-dashboard` with `Authorization: Bearer {jwt}`
2. Assert response
**Expected:** HTTP 200; `accounts` array length = 3; ordered by balance DESC; each entry has accountId, masked accountNumber, accountType, balance, currency, balance_as_of

### TC-INT-002
**Priority:** P0
**Type:** Integration
**AC covered:** AC-001-E3
**Title:** `should_return_401_when_jwt_is_absent_or_expired`
**Preconditions:** No JWT header; or JWT with expired `exp` claim
**Steps:**
1. `GET /v1/balance-dashboard` without Authorization header
2. `GET /v1/balance-dashboard` with expired JWT
3. Assert both responses
**Expected:** HTTP 401 on both calls; no account data in response body

### TC-INT-003
**Priority:** P0
**Type:** Integration
**AC covered:** AC-001-H1 (scope check)
**Title:** `should_return_403_when_jwt_scope_lacks_accounts_read`
**Preconditions:** Valid JWT but with scope = "transfers:write" only (no accounts:read)
**Steps:**
1. `GET /v1/balance-dashboard` with JWT missing `accounts:read` scope
**Expected:** HTTP 403; no account data

### TC-INT-004
**Priority:** P0
**Type:** Integration
**AC covered:** AC-001-H2
**Title:** `should_maintain_deterministic_tie_break_ordering_across_multiple_requests_within_TTL`
**Preconditions:** CUST-T05 (2 accounts with equal balance); Redis primed with first response
**Steps:**
1. `GET /v1/balance-dashboard` → record order
2. `GET /v1/balance-dashboard` again (cache hit within TTL) → record order
3. Compare both response orders
**Expected:** Both responses return accounts in identical accountId ASC order within the tied-balance group; ordering is deterministic and never changes within TTL

### TC-INT-005
**Priority:** P0
**Type:** Integration
**AC covered:** BR-004, OPEN-004
**Title:** `should_exclude_loan_accounts_when_account_service_returns_mixed_types`
**Preconditions:** CUST-T07 (WireMock returns SAVINGS + CURRENT + LOAN types)
**Steps:**
1. `GET /v1/balance-dashboard` with CUST-T07 JWT
2. Assert response accounts
**Expected:** Response contains only SAVINGS and CURRENT; LOAN absent; `balance_dashboard_excluded_accounts_total` metric incremented by 1

### TC-INT-006
**Priority:** P0
**Type:** Integration
**AC covered:** BR-003, OPEN-003 (dormant/closed filter)
**Title:** `should_exclude_dormant_and_closed_accounts_and_still_emit_audit`
**Preconditions:** CUST-T06 (WireMock returns 2 ACTIVE + 1 DORMANT + 1 CLOSED)
**Steps:**
1. `GET /v1/balance-dashboard` with CUST-T06 JWT
2. Assert response and audit event
**Expected:** Response contains 2 ACTIVE accounts only; DORMANT and CLOSED absent; audit event emitted with accountCount = 2 (not 4); `balance_dashboard_excluded_accounts_total` metric incremented

### TC-INT-007
**Priority:** P0
**Type:** Integration
**AC covered:** AC-001-E1 (zero accounts)
**Title:** `should_return_200_empty_array_and_emit_audit_when_customer_has_no_active_accounts`
**Preconditions:** CUST-T01 (WireMock returns empty list); valid JWT
**Steps:**
1. `GET /v1/balance-dashboard` with CUST-T01 JWT
**Expected:** HTTP 200; `accounts = []`; UI empty-state indicator in API metadata; audit event emitted with accountCount = 0

### TC-INT-008
**Priority:** P1
**Type:** Integration
**AC covered:** AC-002-E2
**Title:** `should_include_account_with_null_balance_as_of_in_list_with_dash_label_and_warn_log`
**Preconditions:** CUST-T08; WireMock returns AccountInfo with balance_as_of = null
**Steps:**
1. `GET /v1/balance-dashboard`
2. Assert response and log output
**Expected:** Account still included in response; `lastUpdated` = null / "–"; WARN log contains accountId; no ERROR thrown; HTTP 200

### TC-INT-009
**Priority:** P0
**Type:** Integration (banking-specific: audit on cache HIT)
**AC covered:** AC-001-H3, AC-003-H3, BR-014
**Title:** `should_emit_audit_event_on_cache_hit_with_cacheHit_true`
**Preconditions:** Redis pre-populated with CUST-T03 data; valid JWT
**Steps:**
1. Warm cache via prior request
2. Make second request (cache hit)
3. Consume Kafka audit topic with Awaitility
4. Assert event fields
**Expected:** Audit event on Kafka topic with eventType=BALANCE_INQUIRY, actorId=customerId, cacheHit=true, result=SUCCESS, purpose=balance-inquiry, correlationId present, timestamp UTC

### TC-INT-010
**Priority:** P0
**Type:** Integration (banking-specific: audit on cache MISS)
**AC covered:** AC-001-H3, BR-005, BR-014
**Title:** `should_emit_audit_event_on_cache_miss_with_cacheHit_false`
**Preconditions:** Redis empty; WireMock returning CUST-T03 accounts; valid JWT
**Steps:**
1. Flush Redis to ensure cold cache
2. `GET /v1/balance-dashboard` (cache miss)
3. Consume audit Kafka topic with Awaitility (timeout 2s)
4. Assert event fields
**Expected:** Audit event with cacheHit=false, result=SUCCESS, accountCount=3; NOT `Thread.sleep` — use Awaitility

### TC-INT-011
**Priority:** P0
**Type:** Integration (banking-specific: GET idempotency)
**AC covered:** AC-001-H2, AC-003-H4
**Title:** `should_return_identical_response_for_repeated_requests_within_cache_ttl`
**Preconditions:** CUST-T04 (10 accounts); Redis warm; valid JWT
**Steps:**
1. `GET /v1/balance-dashboard` → capture full response body (accounts array + balance_as_of values)
2. `GET /v1/balance-dashboard` again (< 30s later) → capture response body
3. Compare both bodies
**Expected:** Both responses are byte-identical for the accounts array; balance_as_of values identical (not replaced by request time); ordering identical

### TC-INT-012
**Priority:** P0
**Type:** Integration
**AC covered:** AC-003-E1, BR-015
**Title:** `should_fail_open_to_account_client_when_redis_is_unavailable`
**Preconditions:** Redis container stopped; WireMock returning CUST-T03 accounts; valid JWT
**Steps:**
1. Stop Redis Testcontainer
2. `GET /v1/balance-dashboard`
3. Assert response, metrics, audit
**Expected:** HTTP 200 with account data (from AccountClient fallback); no Redis error in response; `cache_miss_reason=REDIS_UNAVAILABLE` metric emitted; audit event still emitted

### TC-INT-013
**Priority:** P1
**Type:** Integration (banking-specific: single batched call)
**AC covered:** AC-003-H2, BR-016
**Title:** `should_issue_single_batched_account_client_call_not_n_round_trips`
**Preconditions:** WireMock for AccountClient; CUST-T04 (10 accounts); Redis cold
**Steps:**
1. `GET /v1/balance-dashboard` (cold cache)
2. Query WireMock request journal
**Expected:** WireMock receives exactly 1 request to `/accounts?customerId={id}` (or equivalent batch endpoint); NOT 10 individual account requests

### TC-INT-014
**Priority:** P1
**Type:** Integration (banking-specific: stale-snapshot degraded mode)
**AC covered:** AC-002-E1, BR-013
**Title:** `should_return_stale_snapshot_with_staleness_flag_when_balance_as_of_exceeds_threshold`
**Preconditions:** Cached entry has balance_as_of = 2 minutes ago; staleness threshold = 60s
**Steps:**
1. Pre-populate Redis with account data having old balance_as_of
2. `GET /v1/balance-dashboard`
3. Assert response fields
**Expected:** HTTP 200; response includes `stale: true` flag (or equivalent field); balance_as_of value matches cached value (not overridden); data still returned

### TC-INT-015
**Priority:** P1
**Type:** Integration (banking-specific: circuit breaker)
**AC covered:** BA NFR resilience — circuit breaker on AccountClient
**Title:** `should_open_circuit_breaker_after_n_failures_and_return_cached_snapshot`
**Preconditions:** Resilience4j CB configured (threshold from SA decision, TBD); WireMock returning 500 errors; Redis has cached snapshot
**Steps:**
1. Trigger N consecutive AccountClient failures via WireMock 500 stubs
2. Assert CB state opens
3. `GET /v1/balance-dashboard` while CB is open
**Expected:** CB opens after N failures; service returns cached Redis snapshot with `stale: true`; no AccountClient call made while CB is open
**Notes:** **Depends-on-SA-decision (threshold N)** — test skeleton ready; assertion on N to be filled after SA ADR

### TC-INT-016
**Priority:** P1
**Type:** Integration
**AC covered:** AC-003-H2, BR-012
**Title:** `should_write_account_data_to_redis_with_30s_ttl_after_cache_miss`
**Preconditions:** Redis empty; WireMock returning CUST-T03 accounts; valid JWT
**Steps:**
1. `GET /v1/balance-dashboard` (cache miss)
2. Directly query Redis for key `customer:{customerId}:accounts`
3. Assert TTL
**Expected:** Key exists in Redis; TTL between 28–30 seconds; payload includes balance_as_of for each account

### TC-INT-017
**Priority:** P1
**Type:** Integration
**AC covered:** AC-001-H4 (no FX conversion in response)
**Title:** `should_not_include_fx_conversion_or_total_in_home_currency_in_response`
**Preconditions:** CUST-T03 (THB accounts); valid JWT
**Steps:**
1. `GET /v1/balance-dashboard`
2. Assert response JSON schema
**Expected:** Response JSON contains no fields: `totalInHomeCurrency`, `convertedBalance`, `exchangeRate`; each account shows its own `currency = "THB"` only

### TC-INT-018
**Priority:** P1
**Type:** Integration
**AC covered:** AC-001-E2 (IDOR + audit FORBIDDEN)
**Title:** `should_emit_forbidden_audit_event_when_idor_attempt_detected`
**Preconditions:** Customer A JWT; request supplies customerId belonging to Customer B
**Steps:**
1. `GET /v1/balance-dashboard?customerId={customerB.id}` with JWT of Customer A
2. Assert response and audit event
**Expected:** HTTP 403; audit event emitted with result=FORBIDDEN, actorId=customerA.id; no Customer B data in response

---

## Security Tests

### TC-SEC-001
**Priority:** P0
**Type:** Security (IDOR)
**AC covered:** AC-001-E2, OWASP A01
**Title:** `should_return_403_and_no_customer_b_data_when_customer_a_requests_customer_b_dashboard`
**Preconditions:** Customer A has valid JWT (sub = "cust-a"); Customer B has accounts
**Steps:**
1. `GET /v1/balance-dashboard` with Customer A JWT, injecting customerId = "cust-b" (path param, query param, or request header variants)
2. Assert HTTP status and response body
3. Assert audit event on Kafka
**Expected:** HTTP 403 on all injection variants; no Customer B account data in response; no Customer B data in any log; audit event result=FORBIDDEN, actorId=cust-a

### TC-SEC-002
**Priority:** P0
**Type:** Security
**AC covered:** AC-001-E3
**Title:** `should_return_401_on_all_jwt_invalid_scenarios`
**Preconditions:** Various invalid JWTs
**Steps:**
1. Request with no JWT
2. Request with expired JWT
3. Request with tampered JWT signature
4. Request with JWT signed with wrong key
**Expected:** HTTP 401 on all 4 variants; no account data returned

### TC-SEC-003
**Priority:** P0
**Type:** Security
**AC covered:** NFR Security — JWT scope enforcement
**Title:** `should_return_403_when_jwt_missing_accounts_read_scope`
**Preconditions:** Valid JWT signed with correct key, but `scope` does not include `accounts:read`
**Steps:**
1. `GET /v1/balance-dashboard` with scope-limited JWT
**Expected:** HTTP 403 Forbidden

### TC-SEC-004
**Priority:** P0
**Type:** Security (PDPA masking — API layer)
**AC covered:** AC-002-H3, BR-007
**Title:** `should_never_expose_full_account_number_in_api_json_response`
**Preconditions:** CUST-T03; full accountNumber = "1234567890" in AccountClient data
**Steps:**
1. `GET /v1/balance-dashboard`
2. Parse response JSON
3. Search entire response string for "1234567890"
**Expected:** Full account number "1234567890" absent from entire JSON body; `accountNumber` field = "******7890"

### TC-SEC-005
**Priority:** P0
**Type:** Security (PDPA — logs)
**AC covered:** BR-007, NFR Security (no balance/accountNumber in non-encrypted logs)
**Title:** `should_not_log_balance_values_or_full_account_numbers_in_application_logs`
**Preconditions:** CUST-T03 request processed (cache miss path to exercise all log points)
**Steps:**
1. Execute request
2. Capture structured log output (JSON)
3. Search for balance values ("45000", "30000") and full account number ("1234567890")
**Expected:** Balance values absent from all log entries; full account numbers absent; `accountNumber` in logs appears only as masked or not at all

### TC-SEC-006
**Priority:** P0
**Type:** Security
**AC covered:** NFR Security — feature flag
**Title:** `should_return_404_or_503_when_feature_flag_balance_dashboard_enabled_is_false`
**Preconditions:** Feature flag `balance-dashboard.enabled = false`
**Steps:**
1. Configure feature flag to false
2. `GET /v1/balance-dashboard`
**Expected:** Endpoint returns 404 or 503 (implementation choice); feature not accessible outside staging

### TC-SEC-007
**Priority:** P1
**Type:** Security (PDPA — Redis cache encryption)
**AC covered:** NFR Security — Redis encryption at rest
**Title:** `should_store_cache_payload_in_encrypted_form_not_plaintext`
**Preconditions:** Balance data written to Redis
**Steps:**
1. Execute a request that triggers cache write
2. Directly fetch Redis key value via raw RESP protocol (Testcontainers Redis)
3. Assert raw bytes are not human-readable plaintext JSON
**Expected:** Raw Redis value is AES-256-GCM encrypted blob (not plaintext JSON containing balance or accountNumber)

### TC-SEC-008
**Priority:** P1
**Type:** Security
**AC covered:** BR-006, RISK-003
**Title:** `should_verify_pdpa_consent_scope_covers_balance_inquiry_purpose`
**Preconditions:** Consent registry API available (depends on banking-security confirming registry schema)
**Steps:**
1. Lookup consent record for CUST-T03
2. Assert consent covers purpose = "balance-inquiry"
**Expected:** Consent record exists with purpose or scope covering "view own account balance information"
**Notes:** Mark as blocked pending banking-security consent-registry schema confirmation (RISK-003 early review)

---

## Contract Tests

### TC-CONTRACT-001
**Priority:** P0
**Type:** Contract (Consumer-Driven via Pact)
**AC covered:** US-BC-001, US-BC-002 dependencies
**Title:** `balance_dashboard_service_consumer_contract_with_account_service`
**Consumer:** balance-dashboard-service
**Provider:** account-service (via AccountClient)
**Interaction:** `GET /accounts?customerId={id}` returns `AccountInfo[]`
**Contract fields pinned:** accountId (UUID), accountNumber (masked, `****XXXX`), accountType (SAVINGS|CURRENT|FIXED_DEPOSIT), balance (DECIMAL 18,2), currency (CHAR 3), status (ACTIVE), balance_as_of (ISO 8601 timestamp or null)
**Expected:** Provider honors contract; consumer stubs drive integration tests
**Notes:** Pin AccountInfo contract from money-transfer feature to avoid drift; contract must be registered in Pact Broker

### TC-CONTRACT-002
**Priority:** P0
**Type:** Contract (Consumer-Driven via Pact)
**AC covered:** AC-001-H3, BR-005, BoT compliance
**Title:** `balance_dashboard_service_consumer_contract_with_audit_service`
**Consumer:** balance-dashboard-service
**Provider:** audit-service
**Interaction:** Async event `BALANCE_INQUIRY` published to Kafka topic `audit.events`
**Contract fields pinned:** eventType, actorId, purpose, channel, correlationId, timestamp, result, accountCount, cacheHit
**Expected:** audit-service provider honors the contract; balance-dashboard publishes correct fields; if existing audit-service schema does not include `cacheHit` and `purpose`, contract test surfaces the gap before merge
**Notes:** Depends on SUBDEC-003 (SA confirms schema); if new event type needed, contract test drives that decision

### TC-CONTRACT-003
**Priority:** P1
**Type:** Contract
**AC covered:** US-BC-003 (AccountClient batch call)
**Title:** `balance_dashboard_service_contract_for_batch_account_fetch`
**Consumer:** balance-dashboard-service
**Provider:** account-service
**Interaction:** Single batch call returning N accounts; contract enforces single-call response envelope
**Expected:** Provider returns all customer accounts in one response; no pagination required for N ≤ 10 accounts
**Notes:** Depends on SUBDEC-002 (SA confirms batch endpoint signature)

### TC-CONTRACT-004
**Priority:** P1
**Type:** Contract
**AC covered:** AC-003-E1 (Redis fallback path)
**Title:** `balance_dashboard_service_contract_for_account_client_error_responses`
**Interaction:** AccountClient returns 503 / timeout
**Expected:** Contract defines error response shape (Problem-Detail RFC 7807); balance-dashboard handles gracefully per fail-open policy

---

## E2E Tests

### TC-E2E-001
**Priority:** P0
**Type:** E2E (Playwright)
**AC covered:** AC-001-H1, AC-002-H1, AC-002-H4
**Title:** `should_render_ranked_dashboard_with_correct_labels_and_formatted_balances`
**Preconditions:** CUST-T03 staging fixture; valid session cookie
**Steps:**
1. Navigate to `/balance-dashboard` in browser
2. Assert visible content
**Expected:** 3 account rows visible; sorted by balance descending; Thai labels displayed (not raw enums); balance formatted "฿X,XXX.XX"; masked account numbers visible

### TC-E2E-002
**Priority:** P0
**Type:** E2E
**AC covered:** AC-001-E2 (IDOR — browser-level)
**Title:** `should_not_show_another_customers_data_even_when_id_injected_via_url`
**Preconditions:** Customer A logged in; Customer B exists with accounts
**Steps:**
1. Manipulate URL or request to supply Customer B's ID
2. Assert UI response
**Expected:** 403 page or redirect to login; Customer B's account names/balances never appear in DOM

### TC-E2E-003
**Priority:** P0
**Type:** E2E
**AC covered:** AC-001-E3
**Title:** `should_redirect_to_login_when_session_expires`
**Preconditions:** Expired or cleared session
**Steps:**
1. Navigate to `/balance-dashboard` without valid session
**Expected:** Redirected to login page; no account data briefly flashes before redirect

### TC-E2E-004
**Priority:** P0
**Type:** E2E
**AC covered:** AC-001-E1
**Title:** `should_render_empty_state_ui_indicator_when_customer_has_no_accounts`
**Preconditions:** CUST-T01 staging fixture (no active accounts)
**Steps:**
1. Login as CUST-T01
2. Navigate to `/balance-dashboard`
**Expected:** "No accounts to display" message visible; no error; no account rows; no spinner stuck

### TC-E2E-005
**Priority:** P1
**Type:** E2E
**AC covered:** AC-003-H1, AC-003-H4 (cache observable in browser)
**Title:** `should_return_response_with_x_cache_hit_header_after_cache_warm`
**Preconditions:** CUST-T04 (10 accounts); first request warms cache
**Steps:**
1. First request (cold cache — observe response time > 200ms expected)
2. Second request (warm cache)
3. Assert network timing and X-Cache header
**Expected:** `X-Cache: HIT` header present on second request; response time for second request < 500ms (manual observation in Playwright network log)

### TC-E2E-006
**Priority:** P0
**Type:** E2E (PDPA masking verification in DOM)
**AC covered:** AC-002-H3, BR-007
**Title:** `should_not_expose_full_account_number_in_dom_or_network_tab`
**Preconditions:** CUST-T03 (account number "1234567890")
**Steps:**
1. Navigate to dashboard
2. Search DOM for "1234567890"
3. Inspect network response JSON for "1234567890"
**Expected:** Full account number absent from DOM innerHTML and network response payload; only "******7890" visible

### TC-E2E-007
**Priority:** P1
**Type:** E2E
**AC covered:** AC-002-E1 (stale indicator in UI)
**Title:** `should_show_may_be_stale_indicator_when_balance_as_of_exceeds_threshold`
**Preconditions:** CUST-T09 (FIXED_DEPOSIT with balance_as_of = 24h ago) on staging
**Steps:**
1. Navigate to dashboard as CUST-T09
2. Assert "may be stale" indicator
**Expected:** Stale indicator visible on the FIXED_DEPOSIT account row; "Last updated" shows relative time from balance_as_of (e.g., "1 day ago"), not current time

### TC-E2E-008
**Priority:** P1
**Type:** E2E
**AC covered:** AC-003-E1 (Redis fail-open UX)
**Title:** `should_show_account_data_without_error_message_when_redis_is_down`
**Preconditions:** Redis unavailable on staging (simulated via feature flag or network policy)
**Steps:**
1. Navigate to dashboard when Redis is unavailable
**Expected:** HTTP 200 with account data (from AccountClient fallback); no Redis error message visible to user; no "503 Service Unavailable" page

---

## Performance Tests

### TC-PERF-001
**Priority:** P0
**Type:** Performance (k6)
**AC covered:** AC-003-H1, BR-017, NFR p95 warm < 500ms
**Title:** `warm_cache_p95_must_be_under_500ms_with_10_account_fixture`
**Fixture:** CUST-T04 (10 accounts); Redis pre-warmed; 30 VUs sustained 5 minutes; staging environment
**k6 thresholds:**
```js
thresholds: {
  http_req_duration: ['p(95)<500'],
  http_req_failed: ['rate<0.001'],
}
```
**Expected:** p95 < 500ms; error rate < 0.1%; cache hit ratio > 80% (observable in Grafana)

### TC-PERF-002
**Priority:** P0
**Type:** Performance (k6)
**AC covered:** AC-003-H2, BR-018, NFR p95 cold < 800ms
**Title:** `cold_cache_p95_must_be_under_800ms_with_10_account_fixture`
**Fixture:** CUST-T04; Redis flushed before each VU iteration (each iteration is a fresh cold cache); 30 VUs; 5 minutes; staging
**k6 thresholds:**
```js
thresholds: {
  http_req_duration: ['p(95)<800'],
  http_req_failed: ['rate<0.001'],
}
```
**Expected:** p95 < 800ms; AccountClient called exactly once per iteration (verified via OTel span count)

### TC-PERF-003
**Priority:** P0
**Type:** Performance (k6)
**AC covered:** AC-003-E2 (cache hit ratio > 70%)
**Title:** `cache_hit_ratio_must_exceed_70_percent_under_sustained_load`
**Fixture:** 50 VUs; 5 minutes warm-up + 5 minutes steady state; CUST-T04
**k6 custom metric:** track `X-Cache` header values
**Expected:** After warm-up period, cache hit ratio > 70% as measured by X-Cache header proportion; observable in Grafana `balance_dashboard_cache_hit_ratio`

### TC-PERF-004
**Priority:** P1
**Type:** Performance (k6)
**AC covered:** US-BC-003, BR-016 (single batched call)
**Title:** `account_client_must_be_called_exactly_once_per_cold_cache_request`
**Fixture:** CUST-T04; cold cache per iteration; OTel span export to Jaeger on staging
**Steps:**
1. Run k6 cold cache script
2. Query Jaeger for traces with service=balance-dashboard-service
3. Count spans to account-service per trace
**Expected:** Each trace shows exactly 1 span to account-service; no traces show N=10 spans (N round-trips)

### TC-PERF-005
**Priority:** P1
**Type:** Performance (k6)
**AC covered:** NFR concurrency — 50 peak concurrent users
**Title:** `dashboard_handles_50_concurrent_users_without_error_rate_exceeding_0_1_percent`
**Fixture:** 50 VUs; 5 minutes; mixed warm/cold cache (realistic); staging
**k6 thresholds:**
```js
thresholds: {
  http_req_duration: ['p(95)<800'],
  http_req_failed: ['rate<0.001'],
}
```
**Expected:** p95 < 800ms at 50 VUs; error rate < 0.1%; no Redis connection pool exhaustion

---

## A11y Tests

### TC-A11Y-001
**Priority:** P1
**Type:** A11y (automated — axe-core via Playwright)
**AC covered:** AC-005-H4, BR-023
**Title:** `lighthouse_mobile_accessibility_score_must_be_90_or_higher`
**Preconditions:** Staging URL with CUST-T03 data rendered
**Steps:**
1. Run Lighthouse via `lighthouse` npm package in Playwright against staging URL
2. Extract Accessibility category score
**Expected:** Accessibility score ≥ 90; evidence (JSON report) attached to QA report

### TC-A11Y-002
**Priority:** P1
**Type:** A11y (automated — axe-core)
**AC covered:** US-BC-005 general
**Title:** `axe_core_must_find_zero_violations_on_balance_dashboard_component`
**Preconditions:** `<balance-dashboard>` rendered with CUST-T03 data
**Steps:**
1. Run `@axe-core/playwright` against rendered page
2. Assert violations
**Expected:** Zero WCAG 2.1 AA violations reported by axe-core

### TC-A11Y-003
**Priority:** P1
**Type:** A11y (automated — Playwright viewport)
**AC covered:** AC-005-H1, BR-019
**Title:** `dashboard_must_render_without_horizontal_scroll_at_375px_viewport`
**Steps:**
1. Set Playwright viewport to `{ width: 375, height: 812 }`
2. Navigate to dashboard with 3 accounts
3. Assert `document.body.scrollWidth <= 375`
**Expected:** No horizontal overflow; all content readable at 375px

### TC-A11Y-004
**Priority:** P1
**Type:** A11y (automated — Playwright)
**AC covered:** AC-005-H3, BR-020
**Title:** `keyboard_tab_navigation_moves_focus_through_accounts_in_rank_order`
**Steps:**
1. Focus first element
2. Press Tab key 3 times (3 account rows)
3. Assert focused element sequence
**Expected:** Focus moves through Account 1 → 2 → 3 in rank order; each focused row has visible focus ring (contrast assertion via axe)

### TC-A11Y-005
**Priority:** P1
**Type:** A11y (manual — scripted)
**AC covered:** AC-005-H2, BR-021
**Title:** `voiceover_announces_rank_type_balance_and_last_updated_per_account_row`
**Manual test steps:**
1. Open dashboard on iOS device (or iOS Simulator) with VoiceOver enabled
2. Navigate to balance dashboard
3. Swipe through each account row
4. Record announcement text
**Expected:** Each row announced as: "Account [N] of [M], [account type label], [masked account number], [balance as spoken amount] baht, Last updated [relative time]"
**Notes:** Automated axe cannot fully verify VoiceOver announcement prosody — manual test required; document evidence in QA report

### TC-A11Y-006
**Priority:** P1
**Type:** A11y (automated)
**AC covered:** AC-005-E1, BR-019
**Title:** `layout_must_not_clip_or_truncate_text_at_200_percent_font_size`
**Steps:**
1. Set browser font size to 200% via Playwright `page.evaluate(() => document.documentElement.style.fontSize = '200%')`
2. Render dashboard with 3 accounts
3. Check for overflow: `getComputedStyle(element).overflow !== 'hidden'` and no clipped text
**Expected:** No text clipping; layout adapts with line-wrapping; no information lost
