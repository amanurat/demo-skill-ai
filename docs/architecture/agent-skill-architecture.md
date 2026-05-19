# Agent & Skill Architecture Diagrams

> เอกสารนี้อธิบายภาพรวมการทำงานของ Multi-Agent System ตั้งแต่การรับคำสั่งจากผู้ใช้ การประมวลผลของ Agent แต่ละตัว การเรียกใช้ Skill และการส่งผลลัพธ์กลับ

---

## Diagram 1 — System Overview (Big Picture)

แสดงองค์ประกอบหลักทั้งหมดของระบบและความสัมพันธ์ระหว่างกัน

```mermaid
graph TB
    subgraph USER["👤 User Layer"]
        U([User / Stakeholder])
    end

    subgraph HARNESS["🖥️ Claude Code Harness (VSCode Extension)"]
        direction TB
        CLAUDE_MD[CLAUDE.md\nProject Instructions]
        SKILL_REGISTRY[Skill Registry\n.claude/agents/*.md]
        MEMORY[Memory System\n.claude/memory/]
    end

    subgraph PLAYER["🎯 Player — Main Claude Session (Opus)"]
        direction TB
        INTENT[Intent Classification\nSDLC Phase Mapping]
        PLAN[Delegation Plan\nAgent Chain Design]
        VALIDATE[Artifact Validation\nHandoff Schema Check]
        GATE[Quality Gate Enforcement]
        RETRY[Retry / Feedback Loop\nmax 3 iterations]
        ESCALATE[Human Escalation\non retry exhaustion]
    end

    subgraph TOOLS["🛠️ Player Tools"]
        TASK_TOOL[Task Tool\nspawns subagents]
        SKILL_TOOL[Skill Tool\ninvokes patterns inline]
        FILE_TOOLS[Read / Write / Edit\nGlob / Grep / Bash]
    end

    subgraph AGENTS["🤖 Specialized Agents (Isolated Subprocesses)"]
        direction LR
        BA[banking-ba\nSonnet]
        SA[banking-solution-architect\nOpus]
        TL[banking-tech-lead\nOpus]
        FE[banking-frontend-dev\nSonnet]
        BE[banking-backend-dev\nSonnet]
        RV[banking-reviewer\nOpus]
        SEC[banking-security\nOpus]
        QA[banking-qa\nSonnet]
        DO[banking-devops\nSonnet]
    end

    subgraph SKILLS["📚 Skill Library"]
        SK1[openapi-flyway-standards]
        SK2[spring-boot-banking]
        SK3[angular-banking-ui]
        SK4[code-review-checklists]
        SK5[banking-security-patterns]
        SK6[banking-test-automation]
        SK7[banking-devops-platform]
        SK8[resilience4j-patterns]
        SK9[kafka-spring-patterns]
    end

    subgraph ARTIFACTS["📦 Handoff Artifacts"]
        ART[JSON Envelope\nartifact_id · from/to_agent\nphase · payload · metadata]
    end

    U -- "natural language\nrequirement" --> PLAYER
    CLAUDE_MD -- "loads into context" --> PLAYER
    SKILL_REGISTRY -- "agent definitions" --> TASK_TOOL
    MEMORY -- "recalled context" --> PLAYER

    PLAYER --> INTENT
    INTENT --> PLAN
    PLAN --> TASK_TOOL
    PLAN --> SKILL_TOOL

    TASK_TOOL -- "spawns with\nsubagent_type" --> AGENTS
    SKILL_TOOL -- "injects pattern\ninto context" --> PLAYER

    AGENTS -- "use domain skills" --> SKILLS
    AGENTS -- "produce" --> ARTIFACTS
    ARTIFACTS -- "returned to" --> VALIDATE

    VALIDATE --> GATE
    GATE -- "pass" --> PLAN
    GATE -- "fail" --> RETRY
    RETRY -- "iteration ≤ 3" --> TASK_TOOL
    RETRY -- "iteration > 3" --> ESCALATE
    ESCALATE -- "asks for\nguidance" --> U

    PLAYER -- "status summary\n+ artifacts" --> U
```

---

## Diagram 2 — Agent Registry & Metadata

