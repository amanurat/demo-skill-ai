# Project Structure (Planned)

> **Note:** Code does not exist yet. This document is the **target layout** that agents will scaffold in future sessions.

## Top-Level Layout

```
demo-skill-ai/
├── CLAUDE.md
├── .claude/
│   └── agents/                       # Subagent definitions (existing)
├── docs/
│   ├── architecture/                 # System docs (existing)
│   ├── adr/                          # Architecture Decision Records
│   ├── api/                          # Generated OpenAPI docs
│   ├── runbooks/                     # On-call runbooks
│   └── test-plans/                   # QA test plans
├── frontend/                         # Angular workspace (future)
│   ├── apps/
│   │   └── banking-web/              # Main Angular app
│   ├── libs/
│   │   ├── ui/                       # Shared components
│   │   ├── data-access/              # API clients (generated from OpenAPI)
│   │   └── feature-transfer/        # Money Transfer feature module
│   ├── angular.json
│   ├── package.json
│   └── tsconfig.json
├── backend/                          # Maven monorepo (future)
│   ├── pom.xml                       # Parent POM
│   ├── parent-bom/                   # Shared dependency BOM
│   ├── common-libs/
│   │   ├── audit-lib/                # Reusable audit publisher
│   │   ├── idempotency-lib/          # Idempotency-Key handling
│   │   └── observability-lib/        # OTel + metrics setup
│   ├── api-gateway/
│   ├── identity-service/
│   ├── account-service/
│   ├── transfer-service/
│   ├── ledger-service/
│   ├── notification-service/
│   └── audit-service/
├── infra/                            # Infrastructure as Code (future)
│   ├── docker-compose.yml            # Local dev
│   ├── helm/                         # Helm charts per service
│   ├── k8s/                          # Raw K8s manifests
│   └── terraform/                    # Cloud provisioning
├── .github/
│   └── workflows/                    # CI/CD pipelines
└── (prompt journey files)
```

## Backend Monorepo — Module Conventions

Each service follows the same internal layout (hexagonal architecture):

```
backend/transfer-service/
├── pom.xml
├── api/
│   └── openapi.yaml                  # Source of truth for HTTP contract
├── src/
│   ├── main/
│   │   ├── java/com/bank/transfer/
│   │   │   ├── TransferServiceApplication.java
│   │   │   ├── domain/               # Pure domain (entities, value objects, domain services)
│   │   │   ├── application/          # Use cases, sagas
│   │   │   ├── infrastructure/       # Adapters: JPA, Kafka, REST clients
│   │   │   └── interfaces/
│   │   │       ├── rest/             # Controllers
│   │   │       └── events/           # Kafka listeners
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/         # Flyway scripts
│   └── test/
│       └── java/                     # Unit + integration tests
└── README.md
```

## Naming Conventions

| Type | Convention | Example |
|---|---|---|
| Java package | `com.bank.<service>.<layer>` | `com.bank.transfer.domain` |
| REST endpoint | `/api/v1/<resource>` | `/api/v1/transfers` |
| Event topic | `<service>.<entity>.<event>.v<n>` | `transfer.transfer.requested.v1` |
| DB table | `snake_case` | `transfer_idempotency` |
| Flyway script | `V<seq>__<desc>.sql` | `V001__create_transfers.sql` |
| Docker image | `bank/<service>:<semver>` | `bank/transfer-service:1.0.0` |
| Helm release | `<service>-<env>` | `transfer-service-staging` |

## Branching Strategy

- **Trunk-based** with short-lived feature branches (≤ 2 days)
- Branch name: `feat/<ticket>-short-desc`, `fix/...`, `chore/...`
- Pull requests required; CI must pass; reviewer + security approve

## Commit Convention

Conventional Commits:
```
<type>(<scope>): <subject>

<body>

<footer>
```

Types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `perf`, `build`, `ci`

## Frontend Workspace (Nx-style monorepo)

```
frontend/
├── apps/
│   └── banking-web/                  # The deployable Angular app
├── libs/
│   ├── ui/                           # Presentational components
│   ├── data-access/                  # HttpClient services, NgRx slices
│   ├── feature-auth/                 # Auth feature module
│   └── feature-transfer/             # Money Transfer pages
└── tools/
    └── generators/                   # Custom Nx generators (optional)
```

## CI/CD Pipeline (Planned)

1. **Lint** (frontend + backend)
2. **Unit tests** (parallel per module)
3. **SAST + SCA** scans
4. **Build images** (Buildx, multi-arch)
5. **Integration tests** (Testcontainers)
6. **Container scan** (Trivy)
7. **Push to registry**
8. **Deploy to staging** (Helm)
9. **Smoke tests + DAST**
10. **Manual gate** → Deploy to prod
