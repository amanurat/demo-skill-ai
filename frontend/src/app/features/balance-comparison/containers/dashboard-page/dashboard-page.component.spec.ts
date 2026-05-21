/**
 * dashboard-page.component.spec.ts
 * Integration tests (Step 4). All 5 states tested.
 * Tests from task-plan §Step 4 BalanceDashboardPageComponentTest.
 *
 * CRITICAL — test case 8: verify accounts rendered in RECEIVED order (no re-sort).
 */

import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';

import { DashboardPageComponent } from './dashboard-page.component';
import { DashboardService } from '../../services/dashboard.service';
import { BalanceStalenessService } from '../../services/balance-staleness.service';
import { DashboardState } from '../../models/balance-dashboard.model';

// Mock DashboardService
class MockDashboardService {
  returnState: DashboardState = { kind: 'loading' };
  loadDashboard() {
    return of(this.returnState);
  }
}

const THREE_ACCOUNTS_LOADED: DashboardState = {
  kind: 'loaded',
  data: {
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
  },
};

describe('DashboardPageComponent', () => {
  let fixture: ComponentFixture<DashboardPageComponent>;
  let component: DashboardPageComponent;
  let mockService: MockDashboardService;

  beforeEach(async () => {
    mockService = new MockDashboardService();

    await TestBed.configureTestingModule({
      imports: [DashboardPageComponent, RouterTestingModule],
      providers: [
        { provide: DashboardService, useValue: mockService },
        BalanceStalenessService,
      ],
    }).compileComponents();
  });

  function createWithState(state: DashboardState): ComponentFixture<DashboardPageComponent> {
    mockService.returnState = state;
    const f = TestBed.createComponent(DashboardPageComponent);
    f.detectChanges();
    return f;
  }

  // Test 1: on init → calls loadDashboard()
  it('on init — calls loadDashboard()', () => {
    const spy = spyOn(mockService, 'loadDashboard').and.callThrough();
    mockService.returnState = THREE_ACCOUNTS_LOADED;
    fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();
    expect(spy).toHaveBeenCalledOnceWith();
  });

  // Test 2: loaded state — renders AccountRowComponent for each account in RECEIVED ORDER
  it('loaded state — renders account rows for each account', () => {
    fixture = createWithState(THREE_ACCOUNTS_LOADED);
    const rows = fixture.debugElement.queryAll(By.css('balance-account-row'));
    expect(rows.length).toBe(3);
  });

  // Test 3: empty state — renders EmptyStateComponent
  it('empty state — renders BalanceEmptyStateComponent', () => {
    fixture = createWithState({
      kind: 'empty',
      meta: { accountCount: 0, freshness: 'live', cacheHit: false, correlationId: '' },
    });
    const emptyState = fixture.debugElement.query(By.css('[data-testid="empty-state-region"]'));
    expect(emptyState).toBeTruthy();
  });

  // Test 4: 503 error — renders ErrorRetryBannerComponent with retry
  it('error state — renders error-retry-banner with retry', () => {
    fixture = createWithState({
      kind: 'error',
      code: 'SERVICE_UNAVAILABLE',
      correlationId: 'corr-503',
      retryable: true,
    });
    const errorBanner = fixture.debugElement.query(By.css('[data-testid="error-state-region"]'));
    expect(errorBanner).toBeTruthy();
  });

  // Test 5: 403 — forbidden state renders generic error (no retry)
  it('403 — forbidden state shows generic error', () => {
    fixture = createWithState({
      kind: 'forbidden',
      correlationId: 'corr-403',
    });
    const forbidden = fixture.debugElement.query(By.css('[data-testid="forbidden-state-region"]'));
    expect(forbidden).toBeTruthy();
  });

  // Test 6: 401 — calls router.navigate(['/login'])
  it('401 — calls router.navigate to /login', () => {
    mockService.returnState = { kind: 'unauthorized' };
    fixture = TestBed.createComponent(DashboardPageComponent);
    component = fixture.componentInstance;
    const router = TestBed.inject(require('@angular/router').Router);
    const navSpy = spyOn(router, 'navigate');
    fixture.detectChanges();
    expect(navSpy).toHaveBeenCalledWith(['/login']);
  });

  // Test 7: meta.freshness=snapshot — renders StaleBannerComponent
  it('freshness=snapshot — renders stale banner', () => {
    const snapshotState: DashboardState = {
      ...THREE_ACCOUNTS_LOADED,
      data: {
        ...THREE_ACCOUNTS_LOADED.data,
        meta: { ...THREE_ACCOUNTS_LOADED.data.meta, freshness: 'snapshot' },
      },
    };
    fixture = createWithState(snapshotState);
    const staleBanner = fixture.debugElement.query(By.css('[data-testid="stale-banner-region"]'));
    expect(staleBanner).toBeTruthy();
  });

  // Test 8 (CRITICAL): accounts rendered in RECEIVED order — NO FE re-sort
  it('accounts rendered in RECEIVED array order — no re-sort (task-plan critical constraint)', () => {
    // Accounts intentionally have rank=1 on UUID "z-id" and rank=2 on UUID "a-id"
    // A re-sort by accountId ASC would reverse them — this test detects that.
    const noResortState: DashboardState = {
      kind: 'loaded',
      data: {
        accounts: [
          {
            rank: 1,
            accountId: 'z-z-z-z-z',  // lexicographically LAST UUID
            accountNumberMasked: '****0001',
            accountType: 'SAVINGS',
            balance: '100.00',
            currency: 'THB',
            balanceAsOf: '2026-05-21T08:00:00Z',
            isStale: false,
            displayLabel: 'account.type.savings',
          },
          {
            rank: 2,
            accountId: 'a-a-a-a-a',  // lexicographically FIRST UUID
            accountNumberMasked: '****0002',
            accountType: 'CURRENT',
            balance: '50.00',
            currency: 'THB',
            balanceAsOf: '2026-05-21T08:00:00Z',
            isStale: false,
            displayLabel: 'account.type.current',
          },
        ],
        meta: {
          accountCount: 2,
          freshness: 'live',
          cacheHit: false,
          correlationId: 'test-corr',
        },
      },
    };

    fixture = createWithState(noResortState);
    const rows = fixture.debugElement.queryAll(By.css('balance-account-row'));

    expect(rows.length).toBe(2);

    // First rendered row MUST be rank=1 (accountId z-z-z-z-z, NOT re-sorted to a-a-a-a-a)
    const firstRowAccount = rows[0].componentInstance.account;
    expect(firstRowAccount.rank).toBe(1);
    expect(firstRowAccount.accountId).toBe('z-z-z-z-z');

    // Second rendered row MUST be rank=2
    const secondRowAccount = rows[1].componentInstance.account;
    expect(secondRowAccount.rank).toBe(2);
    expect(secondRowAccount.accountId).toBe('a-a-a-a-a');
  });

  // Loading state test
  it('loading state — renders loading skeleton', () => {
    mockService.returnState = { kind: 'loading' } as any;
    // Override to not complete immediately
    spyOn(mockService, 'loadDashboard').and.returnValue(new (require('rxjs').Subject)().asObservable());
    fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();
    // Initial state is loading
    expect(fixture.componentInstance.isLoading()).toBeTrue();
    const skeleton = fixture.debugElement.query(By.css('[data-testid="loading-state"]'));
    expect(skeleton).toBeTruthy();
  });

  // Keyboard navigation: Tab order follows DOM (focus order = rank order)
  it('keyboard navigation — tab order matches account rank order (AC-005-H3)', () => {
    fixture = createWithState(THREE_ACCOUNTS_LOADED);
    const rows = fixture.debugElement.queryAll(By.css('balance-account-row'));
    // Verify account rank is 1, 2, 3 in DOM order
    expect(rows[0].componentInstance.account.rank).toBe(1);
    expect(rows[1].componentInstance.account.rank).toBe(2);
    expect(rows[2].componentInstance.account.rank).toBe(3);
  });
});
