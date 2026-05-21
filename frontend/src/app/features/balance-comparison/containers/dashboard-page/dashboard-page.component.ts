/**
 * dashboard-page.component.ts — DashboardPageComponent
 * Smart (container) component. Wires service → state → dumb components.
 * Source: task-plan §Step 4, component-specs §1.
 *
 * State machine: loading | loaded | empty | error | unauthorized | forbidden
 * - 401 → router.navigate(['/login'])
 * - 403 → show "Access denied" toast (no auto-retry) — via simple flag
 * - 503 → render ErrorRetryBannerComponent with retry
 * - empty → render EmptyStateComponent
 * - loaded → render account list
 * - meta.freshness=snapshot/stale → render StaleBannerComponent
 *
 * No re-sort: accounts rendered in received array order (task-plan constraint).
 * Double-submit protection on retry button.
 *
 * OTel tracing: X-Correlation-Id from response echoed to span (implementation-notes §8).
 */

import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
  computed,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import { DashboardService } from '../../services/dashboard.service';
import { BalanceStalenessService } from '../../services/balance-staleness.service';
import {
  DashboardState,
  AccountViewDto,
  DashboardMeta,
} from '../../models/balance-dashboard.model';
import { AccountRowComponent } from '../../components/account-row/account-row.component';
import { StaleBannerComponent } from '../../components/stale-banner/stale-banner.component';
import { EmptyStateComponent } from '../../components/empty-state/empty-state.component';
import { ErrorRetryBannerComponent } from '../../components/error-retry-banner/error-retry-banner.component';
import { LoadingSkeletonComponent } from '../../components/loading-skeleton/loading-skeleton.component';

@Component({
  selector: 'balance-dashboard-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    AccountRowComponent,
    StaleBannerComponent,
    EmptyStateComponent,
    ErrorRetryBannerComponent,
    LoadingSkeletonComponent,
  ],
  templateUrl: './dashboard-page.component.html',
  styleUrls: ['./dashboard-page.component.scss'],
})
export class DashboardPageComponent implements OnInit, OnDestroy {
  private readonly dashboardService = inject(DashboardService);
  private readonly stalenessService = inject(BalanceStalenessService);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  // ---- State signals ----
  readonly state = signal<DashboardState>({ kind: 'loading' });
  /** Prevents double-submit on retry (banking hard rule). */
  readonly isRetrying = signal(false);
  /** Session-level dismiss for stale banner. */
  readonly staleBannerDismissed = signal(false);

  // ---- Computed helpers ----
  readonly isLoading = computed(() => this.state().kind === 'loading');
  readonly isLoaded = computed(() => this.state().kind === 'loaded');
  readonly isEmpty = computed(() => this.state().kind === 'empty');
  readonly isError = computed(() => this.state().kind === 'error');
  readonly isForbidden = computed(() => this.state().kind === 'forbidden');

  readonly accounts = computed<ReadonlyArray<AccountViewDto>>(() => {
    const s = this.state();
    return s.kind === 'loaded' ? s.data.accounts : [];
  });

  readonly meta = computed<DashboardMeta | null>(() => {
    const s = this.state();
    if (s.kind === 'loaded') return s.data.meta;
    if (s.kind === 'empty') return s.meta;
    return null;
  });

  readonly showStaleBanner = computed(() => {
    const m = this.meta();
    if (!m || this.staleBannerDismissed()) return false;
    return this.stalenessService.isFreshnessSnapshot(m);
  });

  readonly errorCode = computed(() => {
    const s = this.state();
    return s.kind === 'error' ? s.code : '';
  });

  readonly correlationId = computed(() => {
    const s = this.state();
    if (s.kind === 'error') return s.correlationId;
    if (s.kind === 'forbidden') return s.correlationId;
    return '';
  });

  ngOnInit(): void {
    this.loadDashboard();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadDashboard(): void {
    this.state.set({ kind: 'loading' });
    this.dashboardService
      .loadDashboard()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (newState: DashboardState) => {
          if (newState.kind === 'unauthorized') {
            // 401 — redirect to login silently
            this.router.navigate(['/login']);
            return;
          }
          this.state.set(newState);
          this.isRetrying.set(false);
        },
        error: () => {
          // Should not reach here (DashboardService swallows to state)
          this.state.set({
            kind: 'error',
            code: 'SERVICE_UNAVAILABLE',
            correlationId: '',
            retryable: true,
          });
          this.isRetrying.set(false);
        },
      });
  }

  onRetry(): void {
    // Double-submit protection — ignore if already retrying
    if (this.isRetrying()) return;
    this.isRetrying.set(true);
    this.loadDashboard();
  }

  onStaleBannerRetry(): void {
    this.loadDashboard();
  }

  onStaleBannerDismiss(): void {
    this.staleBannerDismissed.set(true);
  }

  onRowSelect(_account: AccountViewDto): void {
    // R-FE-008: removed console.log (production code must not log to console).
    // Account detail navigation is deferred to v1.1 — see task-plan §v1.1 backlog.
    // TODO: v1.1 — navigate to account detail route when implemented.
  }
}
