/**
 * stale-banner.component.ts — StaleBannerComponent
 * Global top-of-list staleness banner.
 * Source: component-specs §7, task-plan §Step 3.
 *
 * Shown when meta.freshness === 'snapshot' | 'stale'.
 * HIDDEN when meta.freshness === 'live'.
 * role="status" + aria-live="polite" — announced once on appearance.
 */

import {
  Component,
  Input,
  Output,
  EventEmitter,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import type { Freshness } from '../../models/balance-dashboard.model';

@Component({
  selector: 'balance-stale-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './stale-banner.component.html',
  styleUrls: ['./stale-banner.component.scss'],
})
export class StaleBannerComponent {
  /** When 'live' — component renders nothing. */
  @Input({ required: true }) freshness!: Freshness;
  @Output() retry = new EventEmitter<void>();
  @Output() dismiss = new EventEmitter<void>();

  get isVisible(): boolean {
    return this.freshness === 'snapshot' || this.freshness === 'stale';
  }

  get bannerTitle(): string {
    return this.freshness === 'stale'
      ? 'ข้อมูลอาจค้างนาน'
      : 'ยอดเงินอาจไม่เป็นปัจจุบัน';
  }

  get bannerBody(): string {
    return this.freshness === 'stale'
      ? 'กรุณารีเฟรชเพื่อรับข้อมูลล่าสุด'
      : 'ระบบใช้ข้อมูลล่าสุดที่บันทึกไว้ ลองรีเฟรชอีกครั้งในอีกสักครู่';
  }

  onRetry(): void {
    this.retry.emit();
  }

  onDismiss(): void {
    this.dismiss.emit();
  }
}
