/**
 * empty-state.component.spec.ts
 * Tests from task-plan §Step 3 EmptyStateTest.
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmptyStateComponent } from './empty-state.component';
import { By } from '@angular/platform-browser';

describe('EmptyStateComponent', () => {
  let fixture: ComponentFixture<EmptyStateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyStateComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EmptyStateComponent);
    fixture.detectChanges();
  });

  it('renders illustration and no-accounts copy (AC-001-E1)', () => {
    const heading = fixture.debugElement.query(By.css('[data-testid="empty-state-heading"]'));
    expect(heading).toBeTruthy();
    expect(heading.nativeElement.textContent).toContain('ยังไม่มีบัญชีที่ใช้งานอยู่');
  });

  it('renders subtitle explaining exclusion rule', () => {
    const subtitle = fixture.debugElement.query(By.css('[data-testid="empty-state-subtitle"]'));
    expect(subtitle).toBeTruthy();
    expect(subtitle.nativeElement.textContent).toContain('บัญชีที่ปิดหรือไม่เคลื่อนไหว');
  });

  it('has role="status" (informational, not urgent)', () => {
    const container = fixture.debugElement.query(By.css('[data-testid="empty-state"]'));
    expect(container.nativeElement.getAttribute('role')).toBe('status');
  });

  it('disabled CTA is <span aria-disabled="true"> — not a <button disabled>', () => {
    const cta = fixture.debugElement.query(By.css('[data-testid="empty-state-cta-disabled"]'));
    expect(cta).toBeTruthy();
    expect(cta.nativeElement.tagName.toLowerCase()).toBe('span');
    expect(cta.nativeElement.getAttribute('aria-disabled')).toBe('true');
    // Must NOT be a button element (would add confusing focus stop)
    expect(cta.nativeElement.tagName.toLowerCase()).not.toBe('button');
  });
});
