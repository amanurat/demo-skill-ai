/**
 * stale-badge.component.ts — StaleBadgeComponent
 * Per-row stale indicator pill.
 * Source: component-specs §6, task-plan §Step 3.
 *
 * Non-interactive text badge — no role, no click handler.
 * Visible text content is sufficient for accessibility (part of parent row aria-label).
 */

import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'balance-stale-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './stale-badge.component.html',
  styleUrls: ['./stale-badge.component.scss'],
})
export class StaleBadgeComponent {
  /** ISO-8601 timestamp for tooltip absolute display. */
  @Input({ required: true }) lastUpdatedAbsolute!: string;

  get absoluteFormatted(): string {
    if (!this.lastUpdatedAbsolute) return '';
    const d = new Date(this.lastUpdatedAbsolute);
    const months = [
      'ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.', 'พ.ค.', 'มิ.ย.',
      'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.', 'พ.ย.', 'ธ.ค.',
    ];
    const dd = d.getDate();
    const mmm = months[d.getMonth()];
    const yyyy = d.getFullYear();
    const hh = String(d.getHours()).padStart(2, '0');
    const min = String(d.getMinutes()).padStart(2, '0');
    return `${dd} ${mmm} ${yyyy} ${hh}:${min} น.`;
  }
}
