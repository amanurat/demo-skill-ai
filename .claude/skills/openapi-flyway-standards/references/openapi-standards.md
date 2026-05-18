# OpenAPI 3 Standards — Banking Tech Lead Authoring Guide

Reference loaded on demand by `openapi-flyway-standards` skill. Every banking endpoint spec must follow these rules; deviations need an ADR.

## 1. Operation Metadata — `summary`, `description`, `operationId`

Every operation declares all three. `operationId` is **camelCase** and globally unique across the spec (drives generated client method names).

```yaml
paths:
  /api/v1/transfers:
    post:
      summary: Create a transfer
      description: |
        Initiates a money transfer between two accounts. Requires an
        Idempotency-Key header. Returns 201 on accept; transfer settles
        asynchronously via Saga.
      operationId: createTransfer
```

## 2. Request / Response Schemas — `$ref` + Examples

Bodies and responses use `$ref` to named schemas. Always include at least one `example`.

```yaml
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/TransferRequest'
            example:
              fromAccountId: "11111111-1111-1111-1111-111111111111"
              toAccountId:   "22222222-2222-2222-2222-222222222222"
              amount:        "150.0000"
              currency:      "THB"
      responses:
        '201':
          description: Transfer accepted
          content:
            application/json:
              schema: { $ref: '#/components/schemas/TransferAccepted' }
```

## 3. Error Model — Problem-Detail (RFC 7807)

All 4xx / 5xx responses use the Problem-Detail shape. Define once, reuse everywhere.

```yaml
components:
  schemas:
    ProblemDetail:
      type: object
      required: [type, title, status]
      properties:
        type:     { type: string, format: uri, example: "https://errors.bank.local/insufficient-funds" }
        title:    { type: string, example: "Insufficient funds" }
        status:   { type: integer, example: 422 }
        detail:   { type: string, example: "Account balance 50.00 is below requested amount 150.00" }
        instance: { type: string, format: uri, example: "/api/v1/transfers/req-abc-123" }
        code:     { type: string, example: "INSUFFICIENT_FUNDS" }
        traceId:  { type: string, example: "00-7e3f...-01" }
```

Each documented response references it:

```yaml
        '422':
          description: Business rule violation
          content:
            application/problem+json:
              schema: { $ref: '#/components/schemas/ProblemDetail' }
```

## 4. Security — `bearerAuth` (JWT)

Declare once in `components.securitySchemes`; apply per-operation (or globally).

```yaml
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
security:
  - bearerAuth: []
```

Public endpoints (e.g., health) override with `security: []`.

## 5. Pagination — `page`, `size`, `sort` + `X-Total-Count`

Standard query params; total count in response header.

```yaml
  /api/v1/transfers:
    get:
      operationId: listTransfers
      parameters:
        - in: query
          name: page
          schema: { type: integer, minimum: 0, default: 0 }
        - in: query
          name: size
          schema: { type: integer, minimum: 1, maximum: 100, default: 20 }
        - in: query
          name: sort
          schema: { type: string, example: "createdAt,desc" }
      responses:
        '200':
          description: OK
          headers:
            X-Total-Count:
              schema: { type: integer }
              description: Total number of records matching the query
```

For very high-volume endpoints, prefer **cursor pagination** (`?cursor=...`) — document in an ADR.

## 6. Required Headers

| Header | Where | Purpose |
|---|---|---|
| `Idempotency-Key` | Request — all POST/PUT/PATCH on financial endpoints | De-duplicate retries |
| `X-Request-Id` | Echoed in every response | Trace correlation |
| `Deprecation` + `Sunset` | Response on deprecated endpoints | Lifecycle signaling |

```yaml
      parameters:
        - in: header
          name: Idempotency-Key
          required: true
          schema: { type: string, format: uuid }
          description: Client-generated UUID v4. TTL 24h. See ADR-003.
      responses:
        '201':
          headers:
            X-Request-Id:
              schema: { type: string }
              description: Echoed correlation id (also present in logs / traces).
```

## 7. URI Versioning

Versions live in the URI: `/api/v1/...`, `/api/v2/...`. Never break v1 silently. When introducing v2:

- Keep v1 operational until sunset.
- Mark v1 operations `deprecated: true`.
- Add `Deprecation` header + `Sunset` date in v1 responses.

```yaml
  /api/v1/transfers/{transferId}:
    get:
      deprecated: true
      operationId: getTransferV1
      description: |
        Deprecated 2026-04-01. Use `/api/v2/transfers/{transferId}`.
        Sunset: 2026-10-01.
```

## 8. Linting Gate

Spec must lint clean (Spectral or equivalent) before handoff. Custom rules to enforce above conventions live in `infra/spectral/.spectral.yaml` (DevOps owns the ruleset).
