/**
 * pipes.spec.ts
 * Unit tests for AccountTypeLabelPipe, AccountTypeIconPipe, BalanceAmountPipe.
 * Tests from task-plan §Step 2.
 */

import { AccountTypeLabelPipe } from './account-type-label.pipe';
import { AccountTypeIconPipe } from './account-type-icon.pipe';
import { BalanceAmountPipe } from './balance-amount.pipe';

// ---- AccountTypeLabelPipe ----

describe('AccountTypeLabelPipe', () => {
  let pipe: AccountTypeLabelPipe;

  beforeEach(() => { pipe = new AccountTypeLabelPipe(); });

  it('SAVINGS TH — returns บัญชีออมทรัพย์', () => {
    expect(pipe.transform('SAVINGS', 'th')).toBe('บัญชีออมทรัพย์');
  });

  it('CURRENT TH — returns บัญชีกระแสรายวัน', () => {
    expect(pipe.transform('CURRENT', 'th')).toBe('บัญชีกระแสรายวัน');
  });

  it('FIXED_DEPOSIT TH — returns บัญชีเงินฝากประจำ', () => {
    expect(pipe.transform('FIXED_DEPOSIT', 'th')).toBe('บัญชีเงินฝากประจำ');
  });

  it('SAVINGS EN — returns Savings Account', () => {
    expect(pipe.transform('SAVINGS', 'en')).toBe('Savings Account');
  });

  it('unknown type — returns raw enum value as safe fallback', () => {
    // Cast to test unknown type safety
    const unknownType = 'UNKNOWN' as any;
    expect(pipe.transform(unknownType, 'th')).toBe('UNKNOWN');
  });
});

// ---- AccountTypeIconPipe ----

describe('AccountTypeIconPipe', () => {
  let pipe: AccountTypeIconPipe;

  beforeEach(() => { pipe = new AccountTypeIconPipe(); });

  it('SAVINGS → banknotes', () => {
    expect(pipe.transform('SAVINGS')).toBe('banknotes');
  });

  it('CURRENT → credit-card', () => {
    expect(pipe.transform('CURRENT')).toBe('credit-card');
  });

  it('FIXED_DEPOSIT → lock-closed', () => {
    expect(pipe.transform('FIXED_DEPOSIT')).toBe('lock-closed');
  });
});

// ---- BalanceAmountPipe ----

describe('BalanceAmountPipe', () => {
  let pipe: BalanceAmountPipe;

  beforeEach(() => { pipe = new BalanceAmountPipe(); });

  it('formats balance string with THB currency display', () => {
    const result = pipe.transform('128540.25');
    expect(result.display).toContain('128');
    expect(result.display).toContain('540');
  });

  it('ariaLabel uses spoken form with บาท สตางค์ — not digit-by-digit (AC-005-E2)', () => {
    const result = pipe.transform('128540.25');
    expect(result.ariaLabel).toContain('บาท');
    expect(result.ariaLabel).toContain('สตางค์');
    expect(result.ariaLabel).toContain('25');
  });

  it('ariaLabel omits สตางค์ when value is whole baht', () => {
    const result = pipe.transform('45000.00');
    expect(result.ariaLabel).toContain('บาท');
    expect(result.ariaLabel).not.toContain('สตางค์');
  });

  it('null value returns em dash display and ไม่ทราบยอดเงิน aria', () => {
    const result = pipe.transform(null);
    expect(result.display).toBe('—');
    expect(result.ariaLabel).toBe('ไม่ทราบยอดเงิน');
  });

  it('undefined value returns em dash', () => {
    const result = pipe.transform(undefined);
    expect(result.display).toBe('—');
  });

  it('balance field remains string — pipe does not parse for math (type contract)', () => {
    // The pipe accepts string input; if it receives number it would be a caller error
    const result = pipe.transform('12500.50');
    expect(result.display).toBeTruthy();
    expect(result.ariaLabel).toContain('50 สตางค์');
  });

  // R-FE-004: precision test — satang parsed from string, not float arithmetic
  it('satang extracted from string directly — no float arithmetic precision loss', () => {
    // 128540.25: if parsed as float, (128540.25 - 128540) * 100 can be 24.999...
    // String-based extraction must return exactly 25
    const result = pipe.transform('128540.25');
    expect(result.ariaLabel).toContain('25 สตางค์');
    expect(result.ariaLabel).not.toContain('24');
  });

  it('zero satang — only บาท portion in aria label', () => {
    const result = pipe.transform('50000.00');
    expect(result.ariaLabel).toContain('บาท');
    expect(result.ariaLabel).not.toContain('สตางค์');
  });
});
