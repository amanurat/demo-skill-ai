/**
 * account-type-label.pipe.ts
 * Maps AccountType enum to Thai/English label.
 * Source: task-plan §Step 2 AccountTypeLabelPipe.
 */

import { Pipe, PipeTransform } from '@angular/core';
import { AccountType } from '../models/balance-dashboard.model';

type Locale = 'th' | 'en';

const LABELS: Record<AccountType, Record<Locale, string>> = {
  SAVINGS: {
    th: 'บัญชีออมทรัพย์',
    en: 'Savings Account',
  },
  CURRENT: {
    th: 'บัญชีกระแสรายวัน',
    en: 'Current Account',
  },
  FIXED_DEPOSIT: {
    th: 'บัญชีเงินฝากประจำ',
    en: 'Fixed Deposit Account',
  },
};

@Pipe({ name: 'accountTypeLabel', standalone: true, pure: true })
export class AccountTypeLabelPipe implements PipeTransform {
  transform(type: AccountType, locale: Locale = 'th'): string {
    return LABELS[type]?.[locale] ?? type;
  }
}
