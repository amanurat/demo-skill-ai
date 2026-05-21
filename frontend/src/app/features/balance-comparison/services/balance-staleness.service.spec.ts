/**
 * balance-staleness.service.spec.ts
 * Tests from task-plan §Step 2 BalanceStalenessServiceTest.
 */

import { TestBed } from '@angular/core/testing';
import { BalanceStalenessService } from './balance-staleness.service';
import { AccountViewDto, DashboardMeta } from '../models/balance-dashboard.model';

const makeMeta = (freshness: 'live' | 'snapshot' | 'stale'): DashboardMeta => ({
  accountCount: 1,
  freshness,
  cacheHit: false,
  correlationId: 'corr-123',
});

const makeAccount = (isStale: boolean): AccountViewDto => ({
  rank: 1,
  accountId: 'test-id',
  accountNumberMasked: '****1234',
  accountType: 'SAVINGS',
  balance: '1000.00',
  currency: 'THB',
  balanceAsOf: '2026-05-21T08:00:00Z',
  isStale,
  displayLabel: 'account.type.savings',
});

describe('BalanceStalenessService', () => {
  let service: BalanceStalenessService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [BalanceStalenessService] });
    service = TestBed.inject(BalanceStalenessService);
  });

  // Banner tests
  it('isFreshnessSnapshot — freshness=snapshot returns true for banner', () => {
    expect(service.isFreshnessSnapshot(makeMeta('snapshot'))).toBeTrue();
  });

  it('isFreshnessSnapshot — freshness=stale returns true for banner', () => {
    expect(service.isFreshnessSnapshot(makeMeta('stale'))).toBeTrue();
  });

  it('isFreshnessSnapshot — freshness=live returns false', () => {
    expect(service.isFreshnessSnapshot(makeMeta('live'))).toBeFalse();
  });

  // Per-row stale badge tests
  it('isRowStale — isStale=true on row shows badge', () => {
    expect(service.isRowStale(makeAccount(true))).toBeTrue();
  });

  it('isRowStale — isStale=false no badge', () => {
    expect(service.isRowStale(makeAccount(false))).toBeFalse();
  });
});
