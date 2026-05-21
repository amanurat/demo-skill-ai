/**
 * account-type-icon.pipe.ts
 * Maps AccountType to Heroicons v2 outline icon name.
 * Source: task-plan §Step 2 + implementation-notes §8.4 (icon mapping locked).
 *
 * SAVINGS      → banknotes
 * CURRENT      → credit-card
 * FIXED_DEPOSIT → lock-closed
 */

import { Pipe, PipeTransform } from '@angular/core';
import { AccountType } from '../models/balance-dashboard.model';

const ICON_MAP: Record<AccountType, string> = {
  SAVINGS: 'banknotes',
  CURRENT: 'credit-card',
  FIXED_DEPOSIT: 'lock-closed',
};

@Pipe({ name: 'accountTypeIcon', standalone: true, pure: true })
export class AccountTypeIconPipe implements PipeTransform {
  transform(type: AccountType): string {
    return ICON_MAP[type] ?? 'banknotes';
  }
}
