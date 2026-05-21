/**
 * balance-dashboard-api.service.spec.ts
 * Step 1 tests — verify HTTP calls, shape, and string balance type.
 * Tests from task-plan §Step 1 BalanceDashboardApiServiceTest.
 */

import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { BalanceDashboardApiService } from './balance-dashboard-api.service';
import { BalanceDashboardResponse } from '../models/balance-dashboard.model';

const MOCK_RESPONSE: BalanceDashboardResponse = {
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
  ],
  meta: {
    accountCount: 1,
    freshness: 'live',
    cacheHit: false,
    correlationId: '7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60',
  },
};

describe('BalanceDashboardApiService', () => {
  let service: BalanceDashboardApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [BalanceDashboardApiService],
    });
    service = TestBed.inject(BalanceDashboardApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getDashboard_happyPath_returns200 — calls GET /api/v1/balance-dashboard and maps response', (done) => {
    service.getDashboard().subscribe({
      next: (response) => {
        expect(response.accounts.length).toBe(1);
        expect(response.meta.freshness).toBe('live');
        done();
      },
      error: done.fail,
    });

    const req = httpMock.expectOne('/api/v1/balance-dashboard');
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_RESPONSE);
  });

  it('getDashboard_401_throwsHttpErrorResponse — 401 propagates as error', (done) => {
    service.getDashboard().subscribe({
      next: () => done.fail('Expected error'),
      error: (err) => {
        expect(err.status).toBe(401);
        done();
      },
    });

    const req = httpMock.expectOne('/api/v1/balance-dashboard');
    req.flush({ code: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });
  });

  it('getDashboard_503_throwsHttpErrorResponse — 503 propagates as error', (done) => {
    service.getDashboard().subscribe({
      next: () => done.fail('Expected error'),
      error: (err) => {
        expect(err.status).toBe(503);
        done();
      },
    });

    const req = httpMock.expectOne('/api/v1/balance-dashboard');
    req.flush({ code: 'SERVICE_UNAVAILABLE' }, { status: 503, statusText: 'Service Unavailable' });
  });

  it('balance_fieldIsString_notParsedAsNumber — balance type is string after deserialization', (done) => {
    service.getDashboard().subscribe({
      next: (response) => {
        // CRITICAL: balance must be string, not number — prevents IEEE-754 precision loss
        expect(typeof response.accounts[0].balance).toBe('string');
        expect(response.accounts[0].balance).toBe('128540.25');
        done();
      },
      error: done.fail,
    });

    const req = httpMock.expectOne('/api/v1/balance-dashboard');
    // Simulate server returning balance as a quoted JSON string
    req.flush({
      ...MOCK_RESPONSE,
      accounts: [{ ...MOCK_RESPONSE.accounts[0], balance: '128540.25' }],
    });
  });
});
