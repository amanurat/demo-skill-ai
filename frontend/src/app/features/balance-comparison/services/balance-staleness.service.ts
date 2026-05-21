/**
 * balance-staleness.service.ts
 * Stateless helper — freshness signal and per-row staleness evaluation.
 * Source: task-plan §Step 2, implementation-notes §8.3.
 */

import { Injectable } from '@angular/core';
import { AccountViewDto, DashboardMeta, Freshness } from '../models/balance-dashboard.model';

@Injectable({ providedIn: 'root' })
export class BalanceStalenessService {
  /**
   * Returns true when the global staleness banner should be shown.
   * Banner is shown for 'snapshot' or 'stale' freshness (not 'live').
   */
  isFreshnessSnapshot(meta: DashboardMeta): boolean {
    return meta.freshness === 'snapshot' || meta.freshness === 'stale';
  }

  /**
   * Returns the freshness value from meta.
   * Convenience accessor for banner copy selection.
   */
  getFreshness(meta: DashboardMeta): Freshness {
    return meta.freshness;
  }

  /**
   * Returns true when the per-row staleness badge should be rendered.
   * Driven by server-computed account.isStale (now() - balanceAsOf > 60s).
   * INDEPENDENT of meta.freshness.
   */
  isRowStale(account: AccountViewDto): boolean {
    return account.isStale;
  }
}
