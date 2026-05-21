/**
 * loading-skeleton.component.ts — LoadingSkeletonComponent
 * Loading skeleton placeholder rows.
 * Source: component-specs §12, screen-specs §2, task-plan §Step 3.
 *
 * aria-busy="true" on list; skeleton rows are aria-hidden="true".
 * No shimmer in v1 (LO-FI commitment + OPEN-D-009 — static only).
 * rowCount default 3 — do NOT echo cached count (PII timing leak risk).
 */

import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'balance-loading-skeleton',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './loading-skeleton.component.html',
  styleUrls: ['./loading-skeleton.component.scss'],
})
export class LoadingSkeletonComponent {
  /** Fixed default 3 — do NOT echo actual account count (PII timing leak). */
  @Input() rowCount = 3;

  get rows(): number[] {
    return Array.from({ length: this.rowCount }, (_, i) => i);
  }
}
