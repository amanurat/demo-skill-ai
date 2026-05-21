/**
 * balance-comparison.e2e.spec.ts
 * Playwright E2E tests covering AC-001, AC-002, AC-003, AC-005.
 * Source: task-plan §Step 5 routing test cases + AC coverage map.
 *
 * Assumes:
 *   - Dev server running at http://localhost:4200
 *   - Mock API server OR browser dev-tools intercept configured
 */

import { test, expect, Page } from '@playwright/test';

const BASE_URL = 'http://localhost:4200';
const DASHBOARD_URL = `${BASE_URL}/balance-dashboard`;

// Helpers
const MOCK_LIVE_RESPONSE = {
  accounts: [
    {
      rank: 1,
      accountId: 'a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d',
      accountNumberMasked: '****7890',
      accountType: 'SAVINGS',
      balance: '128540.25',
      currency: 'THB',
      balanceAsOf: new Date(Date.now() - 2 * 60 * 1000).toISOString(),
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
      balanceAsOf: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
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
      balanceAsOf: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
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

async function mockApiAndNavigate(
  page: Page,
  responseBody: object,
  status = 200
): Promise<void> {
  await page.route('/api/v1/balance-dashboard', async (route) => {
    if (status === 200) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(responseBody),
        headers: {
          'X-Cache': 'MISS',
          'X-Correlation-Id': '7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60',
          'Cache-Control': 'private, no-store',
        },
      });
    } else {
      await route.fulfill({
        status,
        contentType: 'application/problem+json',
        body: JSON.stringify({ code: 'SERVICE_UNAVAILABLE', correlationId: 'test-corr' }),
      });
    }
  });

  // Set mock auth token so AuthGuard passes
  await page.addInitScript(() => {
    sessionStorage.setItem('auth_token', 'mock-jwt-for-e2e');
  });

  await page.goto(DASHBOARD_URL);
}

// ---- AC-001: Ranked list returned ----

test.describe('AC-001: Balance dashboard default state', () => {
  test('AC-001-H1: shows 3 accounts in ranked order with all required fields', async ({ page }) => {
    await mockApiAndNavigate(page, MOCK_LIVE_RESPONSE);

    // Page title
    await expect(page.locator('h1')).toHaveText('บัญชีของฉัน');

    // 3 account rows rendered
    const rows = page.locator('balance-account-row');
    await expect(rows).toHaveCount(3);

    // First row — rank 1, savings
    const firstRow = rows.nth(0);
    await expect(firstRow.locator('[data-testid="account-type-label"]')).toContainText('บัญชีออมทรัพย์');
    await expect(firstRow.locator('[data-testid="masked-account-number"]')).toHaveText('****7890');

    // Balance displayed (not hidden, not zero)
    const balanceEl = firstRow.locator('[data-testid="balance-amount"]');
    await expect(balanceEl).toBeVisible();
    const balanceText = await balanceEl.textContent();
    expect(balanceText).toContain('128');
  });

  test('AC-001-H2: accounts rendered in RECEIVED order (no FE re-sort)', async ({ page }) => {
    await mockApiAndNavigate(page, MOCK_LIVE_RESPONSE);

    const rows = page.locator('balance-account-row');
    await expect(rows).toHaveCount(3);

    // Verify rank order = DOM order
    const firstTypeLabel = await rows.nth(0).locator('[data-testid="account-type-label"]').textContent();
    expect(firstTypeLabel).toContain('บัญชีออมทรัพย์'); // rank 1 = savings

    const thirdTypeLabel = await rows.nth(2).locator('[data-testid="account-type-label"]').textContent();
    expect(thirdTypeLabel).toContain('บัญชีเงินฝากประจำ'); // rank 3 = fixed deposit
  });

  test('AC-001-H4: currency code shown on each row', async ({ page }) => {
    await mockApiAndNavigate(page, MOCK_LIVE_RESPONSE);

    const currencyLabels = page.locator('[data-testid="currency-code"]');
    const count = await currencyLabels.count();
    expect(count).toBe(3);
    for (let i = 0; i < count; i++) {
      await expect(currencyLabels.nth(i)).toHaveText('THB');
    }
  });

  test('AC-001-E1: empty state shown when accounts=[]', async ({ page }) => {
    await mockApiAndNavigate(page, {
      accounts: [],
      meta: { accountCount: 0, freshness: 'live', cacheHit: false, correlationId: 'test' },
    });

    await expect(page.locator('[data-testid="empty-state-region"]')).toBeVisible();
    await expect(page.locator('[data-testid="empty-state-heading"]')).toContainText('ยังไม่มีบัญชีที่ใช้งานอยู่');
  });
});