แสดงข้อมูลของ Agent แต่ละตัว — Model, Tools, SDLC Phase, และ Skill ที่ใช้

```mermaid
graph LR
    subgraph DISCOVERY["🔍 Discovery Phase"]
        BA["banking-ba\n━━━━━━━━━━━━\nModel: Sonnet\nTools: Read·Write\n       Glob·Grep\n       WebSearch\n━━━━━━━━━━━━\nOutput: User Stories\n        AC · NFRs"]
    end

    subgraph PLANNING["📐 Planning Phase"]
        SA["banking-solution-architect\n━━━━━━━━━━━━\nModel: Opus\nTools: Read·Write\n       Glob·Grep\n       WebSearch\n━━━━━━━━━━━━\nOutput: Service Map\n        Events · ADRs"]
    end

    subgraph DESIGN["🏗️ Design Phase"]
        TL["banking-tech-lead\n━━━━━━━━━━━━\nModel: Opus\nTools: Read·Write\n       Glob·Grep\n       WebSearch\n━━━━━━━━━━━━\nOutput: OpenAPI Spec\n        DB Schema · ADRs\nSkill: openapi-flyway-standards"]
    end

    subgraph DEVELOPMENT["💻 Development Phase"]
        FE["banking-frontend-dev\n━━━━━━━━━━━━\nModel: Sonnet\nTools: Read·Write·Edit\n       Glob·Grep·Bash\n━━━━━━━━━━━━\nOutput: Angular Components\n        Routes · State\nSkill: angular-banking-ui"]
        BE["banking-backend-dev\n━━━━━━━━━━━━\nModel: Sonnet\nTools: Read·Write·Edit\n       Glob·Grep·Bash\n━━━━━━━━━━━━\nOutput: Spring Boot Service\n        Tests · OpenAPI sync\nSkills: spring-boot-banking\n        resilience4j-patterns\n        kafka-spring-patterns"]
    end

    subgraph REVIEW["🔎 Review Phase"]
        RV["banking-reviewer\n━━━━━━━━━━━━\nModel: Opus\nTools: Read·Glob\n       Grep·Bash\n━━━━━━━━━━━━\nOutput: verdict\n        blocker/major comments\nSkill: code-review-checklists"]
    end

    subgraph SECURITY["🔒 Security Phase"]
        SEC["banking-security\n━━━━━━━━━━━━\nModel: Opus\nTools: Read·Glob\n       Grep·Bash\n       WebSearch\n━━━━━━━━━━━━\nOutput: SAST · OWASP findings\n        PCI-DSS · GDPR check\nSkills: banking-security-patterns\n        dependency-auditor"]
    end

    subgraph TESTING["🧪 Testing Phase"]
        QA["banking-qa\n━━━━━━━━━━━━\nModel: Sonnet\nTools: Read·Write·Edit\n       Glob·Grep·Bash\n━━━━━━━━━━━━\nOutput: Test Plan\n        Unit·Integration·E2E\n        Perf SLA check\nSkill: banking-test-automation"]
    end

    subgraph DEPLOYMENT["🚀 Deployment Phase"]
        DO["banking-devops\n━━━━━━━━━━━━\nModel: Sonnet\nTools: Read·Write·Edit\n       Glob·Grep·Bash\n━━━━━━━━━━━━\nOutput: Dockerfile · Helm\n        K8s · CI/CD · Grafana\nSkills: banking-devops-platform\n        spring-startup-optimizer"]
    end

    BA --> SA --> TL --> FE & BE --> RV --> SEC --> QA --> DO
```

---

## Diagram 3 — SDLC Forward Flow + Quality Gates

แสดง Workflow หลักพร้อม Quality Gate ที่ Player ตรวจสอบก่อนส่งต่อ Agent ถัดไป

