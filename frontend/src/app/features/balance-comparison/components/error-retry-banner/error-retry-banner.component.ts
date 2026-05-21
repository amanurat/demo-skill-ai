/**
 * error-retry-banner.component.ts — ErrorRetryBannerComponent
 * Full-page error state. Shown when API exhausted retries.
 * Source: component-specs §15, screen-specs §5, task-plan §Step 3.
 *
 * role="alert" + aria-live="assertive" — interrupts SR to inform user.
 * Heading receives programmatic focus on entry.
 * Security C-2: NEVER include balance values, account numbers, or service names in copy.
 * errorCode=FORBIDDEN → "Access denied" message, no retry button.
 * errorCode=SERVICE_UNAVAILABLE → retry button visible.
 */

import {
  Component,
  Input,
  Output,
  EventEmitter,
  ChangeDetectionStrategy,
  ElementRef,
  ViewChild,
  AfterViewInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import type { ErrorCode } from '../../models/balance-dashboard.model';

@Component({
  selector: 'balance-error-retry-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './error-retry-banner.component.html',
  styleUrls: ['./error-retry-banner.component.scss'],
})
export class ErrorRetryBannerComponent implements AfterViewInit {
  @Input({ required: true }) errorCode!: ErrorCode | string;
  @Input() correlationId = '';
  @Output() retry = new EventEmitter<void>();

  @ViewChild('headingRef') headingRef!: ElementRef<HTMLElement>;

  get isRetryable(): boolean {
    return this.errorCode === 'SERVICE_UNAVAILABLE';
  }

  get isForbidden(): boolean {
    return this.errorCode === 'FORBIDDEN';
  }

  get headingText(): string {
    // Security C-2: generic copy only
    if (this.isForbidden) {
      return 'ไม่สามารถเข้าถึงข้อมูลได้';
    }
    return 'เกิดข้อผิดพลาดในการโหลดข้อมูล';
  }

  get bodyText(): string {
    if (this.isForbidden) {
      return 'คำขอนี้ไม่ได้รับอนุญาต';
    }
    return 'กรุณาลองใหม่อีกครั้ง หรือกลับมาภายในไม่กี่นาที';
  }

  ngAfterViewInit(): void {
    // Programmatic focus on heading per accessibility-final §2.4
    if (this.headingRef?.nativeElement) {
      this.headingRef.nativeElement.focus();
    }
  }

  onRetry(): void {
    this.retry.emit();
  }
}
