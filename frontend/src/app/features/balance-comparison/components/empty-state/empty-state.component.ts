/**
 * empty-state.component.ts — EmptyStateComponent
 * Shown when API returns 200 with accounts=[].
 * Source: component-specs §8, screen-specs §3, task-plan §Step 3.
 *
 * role="status" (informational, not urgent per LO-FI a11y §3).
 * Heading receives programmatic focus on state transition.
 */

import {
  Component,
  ChangeDetectionStrategy,
  ElementRef,
  ViewChild,
  AfterViewInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'balance-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './empty-state.component.html',
  styleUrls: ['./empty-state.component.scss'],
})
export class EmptyStateComponent implements AfterViewInit {
  @ViewChild('headingRef') headingRef!: ElementRef<HTMLElement>;

  ngAfterViewInit(): void {
    // Programmatic focus on heading when state transitions to empty
    if (this.headingRef?.nativeElement) {
      this.headingRef.nativeElement.focus();
    }
  }

  /**
   * R-FE-001: Angular event binding replaces inline onerror handler (CSP violation fix).
   * Called when the illustration image fails to load (e.g., asset not found).
   * Hides the broken image; the fallback SVG is always visible alongside it.
   */
  onImgError(event: Event): void {
    (event.target as HTMLImageElement).style.display = 'none';
  }
}