```mermaid
flowchart TD
    START([👤 User Requirement]) --> BA

    BA[banking-ba\nUser Stories · AC · NFRs]
    G1{{"🚦 Gate: Requirement\nCompleteness ≥ 90%\nNo ambiguous terms"}}
    SA[banking-solution-architect\nService Map · Events · ADRs]
    G2{{"🚦 Gate: Architecture\nSoundness\nAll NFRs traced · ≥1 ADR\nper major decision"}}
    TL[banking-tech-lead\nOpenAPI Spec · DB Schema\nFlyway Migrations · ADRs]
    G3{{"🚦 Gate: Contract\nValidated\nOpenAPI lints clean\nThreat model done"}}

    subgraph DEV["Parallel Development"]
        FE[banking-frontend-dev\nAngular UI]
        BE[banking-backend-dev\nSpring Boot Service]
    end

    G4{{"🚦 Gate: Code Health\nCoverage ≥ 80%\nLint passes · Build green"}}
    RV[banking-reviewer\nPrincipal Engineer Review]
    G5{{"🚦 Gate: Best-Practice\nCompliance\nNo blocker/major unresolved"}}
    SEC[banking-security\nOWASP · SAST · PCI-DSS]
    G6{{"🚦 Gate: Vulnerability\nFloor\nNo critical/high CVEs\nNo secrets in code"}}
    QA[banking-qa\nTest Plan · Unit · Integration\nE2E · Performance]
    G7{{"🚦 Gate: Test Coverage\n+ SLA\nAll suites green\nPerf SLA met"}}
    DO[banking-devops\nCI/CD · Docker · Helm\nK8s · Grafana]
    G8{{"🚦 Gate: Deployable\n+ Observable\nSmoke tests pass\nDashboards live"}}
    DONE([✅ DoD Met\nDelivered to User])

    START --> BA --> G1
    G1 -- "pass" --> SA
    G1 -- "fail" --> BA

    SA --> G2
    G2 -- "pass" --> TL
    G2 -- "fail" --> SA

    TL --> G3
    G3 -- "pass" --> DEV
    G3 -- "fail" --> TL

    DEV --> G4
    G4 -- "pass" --> RV
    G4 -- "fail" --> DEV

    RV --> G5
    G5 -- "approved" --> SEC
    G5 -- "changes_requested" --> DEV

    SEC --> G6
    G6 -- "approved" --> QA
    G6 -- "changes_requested" --> DEV

    QA --> G7
    G7 -- "pass" --> DO
    G7 -- "fail" --> DEV

    DO --> G8
    G8 -- "pass" --> DONE
    G8 -- "fail" --> DO

    style DONE fill:#22c55e,color:#fff
    style START fill:#3b82f6,color:#fff
```

---

## Diagram 4 — Feedback Loop & Retry Mechanics

แสดงกลไก Retry ที่ Player จัดการ พร้อม Escalation เมื่อครบ 3 รอบ

```mermaid
stateDiagram-v2
    [*] --> AgentInvoked : Task tool call\n(iteration=1)

    AgentInvoked --> ArtifactEmitted : Agent completes work

    ArtifactEmitted --> PlayerValidates : Returns JSON artifact

    PlayerValidates --> QualityGate : Envelope valid ✓

    QualityGate --> ForwardNext : gate PASS\nquality_gate_passed=true
    QualityGate --> FeedbackLoop : gate FAIL\nquality_gate_passed=false

    ForwardNext --> [*] : Artifact forwarded\nto next agent

    FeedbackLoop --> CheckIteration : Compose feedback\ncomments

    CheckIteration --> AgentInvoked : iteration < 3\nincrement iteration\nre-invoke same agent\nwith prior artifact + comments
    CheckIteration --> HumanEscalation : iteration = 3\n(max retries exhausted)

    HumanEscalation --> UserDecision : Player surfaces:\n• top blockers\n• 3 attempts summary\n• options to proceed

    UserDecision --> AgentInvoked : User provides\nguidance → reset iteration
    UserDecision --> [*] : User chooses\nto descope / abort

    note right of FeedbackLoop
        Banking Feedback Triggers:
        • Reviewer → changes_requested
        • Security → critical/high finding
        • QA → test failures / SLA miss
        • DevOps → deploy failure
        • TL → ambiguous requirement
    end note

    note right of HumanEscalation
        Hard-Stop Triggers (no retry):
        • PII/card data in logs
        • Hardcoded secrets
        • PCI scope expansion
        • Compliance hard-fail
    end note
```

---

## Diagram 5 — Handoff Artifact Lifecycle

แสดงวิธีที่ JSON Artifact ถูกสร้าง ตรวจสอบ และส่งต่อระหว่าง Agent

