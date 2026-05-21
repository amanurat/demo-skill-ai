/**
 * balance-dashboard.model.ts
 * TypeScript interface contracts for Balance Comparison Dashboard.
 * These shapes are LOCKED — see task-plan.md §Interface Contracts.
 * balance field MUST remain string (BigDecimal-as-string to prevent JS float precision loss).
 */

// ---- Enums / Union Types ----

export type AccountType = 'SAVINGS' | 'CURRENT' | 'FIXED_DEPOSIT';

export type Freshness = 'live' | 'snapshot' | 'stale';

export type DisplayLabel =
  | 'account.type.savings'
  | 'account.type.current'
  | 'account.type.fixedDeposit';

export type ErrorCode =
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'RATE_LIMIT_EXCEEDED'
  | 'SERVICE_UNAVAILABLE';

// ---- Core Data Shapes ----

/**
 * Per-account row.
 * CRITICAL: balance is string — NOT number. Format: ^-?\d+\.\d{2}$
 * accountId is used ONLY as @for track key — must NOT be displayed in UI (BA NFR §8).
 * rank is 1-based server-authoritative — FE must NOT re-sort.
 */
export interface AccountViewDto {
  /** 1-based server-authoritative rank. FE renders in received order. */
  readonly rank: number;
  /** UUID. Used as @for track key only — never displayed. */
  readonly accountId: string;
  /** Pattern: ^\*+\d{4}$ — already masked by account-service. */
  readonly accountNumberMasked: string;
  readonly accountType: AccountType;
  /**
   * BigDecimal serialized as string to prevent IEEE-754 precision loss.
   * Format: ^-?\d+\.\d{2}$
   * NEVER parse to number for math operations.
   */
  readonly balance: string;
  /** ISO 4217. v1: always "THB". */
  readonly currency: string;
  /** ISO-8601 UTC timestamp of ledger event. May be null per AC-002-E2. */
  readonly balanceAsOf: string | null;
  /** Server-computed: now() - balanceAsOf > 60s. Drives per-row stale badge. */
  readonly isStale: boolean;
  /** i18n key resolved by FE. */
  readonly displayLabel: DisplayLabel;
}

export interface DashboardMeta {
  readonly accountCount: number;
  /** Drives global staleness banner. 'live' = no banner. */
  readonly freshness: Freshness;
  /** Observability only — NEVER display to customer. */
  readonly cacheHit: boolean;
  /** OTel trace ID. Shown only in error UI for support contact. */
  readonly correlationId: string;
}

export interface BalanceDashboardResponse {
  readonly accounts: ReadonlyArray<AccountViewDto>;
  readonly meta: DashboardMeta;
}

// ---- Error Shape (RFC 7807 Problem Detail) ----

export interface ProblemDetail {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly correlationId?: string;
  /** FE branches behavior on this field (not status alone). */
  readonly code?: ErrorCode;
}

// ---- Loading State Machine ----

export type DashboardState =
  | { kind: 'loading' }
  | { kind: 'loaded'; data: BalanceDashboardResponse }
  | { kind: 'empty'; meta: DashboardMeta }
  | { kind: 'error'; code: ErrorCode; correlationId: string; retryable: boolean }
  | { kind: 'unauthorized' }
  | { kind: 'forbidden'; correlationId: string };
