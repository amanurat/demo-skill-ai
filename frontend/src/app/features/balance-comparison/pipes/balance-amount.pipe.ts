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
  /** Visible display string e.g. "฿128,540.25" */
  display: string;
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
      return { display: '—', ariaLabel: 'ไม่ทราบยอดเงิน' };
    }

    const numericValue = parseFloat(value);

    if (isNaN(numericValue)) {
      return { display: '—', ariaLabel: 'ไม่ทราบยอดเงิน' };
    }

    const display = this.formatter.format(numericValue);
    const ariaLabel = this.buildAriaLabel(numericValue);

    return { display, ariaLabel };
  }

  private buildAriaLabel(value: number): string {
    const baht = Math.floor(value);
    const satang = Math.round((value - baht) * 100);
    const bahtFormatted = baht.toLocaleString('th-TH');

    return satang > 0
      ? `${bahtFormatted} บาท ${satang} สตางค์`
      : `${bahtFormatted} บาท`;
  }
}
