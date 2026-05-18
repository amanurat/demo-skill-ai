# Banking-Specific Test Cases — Money Transfer Template

Reference loaded on demand by the `banking-test-automation` skill. Concrete test cases for a Money Transfer feature with the BA-story they cover, the test structure, and sample assertions. Use as a checklist when writing or reviewing tests for any financial flow — adapt names for your feature.

## Catalog (high-level)

| # | Test | Type | Why It Exists |
|---|---|---|---|
| 1 | `transferSucceeds_whenSufficientBalance` | Integration | Happy path |
| 2 | `transferFails_whenInsufficientBalance` | Integration | Validation |
| 3 | `transferIsIdempotent_whenSameKeyRetried` | Integration | Critical correctness |
| 4 | `transferIsRejected_whenDailyLimitExceeded` | Integration | Business rule |
| 5 | `sagaCompensates_whenLedgerCreditFails` | Integration | Distributed tx |
| 6 | `auditEvent_isEmittedForEveryAttempt` | Integration | Compliance |
| 7 | `concurrentTransfers_doNotDoubleDebit` | Integration | Concurrency |
| 8 | `transferCompletes_underP95Sla` | Performance | SLA |
| 9 | `frontendConfirmsBeforeSubmit` | E2E | UX safety |
| 10 | `frontendShowsCorrectErrorMessages` | E2E | UX |

Every BA acceptance criterion for "Money Transfer" must map to at least one entry above.

---

## 1. `transferSucceeds_whenSufficientBalance`

**BA story covered:** "As a customer, I can transfer money between my accounts when I have sufficient balance."

**Structure (Given / When / Then):**
- **Given** a source account with balance ≥ amount + fees
- **When** `POST /api/v1/transfers` is called with a valid request
- **Then** response is `201 Created`, source account is debited, destination is credited, `TransferCompleted` event is on Kafka, audit row is written

**Sample assertions:**
```java
assertThat(response.getStatusCode()).isEqualTo(CREATED);
assertThat(accountRepo.findById(SRC).balance()).isEqualByComparingTo("900.00");
assertThat(accountRepo.findById(DST).balance()).isEqualByComparingTo("1100.00");
await().atMost(2, SECONDS).untilAsserted(() ->
    assertThat(kafkaConsumer.poll("transfers.completed")).hasSize(1));
```

---

## 2. `transferFails_whenInsufficientBalance`

**BA story covered:** "If my balance is insufficient, the transfer must be rejected with a clear error."

**Structure:**
- **Given** source account balance < amount
- **When** transfer request is submitted
- **Then** response is `422 Unprocessable Entity` with Problem-Detail body `{"type": ".../insufficient-funds"}`; no debit, no event, audit row records the rejected attempt

**Sample assertions:**
```java
assertThat(response.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
assertThat(response.getBody()).contains("insufficient-funds");
assertThat(accountRepo.findById(SRC).balance()).isEqualByComparingTo("50.00"); // unchanged
assertThat(auditRepo.lastFor(SRC).status()).isEqualTo("REJECTED");
```

---

## 3. `transferIsIdempotent_whenSameKeyRetried`

**BA story covered:** "A retried request with the same Idempotency-Key must not double-debit."

**Structure:**
- **Given** a valid transfer with `Idempotency-Key: abc-123`
- **When** the same request is sent **twice**
- **Then** both responses are `201` with **identical body**; only **one** debit, one credit, one event

**Sample assertions:**
```java
var first  = transferApi.post(req, "abc-123");
var second = transferApi.post(req, "abc-123");
assertThat(second.getBody()).isEqualTo(first.getBody());
assertThat(ledgerRepo.entriesFor(transferId)).hasSize(2); // one debit + one credit, not four
```

---

## 4. `transferIsRejected_whenDailyLimitExceeded`

**BA story covered:** "Transfers exceeding the customer's daily limit must be rejected."

**Structure:**
- **Given** the customer has already transferred `dailyLimit - 100` today
- **When** they attempt a transfer of `200`
- **Then** response is `422` with `daily-limit-exceeded`; no debit

**Sample assertions:**
```java
seedTodayTransfers(CUSTOMER, money("9900.00")); // limit is 10000
var response = transferApi.post(transferOf("200.00"));
assertThat(response.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
assertThat(response.getBody()).contains("daily-limit-exceeded");
```

---

## 5. `sagaCompensates_whenLedgerCreditFails`