// ---- AC-002: Account display fields ----

test.describe('AC-002: Account row display fields', () => {
  test('AC-002-H1: all display fields present — type, masked number, balance, currency, last-updated', async ({ page }) => {
    await mockApiAndNavigate(page, MOCK_LIVE_RESPONSE);

    const firstRow = page.locator('balance-account-row').nth(0);

    await expect(firstRow.locator('[data-testid="account-type-label"]')).toBeVisible();
    await expect(firstRow.locator('[data-testid="masked-account-number"]')).toBeVisible();
    await expect(firstRow.locator('[data-testid="balance-amount"]')).toBeVisible();
    await expect(firstRow.locator('[data-testid="currency-code"]')).toBeVisible();
    await expect(firstRow.locator('[data-testid="last-updated"]')).toBeVisible();
  });

  test('AC-002-H3: masked account number never shows full account number', async ({ page }) => {
    await mockApiAndNavigate(page, MOCK_LIVE_RESPONSE);

    const maskedEls = page.locator('[data-testid="masked-account-number"]');
    const count = await maskedEls.count();
    for (let i = 0; i < count; i++) {
      const text = await maskedEls.nth(i).textContent();
      expect(text?.trim()).toMatch(/^\*+\d{4}$/);
    }
  });
});

// ---- AC-003: Cache/staleness behavior ----

test.describe('AC-003: Stale/snapshot state', () => {
  test('AC-003-H1: snapshot freshness shows global stale banner', async ({ page }) => {
    const snapshotResponse = {
      ...MOCK_LIVE_RESPONSE,
      meta: { ...MOCK_LIVE_RESPONSE.meta, freshness: 'snapshot', cacheHit: true },
    };

    await mockApiAndNavigate(page, snapshotResponse);

    await expect(page.locator('[data-testid="stale-banner-region"]')).toBeVisible();
    await expect(page.locator('[data-testid="stale-banner"]')).toBeVisible();
  });

  test('AC-003-H4: balanceAsOf from server shown in last-updated field', async ({ page }) => {
    await mockApiAndNavigate(page, MOCK_LIVE_RESPONSE);

    const lastUpdated = page.locator('[data-testid="last-updated"]').first();
    await expect(lastUpdated).toBeVisible();
    // Should not be empty
    const text = await lastUpdated.textContent();
    expect(text?.trim().length).toBeGreaterThan(0);
  });
});

// ---- AC-005: Accessibility and responsive ----

test.describe('AC-005: Accessibility', () => {
  test('AC-005-H1: no horizontal scroll at 375px viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await mockApiAndNavigate(page, MOCK_LIVE_RESPONSE);

    // Check that body scroll width does not exceed viewport width
    const scrollWidth = await page.evaluate(() => document.body.scrollWidth);
    const viewportWidth = await page.evaluate(() => window.innerWidth);
    expect(scrollWidth).toBeLessThanOrEqual(viewportWidth);
  });

  test('AC-005-H2: account rows have composed aria-label with rank, type, balance', async ({ page }) => {
    await mockApiAndNavigate(page, MOCK_LIVE_RESPONSE);

    const firstRowButton = page.locator('balance-account-row').nth(0).locator('button');
    const ariaLabel = await firstRowButton.getAttribute('aria-label');
    expect(ariaLabel).toContain('1');        // rank
    expect(ariaLabel).toContain('บาท');      // balance spoken form
    expect(ariaLabel).toContain('7890');     // last 4 digits
  });

  test('AC-005-H3: keyboard Tab navigation — focus order matches rank order', async ({ page }) => {
    await mockApiAndNavigate(page, MOCK_LIVE_RESPONSE);

    // Tab to first focusable element in account list
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab'); // skip to first row (past refresh button)

    // After tabbing, first account row button should be focused
    const focused = page.locator('balance-account-row button:focus-visible').nth(0);
    const ariaLabel = await focused.getAttribute('aria-label');
    expect(ariaLabel).toContain('1'); // rank 1 is first in focus order
  });

  test('error state: 503 shows retry button', async ({ page }) => {
    await page.route('/api/v1/balance-dashboard', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        body: JSON.stringify({ code: 'SERVICE_UNAVAILABLE', correlationId: 'e2e-503' }),
      });
    });

    await page.addInitScript(() => {
      sessionStorage.setItem('auth_token', 'mock-jwt-for-e2e');
    });

    await page.goto(DASHBOARD_URL);

    await expect(page.locator('[data-testid="error-state-region"]')).toBeVisible();
    await expect(page.locator('[data-testid="error-retry-btn"]')).toBeVisible();
  });
});