```mermaid
sequenceDiagram
    actor User
    participant Player as 🎯 Player<br/>(Main Session)
    participant AgentA as 🤖 Agent A<br/>(e.g. banking-ba)
    participant AgentB as 🤖 Agent B<br/>(e.g. banking-sa)

    User->>Player: "วิเคราะห์ Money Transfer feature"
    Player->>Player: Classify intent → DISCOVERY phase
    Player->>AgentA: Task tool invocation<br/>subagent_type: banking-ba<br/>prompt: [requirement + context]

    activate AgentA
    Note over AgentA: Reads CLAUDE.md, overview.md<br/>Invokes WebSearch if needed<br/>Applies skill patterns
    AgentA->>AgentA: Produces user stories + AC
    AgentA-->>Player: Handoff Artifact (JSON)
    deactivate AgentA

    Note over Player: Validates envelope schema<br/>Checks: artifact_id · from_agent<br/>to_agent · phase · payload · metadata

    Player->>Player: Apply Quality Gate<br/>Requirement Completeness ≥ 90%

    alt Gate PASS — quality_gate_passed: true
        Player->>AgentB: Task tool invocation<br/>subagent_type: banking-solution-architect<br/>prompt: [BA artifact as input]
        activate AgentB
        AgentB-->>Player: Next Handoff Artifact
        deactivate AgentB
    else Gate FAIL — quality_gate_passed: false
        Player->>AgentA: Re-invoke with feedback comments<br/>metadata.iteration += 1<br/>metadata.previous_artifact_id set
        activate AgentA
        AgentA-->>Player: Revised Artifact (iteration 2)
        deactivate AgentA
    end

    Player->>User: Status summary:\n"BA complete — 5 stories, 23 AC.\nForwarding to Architect..."
```

---

## Diagram 6 — Skill Integration Map

แสดงว่า Skill แต่ละตัวถูกใช้งานโดย Agent ใดบ้าง และครอบคลุมความสามารถด้านไหน

```mermaid
graph LR
    subgraph SKILLS["📚 Skill Library"]
        direction TB
        SK_OPENAPI[openapi-flyway-standards\nOpenAPI 3 · DB Schema · Flyway]
        SK_SPRING[spring-boot-banking\nHexagonal · JPA · Saga · Outbox]
        SK_ANGULAR[angular-banking-ui\nComponents · Routing · A11y]
        SK_REVIEW[code-review-checklists\nAnti-patterns · Severity Ratings]
        SK_SEC[banking-security-patterns\nSTRIDE · OWASP · PCI-DSS]
        SK_DEP[dependency-auditor\nCVE · SBOM · License]
        SK_TEST[banking-test-automation\nTestcontainers · Gatling · k6]
        SK_DEVOPS[banking-devops-platform\nCI/CD · Helm · Prometheus]
        SK_R4J[resilience4j-patterns\nCircuit Breaker · Retry · Bulkhead]
        SK_KAFKA[kafka-spring-patterns\nOutbox · DLQ · Avro]
        SK_PERF[spring-performance-tuning\nHikariCP · JVM · GC]
        SK_VT[concurrency-virtual-threads\nProject Loom · Structured Concurrency]
        SK_STARTUP[spring-startup-optimizer\nAOT · CDS · GraalVM]
    end

    subgraph AGENTS["🤖 Agents"]
        TL[banking-tech-lead]
        BE[banking-backend-dev]
        FE[banking-frontend-dev]
        RV[banking-reviewer]
        SEC[banking-security]
        QA[banking-qa]
        DO[banking-devops]
    end

    TL --> SK_OPENAPI

    BE --> SK_SPRING
    BE --> SK_R4J
    BE --> SK_KAFKA
    BE --> SK_PERF
    BE --> SK_VT

    FE --> SK_ANGULAR

    RV --> SK_REVIEW

    SEC --> SK_SEC
    SEC --> SK_DEP

    QA --> SK_TEST

    DO --> SK_DEVOPS
    DO --> SK_STARTUP

    style SK_OPENAPI fill:#fef3c7
    style SK_SPRING fill:#dbeafe
    style SK_ANGULAR fill:#fce7f3
    style SK_REVIEW fill:#f3f4f6
    style SK_SEC fill:#fee2e2
    style SK_DEP fill:#fee2e2
    style SK_TEST fill:#d1fae5
    style SK_DEVOPS fill:#ede9fe
    style SK_R4J fill:#dbeafe
    style SK_KAFKA fill:#dbeafe
    style SK_PERF fill:#dbeafe
    style SK_VT fill:#dbeafe
    style SK_STARTUP fill:#ede9fe
```