**BA story covered:** "If any step of the transfer fails, the system must not leave money in an inconsistent state."

**Structure:**
- **Given** ledger-service is configured to fail the credit step
- **When** transfer is submitted
- **Then** debit is **reversed** by the compensating action, final balance is unchanged, `TransferFailed` event emitted, audit shows `COMPENSATED`

**Sample assertions:**
```java
ledgerStub.failNext("CREDIT");
transferApi.post(req);
await().untilAsserted(() ->
    assertThat(accountRepo.findById(SRC).balance()).isEqualByComparingTo("1000.00")); // restored
assertThat(auditRepo.lastFor(transferId).status()).isEqualTo("COMPENSATED");
```

---

## 6. `auditEvent_isEmittedForEveryAttempt`

**BA story covered:** "Every transfer attempt (success, reject, compensate) must be auditable for 7 years."

**Structure:**
- **Given** a transfer in any terminal state (success / rejected / compensated)
- **When** the attempt finishes
- **Then** exactly one append-only audit row exists with: `transferId`, `actor`, `timestamp`, `status`, `requestHash`

**Sample assertions:**
```java
var audit = auditRepo.findByTransferId(transferId);
assertThat(audit).hasSize(1);
assertThat(audit.get(0).status()).isIn("COMPLETED", "REJECTED", "COMPENSATED");
assertThat(audit.get(0).immutable()).isTrue();
```

---

## 7. `concurrentTransfers_doNotDoubleDebit`

**BA story covered:** "Concurrent requests must never debit the same account twice for the same logical transfer."

**Structure:**
- **Given** two threads submit transfers from the same source account simultaneously, total > balance
- **When** both requests race
- **Then** exactly one succeeds, one is rejected with `optimistic-lock` or `insufficient-funds`; balance never goes negative

**Sample assertions:**
```java
var results = inParallel(2, () -> transferApi.post(transferOf("600.00"))); // balance = 1000
assertThat(results).filteredOn(r -> r.getStatusCode().is2xxSuccessful()).hasSize(1);
assertThat(accountRepo.findById(SRC).balance()).isGreaterThanOrEqualTo(ZERO);
```

---

## 8. `transferCompletes_underP95Sla`

**BA story covered:** "Transfers must complete within the documented SLA under normal load."

**Tool:** Gatling or k6.

**Structure:**
- **Given** 100 RPS sustained for 5 min against staging
- **Then** **p95 ≤ feature SLA**, **p99 ≤ 2× p95**, error rate **< 0.1%**

**Sample assertion (k6):**
```js
export const options = {
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed:   ['rate<0.001'],
  },
};
```

---

## 9. `frontendConfirmsBeforeSubmit`

**BA story covered:** "The UI must require explicit confirmation before submitting a transfer."

**Tool:** Cypress / Playwright.

**Structure:**
- **Given** the user fills the transfer form
- **When** they click "Transfer"
- **Then** a confirmation dialog appears; the API is **not** called until the user confirms

**Sample assertions:**
```js
cy.intercept('POST', '/api/v1/transfers').as('transfer');
cy.get('[data-test=submit]').click();
cy.get('[data-test=confirm-dialog]').should('be.visible');
cy.get('@transfer.all').should('have.length', 0); // not called yet
cy.get('[data-test=confirm-yes]').click();
cy.wait('@transfer').its('response.statusCode').should('eq', 201);
```

---

## 10. `frontendShowsCorrectErrorMessages`

**BA story covered:** "Users must see clear, accurate error messages for every failure mode."

**Structure:**
- For each error mode (insufficient-funds, daily-limit, server-error) → API returns Problem-Detail → UI renders the matching localized message
- No raw stack traces, no PII in the displayed message

**Sample assertions:**
```js
cy.intercept('POST', '/api/v1/transfers', { statusCode: 422, body: { type: '.../daily-limit-exceeded' } });
cy.submitTransfer();
cy.get('[data-test=error]').should('contain.text', 'daily limit');
cy.get('[data-test=error]').should('not.contain.text', 'Exception');
```

---

## How to Use This Catalog

- **Writing tests:** start by mapping each BA AC to one or more rows above; add tests for any AC not covered
- **Reviewing PRs:** if a financial feature ships without coverage for rows 3, 5, 6, 7 — **block the merge**
- **Adapting to other features:** keep the structure (Given/When/Then + sample assertions) and substitute the domain (e.g., `loanApplication`, `cardActivation`)
