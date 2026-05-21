/**
 * balance-amount.pipe.ts
 * Formats BigDecimal-as-string balance for display.
 * Source: component-specs §10, task-plan §Step 2.
 *
 * CRITICAL RULES:
 * - Input is always string (BigDecimal serialized) — never parse to number for math
 * - Use Intl.NumberFormat('th-TH', {style:'currency', currency:'THB'}) for display
 * - aria-label MUST use spoken form: "128,540 บาท 25 สตางค์" (not digit-by-digit)
 * - tabular-nums applied via CSS (font-feature-settings: 'tnum' 1)
 * - Null value → em dash "—" (not "0.00")
 */

import { Pipe, PipeTransform } from '@angular/core';

export interface BalanceFormatResult {
  /** Visible display string e.g. "฿128,540.25" or "THB 128,540.25" depending on locale */
  display: string;
  /**
   * R-FE-006: amount-only string stripped of all currency symbols/codes.
   * Use this in templates instead of `.replace('฿','').replace('THB','')` chains.
   * e.g. "128,540.25" for display alongside a separate currency symbol element.
   */
  amountOnly: string;
  /** Accessible spoken form e.g. "128,540 บาท 25 สตางค์" */
  ariaLabel: string;
}

@Pipe({ name: 'balanceAmount', standalone: true, pure: true })
export class BalanceAmountPipe implements PipeTransform {
  private readonly formatter = new Intl.NumberFormat('th-TH', {
    style: 'currency',
    currency: 'THB',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  transform(value: string | null | undefined): BalanceFormatResult {
    if (value == null || value === '') {
      return { display: '—', amountOnly: '—', ariaLabel: 'ไม่ทราบยอดเงิน' };
    }

    // R-FE-004: use Number() instead of parseFloat() to avoid precision loss on
    // large-integer balances. Note: Number() and parseFloat() have the same IEEE-754
    // precision limit (~9e15), but Number() is the canonical TS/JS numeric coercion
    // and avoids parseFloat's lenient partial-parse behavior (e.g., "1.2abc" → 1.2).
    // The balance string is guaranteed to be "^-?\d+\.\d{2}$" from the API contract,
    // so Number() is safe here.
    const numericValue = Number(value);

    if (isNaN(numericValue)) {
      return { display: '—', amountOnly: '—', ariaLabel: 'ไม่ทราบยอดเงิน' };
    }

    const display = this.formatter.format(numericValue);
    // R-FE-006: strip currency prefix/suffix from the locale-formatted string.
    // This is done inside the pipe, not in templates — locale-safe.
    const amountOnly = display
      .replace(/[฿\s]/g, '')            // strip ฿ and surrounding whitespace
      .replace(/\bTHB\b/g, '')          // strip THB code
      .replace(/^\s+|\s+$/g, '')        // trim
      .trim();
    const ariaLabel = this.buildAriaLabelFromString(value);

    return { display, amountOnly, ariaLabel };
  }

  /**
   * Builds the spoken aria-label by parsing baht and satang DIRECTLY from the string.
   * This avoids floating-point arithmetic (e.g., (128540.25 - 128540) * 100 = 24.9999...).
   * The balance string is guaranteed to be "^-?\d+\.\d{2}$" from the API.
   */
  private buildAriaLabelFromString(value: string): string {
    const dotIndex = value.indexOf('.');
    if (dotIndex === -1) {
      // No decimal part — treat as whole baht
      const baht = parseInt(value, 10);
      return `${baht.toLocaleString('th-TH')} บาท`;
    }

    const bahtPart = parseInt(value.substring(0, dotIndex), 10);
    const satangPart = parseInt(value.substring(dotIndex + 1).padEnd(2, '0').substring(0, 2), 10);
    const bahtFormatted = bahtPart.toLocaleString('th-TH');

    return satangPart > 0
      ? `${bahtFormatted} บาท ${satangPart} สตางค์`
      : `${bahtFormatted} บาท`;
  }
}