---

## Diagram 7 — Agent File Anatomy (agent.md Structure)

แสดงโครงสร้างภายในของไฟล์ agent.md และหน้าที่ของแต่ละส่วน

```mermaid
graph TD
    subgraph FILE["📄 .claude/agents/banking-xxx.md"]
        direction TB

        subgraph FRONT["--- Frontmatter (YAML) ---"]
            F1[name: banking-xxx\nUnique identifier used by Task tool]
            F2[description: ...\nUsed by Player to decide when to invoke]
            F3[tools: Read, Write, Edit, ...\nAllowed tool set — enforced by harness]
            F4[model: sonnet | opus\nSonnet = speed, Opus = reasoning depth]
        end

        subgraph BODY["# Markdown Body"]
            B1[## Persona\nRole, experience, mindset,\nbehavioral constraints]
            B2[## Inputs\nWhat artifact / context\nthis agent expects]
            B3[## Outputs\nHandoff artifact schema\n+ examples]
            B4[## Responsibilities\nDetailed task list]
            B5[## Skills Used\nWhich skill patterns to invoke]
            B6[## Anti-Patterns\nWhat NOT to do]
            B7[## Acceptance Criteria\nSelf-check checklist before emitting]
            B8[## Gotchas\nBanking-specific edge cases]
        end
    end

    subgraph RUNTIME["⚡ At Runtime"]
        R1[Player reads description\nto choose agent]
        R2[Task tool injects frontmatter\nas system config]
        R3[Agent reads body\nas behavioral instructions]
        R4[Harness enforces\ntool allowlist]
        R5[Agent invokes Skill tool\nfor domain patterns]
    end

    FRONT --> RUNTIME
    BODY --> RUNTIME
    F2 --> R1
    F3 --> R4
    F4 --> R2
    B1 --> R3
    B5 --> R5
```

---

## Diagram 8 — Skill Invocation Flow

แสดงวิธีที่ Skill ถูกเรียกใช้และเพิ่ม context ให้กับ Agent

```mermaid
sequenceDiagram
    participant Player as 🎯 Player
    participant TaskTool as Task Tool
    participant Agent as 🤖 banking-backend-dev
    participant SkillTool as Skill Tool
    participant SkillLib as 📚 spring-boot-banking

    Player->>TaskTool: invoke banking-backend-dev\nwith Tech Lead artifact

    activate Agent
    Note over Agent: Reads OpenAPI spec path\nReads DB schema

    Agent->>SkillTool: /spring-boot-banking
    SkillTool->>SkillLib: Load skill content
    SkillLib-->>Agent: Injects pattern context:\n• Hexagonal architecture layout\n• JPA entity design rules\n• Idempotency implementation\n• Outbox pattern template\n• Anti-patterns to avoid

    Agent->>SkillTool: /resilience4j-patterns
    SkillTool-->>Agent: Injects:\n• Circuit Breaker config\n• Retry with backoff/jitter\n• Banking-tuned defaults

    Agent->>Agent: Implements TransferService.java\nwith all patterns applied

    Agent-->>Player: Handoff Artifact\n{ service, files_changed,\n  tests, build_status }
    deactivate Agent

    Note over Player: Validates artifact envelope\nChecks quality gate
```

---

## Diagram 9 — Complete End-to-End: Money Transfer Feature

แสดง Timeline การทำงานทั้งหมดสำหรับ Money Transfer feature ตั้งแต่ต้นจนจบ

