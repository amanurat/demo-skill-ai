/**
 * account-row.component.ts — AccountRowComponent
 * Presentational (dumb) component. Single account row display.
 * Source: component-specs §4, task-plan §Step 3.
 *
 * Accessibility (BR-021, BR-022):
 *   - role="listitem" + <button> for tap target
 *   - aria-label composed from rank, type, masked number, balance, last-updated
 *   - balance aria-label prevents digit-by-digit reading (AC-005-E2)
 *   - icon aria-hidden="true" (decorative)
 *
 * Hard constraints:
 *   - NEVER displays accountId
 *   - NEVER re-sorts (renders in received rank order)
 *   - balance prop is string — displayed via BalanceAmountPipe
 */

import {
  Component,
  Input,
  Output,
  EventEmitter,
  ChangeDetectionStrategy,
  OnChanges,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { AccountViewDto } from '../../models/balance-dashboard.model';
import { AccountTypeLabelPipe } from '../../pipes/account-type-label.pipe';
import { AccountTypeIconPipe } from '../../pipes/account-type-icon.pipe';
import { BalanceAmountPipe } from '../../pipes/balance-amount.pipe';
import { StaleBadgeComponent } from '../stale-badge/stale-badge.component';

@Component({
  selector: 'balance-account-row',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    AccountTypeLabelPipe,
    AccountTypeIconPipe,
    BalanceAmountPipe,
    StaleBadgeComponent,
  ],
  // R-FE-007: pipes are provided here so Angular DI can inject them.
  // This avoids `new AccountTypeLabelPipe()` / `new BalanceAmountPipe()` in component code.
  providers: [AccountTypeLabelPipe, BalanceAmountPipe],
  templateUrl: './account-row.component.html',
  styleUrls: ['./account-row.component.scss'],
})
export class AccountRowComponent implements OnChanges {
  // R-FE-007: inject pipes via DI instead of instantiating with `new` in buildAriaLabel()
  private readonly typeLabelPipe = inject(AccountTypeLabelPipe);
  private readonly amountPipe = inject(BalanceAmountPipe);

  @Input({ required: true }) account!: AccountViewDto;
  /** Total account count for "Account N of M" SR template. */
  @Input({ required: true }) totalCount!: number;
  @Output() rowSelect = new EventEmitter<AccountViewDto>();

  /** Composed aria-label per accessibility-final §3.1 template. */
  composedAriaLabel = '';

  ngOnChanges(): void {
    this.composedAriaLabel = this.buildAriaLabel();
  }

  onSelect(): void {
    this.rowSelect.emit(this.account);
  }

  private buildAriaLabel(): string {
    if (!this.account) return '';

    // R-FE-007: use injected pipe instances (not `new` — which bypasses DI and recreates Intl.NumberFormat each time)
    const typeLabel = this.typeLabelPipe.transform(this.account.accountType, 'th');
    const last4 = this.account.accountNumberMasked.slice(-4);
    const balanceResult = this.amountPipe.transform(this.account.balance);
    const timeLabel = this.buildRelativeTime(this.account.balanceAsOf);
    const staleClause = this.account.isStale ? ', อาจไม่ใช่ยอดล่าสุด' : '';

    if (this.totalCount === 1) {
      // Single account — drop rank prefix per screen-specs §6
      return `${typeLabel} ลงท้ายด้วย ${last4}, ยอดเงิน ${balanceResult.ariaLabel}, ${timeLabel}${staleClause}`;
    }

    return `บัญชีลำดับที่ ${this.account.rank} จาก ${this.totalCount}, ${typeLabel}, ลงท้ายด้วย ${last4}, ยอดเงิน ${balanceResult.ariaLabel}, ${timeLabel}${staleClause}`;
  }

  buildRelativeTime(balanceAsOf: string | null): string {
    if (!balanceAsOf) return 'ไม่ทราบเวลาอัปเดต';

    const now = Date.now();
    const then = new Date(balanceAsOf).getTime();
    const diffMs = now - then;
    const diffSec = Math.floor(diffMs / 1000);

    if (diffSec < 30) return 'อัปเดตล่าสุด';
    if (diffSec < 60) return `อัปเดตเมื่อ ${diffSec} วินาทีที่แล้ว`;

    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `อัปเดตเมื่อ ${diffMin} นาทีที่แล้ว`;

    const diffHr = Math.floor(diffMin / 60);
    if (diffHr < 24) return `อัปเดตเมื่อ ${diffHr} ชั่วโมงที่แล้ว`;

    const diffDay = Math.floor(diffHr / 24);
    if (diffDay < 7) return `อัปเดตเมื่อ ${diffDay} วันที่แล้ว`;

    // ≥7 days: absolute format
    const d = new Date(balanceAsOf);
    return `อัปเดตเมื่อ ${d.getDate()} ${this.thaiMonth(d.getMonth())} ${d.getFullYear()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')} น.`;
  }

  private thaiMonth(monthIndex: number): string {
    const months = [
      'ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.', 'พ.ค.', 'มิ.ย.',
      'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.', 'พ.ย.', 'ธ.ค.',
    ];
    return months[monthIndex] ?? '';
  }
}
