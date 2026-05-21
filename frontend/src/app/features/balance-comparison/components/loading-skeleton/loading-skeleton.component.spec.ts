/**
 * loading-skeleton.component.spec.ts
 * Tests from task-plan §Step 3 LoadingSkeletonTest.
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoadingSkeletonComponent } from './loading-skeleton.component';
import { By } from '@angular/platform-browser';

describe('LoadingSkeletonComponent', () => {
  let fixture: ComponentFixture<LoadingSkeletonComponent>;
  let component: LoadingSkeletonComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoadingSkeletonComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(LoadingSkeletonComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders 3 skeleton rows by default', () => {
    const rows = fixture.debugElement.queryAll(By.css('[data-testid="skeleton-row"]'));
    expect(rows.length).toBe(3);
  });

  it('renders custom rowCount skeleton rows', () => {
    component.rowCount = 5;
    fixture.detectChanges();

    const rows = fixture.debugElement.queryAll(By.css('[data-testid="skeleton-row"]'));
    expect(rows.length).toBe(5);
  });

  it('list has aria-busy="true"', () => {
    const list = fixture.debugElement.query(By.css('[data-testid="loading-skeleton"]'));
    expect(list.nativeElement.getAttribute('aria-busy')).toBe('true');
  });

  it('skeleton rows are aria-hidden="true" (decorative — SR uses list aria-busy)', () => {
    const rows = fixture.debugElement.queryAll(By.css('[data-testid="skeleton-row"]'));
    rows.forEach(row => {
      expect(row.nativeElement.getAttribute('aria-hidden')).toBe('true');
    });
  });
});