```mermaid
timeline
    title Money Transfer — SDLC Agent Chain

    section Discovery
        banking-ba : User tells Player "โอนเงินระหว่างบัญชี"
                   : BA produces 5 user stories + 23 AC + NFRs
                   : Quality Gate ✅ Completeness ≥ 90%

    section Planning
        banking-solution-architect : Receives BA artifact
                                   : Designs 7 services + 3 Kafka events
                                   : ADR-001 Saga · ADR-002 Outbox · ADR-003 Idempotency
                                   : Quality Gate ✅ All NFRs traced

    section Design
        banking-tech-lead : OpenAPI 3 spec (3 endpoints)
                          : Flyway V001__transfers.sql
                          : Quality Gate ✅ OpenAPI lints clean

    section Development
        banking-frontend-dev : Angular TransferComponent
        banking-backend-dev : TransferService + Saga + Repository
                            : 87% unit test coverage

    section Review
        banking-reviewer : Finds anemic domain (major)
                        : → Loop back iteration 2
                        : Backend refactors → Approved ✅

    section Security
        banking-security : Finds HS256 JWT (high)
                        : → Loop back iteration 3
                        : Backend switches to RS256 → Approved ✅

    section Testing
        banking-qa : Testcontainers integration tests
                   : Gatling perf test p95=420ms ✅ SLA met
                   : All 153 tests green

    section Deployment
        banking-devops : Docker image built
                      : Helm deploy to staging
                      : Smoke tests pass · Grafana live
                      : DoD Checklist ✅ Complete
```

---

## Summary — Agent × Tool × Skill Matrix

| Agent | Model | Tools | Skills | Emits To |
|---|---|---|---|---|
| `banking-ba` | Sonnet | Read, Write, Glob, Grep, WebSearch | — | `banking-solution-architect` |
| `banking-solution-architect` | Opus | Read, Write, Glob, Grep, WebSearch | — | `banking-tech-lead` |
| `banking-tech-lead` | Opus | Read, Write, Glob, Grep, WebSearch | `openapi-flyway-standards` | `banking-backend-dev` + `banking-frontend-dev` |
| `banking-backend-dev` | Sonnet | Read, Write, Edit, Glob, Grep, Bash | `spring-boot-banking` · `resilience4j-patterns` · `kafka-spring-patterns` · `spring-performance-tuning` · `concurrency-virtual-threads` | `banking-reviewer` |
| `banking-frontend-dev` | Sonnet | Read, Write, Edit, Glob, Grep, Bash | `angular-banking-ui` | `banking-reviewer` |
| `banking-reviewer` | Opus | Read, Glob, Grep, Bash | `code-review-checklists` | `banking-security` (approved) / Dev (changes) |
| `banking-security` | Opus | Read, Glob, Grep, Bash, WebSearch | `banking-security-patterns` · `dependency-auditor` | `banking-qa` (approved) / Dev (findings) |
| `banking-qa` | Sonnet | Read, Write, Edit, Glob, Grep, Bash | `banking-test-automation` | `banking-devops` |
| `banking-devops` | Sonnet | Read, Write, Edit, Glob, Grep, Bash | `banking-devops-platform` · `spring-startup-optimizer` | `banking-player` (done) |

---

## Key Design Principles

### 1. Isolation per Agent
แต่ละ Agent ทำงานใน **isolated subprocess** — ไม่แชร์ context กับ Agent อื่น การสื่อสารเกิดขึ้นผ่าน **Handoff Artifact (JSON)** เท่านั้น

### 2. Player as Single Source of Truth
Player (Main Claude Session) เป็นผู้ **validate ทุก artifact**, **enforce quality gates**, และ **manage retry state** — Agent ไม่รู้ iteration count ของตัวเอง

### 3. Skill = Contextual Pattern Injection
Skill ไม่ใช่ Agent — เป็น **knowledge pattern** ที่ถูก inject เข้าไปใน Agent context ณ runtime ทำให้ Agent ที่ต่างกันสามารถใช้ pattern เดียวกันได้โดยไม่ต้อง duplicate

### 4. Model Selection by Task Type
- **Opus** → agents ที่ต้องการ deep reasoning (Architect, Tech Lead, Reviewer, Security)
- **Sonnet** → agents ที่ต้องการ throughput และ execution (Dev, QA, DevOps, BA)

### 5. Hard Gates = Non-Negotiable
5 Banking Hard Gates ไม่มีทาง bypass แม้จะครบ 3 retry iterations แล้ว — Player ต้อง escalate ไปยัง human ทันที
