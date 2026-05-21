/**
 * dashboard.service.spec.ts
 * Mock HttpClient tests (Step 2 — tests first, then implement).
 * Tests from task-plan §Step 2 BalanceDashboardFacadeTest.
 */

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DashboardService } from './dashboard.service';
import { BalanceDashboardResponse } from '../models/balance-dashboard.model';

const THREE_ACCOUNTS_RESPONSE: BalanceDashboardResponse = {
  accounts: [
    {
      rank: 1,
      accountId: 'a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d',
      accountNumberMasked: '****7890',
      accountType: 'SAVINGS',
      balance: '128540.25',
      currency: 'THB',
      balanceAsOf: '2026-05-21T08:00:00Z',
      isStale: false,
      displayLabel: 'account.type.savings',
    },
    {
      rank: 2,
      accountId: 'b2c3d4e5-6f7a-4b8c-9d0e-1f2a3b4c5d6e',
      accountNumberMasked: '****1234',
      accountType: 'CURRENT',
      balance: '45000.00',
      currency: 'THB',
      balanceAsOf: '2026-05-21T08:00:00Z',
      isStale: false,
      displayLabel: 'account.type.current',
    },
    {
      rank: 3,
      accountId: 'c3d4e5f6-7a8b-4c9d-0e1f-2a3b4c5d6e7f',
      accountNumberMasked: '****5678',
      accountType: 'FIXED_DEPOSIT',
      balance: '12500.50',
      currency: 'THB',
      balanceAsOf: '2026-05-20T09:30:00Z',
      isStale: false,
      displayLabel: 'account.type.fixedDeposit',
    },
  ],
  meta: {
    accountCount: 3,
    freshness: 'live',
    cacheHit: false,
    correlationId: '7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60',
  },
};

const EMPTY_RESPONSE: BalanceDashboardResponse = {
  accounts: [],
  meta: {
    accountCount: 0,
    freshness: 'live',
    cacheHit: false,
    correlationId: '7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60',
  },
};

describe('DashboardService', () => {
  let service: DashboardService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [DashboardService],
    });
    service = TestBed.inject(DashboardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('success path — emits loaded state with data', (done) => {
    service.loadDashboard().subscribe({
      next: (state) => {
        expect(state.kind).toBe('loaded');
        if (state.kind === 'loaded') {
          expect(state.data.accounts.length).toBe(3);
        }
        done();
      },
    });

    const req = httpMock.expectOne('/api/v1/balance-dashboard');
    req.flush(THREE_ACCOUNTS_RESPONSE);
  });

  it('403 — emits forbidden state', (done) => {
    service.loadDashboard().subscribe({
      next: (state) => {
        expect(state.kind).toBe('forbidden');
        done();
      },
    });

    const req = httpMock.expectOne('/api/v1/balance-dashboard');
    req.flush(
      { code: 'FORBIDDEN', correlationId: 'corr-403' },
      { status: 403, statusText: 'Forbidden' }
    );
  });

  it('401 — emits unauthorized state', (done) => {
    service.loadDashboard().subscribe({
      next: (state) => {
        expect(state.kind).toBe('unauthorized');
        done();
      },
    });

    const req = httpMock.expectOne('/api/v1/balance-dashboard');
    req.flush(
      { code: 'UNAUTHORIZED' },
      { status: 401, statusText: 'Unauthorized' }
    );
  });

  it('503 after max retries — emits error state with SERVICE_UNAVAILABLE', fakeAsync(() => {
    let finalState: any;
    service.loadDashboard().subscribe({
      next: (state) => {
        finalState = state;
      },
    });

    // Initial attempt
    httpMock.expectOne('/api/v1/balance-dashboard').flush(
      { code: 'SERVICE_UNAVAILABLE', correlationId: 'corr-503' },
      { status: 503, statusText: 'Service Unavailable' }
    );

    // Retry 1 (after 1s)
    tick(1000);
    httpMock.expectOne('/api/v1/balance-dashboard').flush(
      { code: 'SERVICE_UNAVAILABLE', correlationId: 'corr-503' },
      { status: 503, statusText: 'Service Unavailable' }
    );

    // Retry 2 (after 2s)
    tick(2000);
    httpMock.expectOne('/api/v1/balance-dashboard').flush(
      { code: 'SERVICE_UNAVAILABLE', correlationId: 'corr-503' },
      { status: 503, statusText: 'Service Unavailable' }
    );

    // Retry 3 (after 4s)
    tick(4000);
    httpMock.expectOne('/api/v1/balance-dashboard').flush(
      { code: 'SERVICE_UNAVAILABLE', correlationId: 'corr-503' },
      { status: 503, statusText: 'Service Unavailable' }
    );

    expect(finalState).toBeTruthy();
    expect(finalState.kind).toBe('error');
    expect(finalState.code).toBe('SERVICE_UNAVAILABLE');
    expect(finalState.retryable).toBeTrue();
  }));

  it('empty accounts[] — emits empty state', (done) => {
    service.loadDashboard().subscribe({
      next: (state) => {
        expect(state.kind).toBe('empty');
        done();
      },
    });

    const req = httpMock.expectOne('/api/v1/balance-dashboard');
    req.flush(EMPTY_RESPONSE);
  });

  it('loading$ state transitions — observable emits synchronously from HTTP mock', (done) => {
    const states: string[] = [];
    service.loadDashboard().subscribe({
      next: (state) => {
        states.push(state.kind);
        if (state.kind !== 'loading') {
          expect(states[states.length - 1]).toBe('loaded');
          done();
        }
      },
    });

    const req = httpMock.expectOne('/api/v1/balance-dashboard');
    req.flush(THREE_ACCOUNTS_RESPONSE);
  });
});
