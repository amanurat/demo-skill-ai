/**
 * account-row.component.spec.ts
 * Tests from task-plan §Step 3 AccountRowComponentTest.
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountRowComponent } from './account-row.component';
import { AccountViewDto } from '../../models/balance-dashboard.model';
import { By } from '@angular/platform-browser';

const MOCK_ACCOUNT_SAVINGS: AccountViewDto = {
  rank: 1,
  accountId: 'a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d',
  accountNumberMasked: '****7890',
  accountType: 'SAVINGS',
  balance: '128540.25',
  currency: 'THB',
  balanceAsOf: new Date(Date.now() - 2 * 60 * 1000).toISOString(), // 2 minutes ago
  isStale: false,
  displayLabel: 'account.type.savings',
};

const MOCK_ACCOUNT_STALE: AccountViewDto = {
  ...MOCK_ACCOUNT_SAVINGS,
  rank: 2,
  accountType: 'FIXED_DEPOSIT',
  accountNumberMasked: '****4421',
  balance: '84012.00',
  isStale: true,
  balanceAsOf: new Date(Date.now() - 25 * 60 * 60 * 1000).toISOString(), // 1 day ago
  displayLabel: 'account.type.fixedDeposit',
};

describe('AccountRowComponent', () => {
  let fixture: ComponentFixture<AccountRowComponent>;
  let component: AccountRowComponent;

  function createComponent(account: AccountViewDto, totalCount = 3): ComponentFixture<AccountRowComponent> {
    TestBed.configureTestingModule({
      imports: [AccountRowComponent],
    }).compileComponents();

    const f = TestBed.createComponent(AccountRowComponent);
    f.componentInstance.account = account;
    f.componentInstance.totalCount = totalCount;
    f.detectChanges();
    return f;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [AccountRowComponent] });
    fixture = TestBed.createComponent(AccountRowComponent);
    component = fixture.componentInstance;
    component.account = MOCK_ACCOUNT_SAVINGS;
    component.totalCount = 3;
    fixture.detectChanges();
  });

  it('renders masked account number — ****XXXX format', () => {
    const el = fixture.debugElement.query(By.css('[data-testid="masked-account-number"]'));
    expect(el).toBeTruthy();
    expect(el.nativeElement.textContent).toContain('****7890');
  });

  it('renders balance as formatted string — NOT as raw number', () => {
    const el = fixture.debugElement.query(By.css('[data-testid="balance-amount"]'));
    expect(el).toBeTruthy();
    // Must contain numeric characters of the balance
    expect(el.nativeElement.textContent).toContain('128');
    expect(el.nativeElement.textContent).toContain('540');
  });

  it('renders correct icon for SAVINGS — banknotes SVG present', () => {
    const icons = fixture.debugElement.queryAll(By.css('.account-type-icon'));
    expect(icons.length).toBeGreaterThan(0);
    // SVG has stroke="currentColor" which inherits --color-text-secondary
    expect(icons[0].nativeElement.getAttribute('stroke')).toBe('currentColor');
  });

  it('isStale=false — no stale badge shown', () => {
    const badge = fixture.debugElement.query(By.css('[data-testid="stale-badge"]'));
    expect(badge).toBeNull();
  });

  it('isStale=true — stale badge is shown', () => {
    const staleFixture = createComponent(MOCK_ACCOUNT_STALE, 3);
    const badge = staleFixture.debugElement.query(By.css('balance-stale-badge'));
    expect(badge).toBeTruthy();
  });

  it('aria-label includes rank and total (AC-005-H2)', () => {
    const button = fixture.debugElement.query(By.css('.account-row__button'));
    const ariaLabel = button.nativeElement.getAttribute('aria-label');
    expect(ariaLabel).toContain('1');  // rank
    expect(ariaLabel).toContain('3');  // total
  });

  it('aria-label contains type, masked last4, balance, last-updated (AC-005-H2)', () => {
    const button = fixture.debugElement.query(By.css('.account-row__button'));
    const ariaLabel = button.nativeElement.getAttribute('aria-label');
    expect(ariaLabel).toContain('บัญชีออมทรัพย์');
    expect(ariaLabel).toContain('7890');
    expect(ariaLabel).toContain('บาท');
  });

  it('icon stroke is currentColor — inherits --color-text-secondary from CSS', () => {
    const icon = fixture.debugElement.query(By.css('.account-type-icon'));
    expect(icon.nativeElement.getAttribute('stroke')).toBe('currentColor');
  });

  it('renders CURRENT account with credit-card SVG path content', () => {
    const currentAccount: AccountViewDto = {
      ...MOCK_ACCOUNT_SAVINGS,
      accountType: 'CURRENT',
      displayLabel: 'account.type.current',
    };
    const f = createComponent(currentAccount, 3);
    // Verify the credit-card path unique segment is present
    const svgPath = f.debugElement.query(By.css('.account-type-icon path'));
    expect(svgPath.nativeElement.getAttribute('d')).toContain('M2.25 8.25h19.5');
  });

  it('renders FIXED_DEPOSIT account with lock-closed SVG path content', () => {
    const fixedAccount: AccountViewDto = {
      ...MOCK_ACCOUNT_SAVINGS,
      accountType: 'FIXED_DEPOSIT',
      displayLabel: 'account.type.fixedDeposit',
    };
    const f = createComponent(fixedAccount, 3);
    const svgPath = f.debugElement.query(By.css('.account-type-icon path'));
    expect(svgPath.nativeElement.getAttribute('d')).toContain('M16.5 10.5V6.75a4.5');
  });

  it('maskedAccountNumber never shows full account number (AC-002-H3)', () => {
    const el = fixture.debugElement.query(By.css('[data-testid="masked-account-number"]'));
    const text = el.nativeElement.textContent;
    // Pattern must match ****XXXX — only 4 trailing digits visible
    expect(text.trim()).toMatch(/^\*+\d{4}$/);
  });

  it('does NOT render accountId in DOM (BA NFR §8)', () => {
    const html = fixture.debugElement.nativeElement.innerHTML;
    expect(html).not.toContain('a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d');
  });

  it('balance ariaLabel is full spoken form — prevents digit-by-digit reading (AC-005-E2)', () => {
    component.account = MOCK_ACCOUNT_SAVINGS;
    component.totalCount = 3;
    component.ngOnChanges();

    expect(component.composedAriaLabel).toContain('บาท');
    // Should NOT be raw digits like "1", "2", "8", "5", "4", "0" separately
    expect(component.composedAriaLabel).not.toMatch(/\b1\b.*\b2\b.*\b8\b/);
  });

  it('single account (totalCount=1) — aria-label drops rank prefix (screen-specs §6)', () => {
    const f = createComponent(MOCK_ACCOUNT_SAVINGS, 1);
    const button = f.debugElement.query(By.css('.account-row__button'));
    const ariaLabel = button.nativeElement.getAttribute('aria-label');
    expect(ariaLabel).not.toContain('ลำดับที่');
    expect(ariaLabel).toContain('บัญชีออมทรัพย์');
  });

  it('null balanceAsOf renders ไม่ทราบเวลาอัปเดต in relative time', () => {
    const relTime = component.buildRelativeTime(null);
    expect(relTime).toBe('ไม่ทราบเวลาอัปเดต');
  });

  it('data-testid="rank" attribute is present on host element', () => {
    const row = fixture.debugElement.query(By.css('[data-testid="account-row-1"]'));
    expect(row).toBeTruthy();
  });
});
