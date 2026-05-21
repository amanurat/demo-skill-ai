/**
 * error-retry-banner.component.spec.ts
 * Tests from task-plan §Step 3 ErrorStateTest.
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorRetryBannerComponent } from './error-retry-banner.component';
import { By } from '@angular/platform-browser';

describe('ErrorRetryBannerComponent', () => {
  let fixture: ComponentFixture<ErrorRetryBannerComponent>;
  let component: ErrorRetryBannerComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorRetryBannerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ErrorRetryBannerComponent);
    component = fixture.componentInstance;
  });

  it('SERVICE_UNAVAILABLE — retry button is visible', () => {
    component.errorCode = 'SERVICE_UNAVAILABLE';
    component.correlationId = 'corr-123';
    fixture.detectChanges();

    const retryBtn = fixture.debugElement.query(By.css('[data-testid="error-retry-btn"]'));
    expect(retryBtn).toBeTruthy();
  });

  it('FORBIDDEN — "Access denied" message shown, NO retry button', () => {
    component.errorCode = 'FORBIDDEN';
    component.correlationId = 'corr-403';
    fixture.detectChanges();

    const retryBtn = fixture.debugElement.query(By.css('[data-testid="error-retry-btn"]'));
    expect(retryBtn).toBeNull();

    const heading = fixture.debugElement.query(By.css('[data-testid="error-heading"]'));
    expect(heading.nativeElement.textContent).toContain('ไม่สามารถเข้าถึงข้อมูลได้');
  });

  it('correlationId is rendered for support contact', () => {
    component.errorCode = 'SERVICE_UNAVAILABLE';
    component.correlationId = '7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60';
    fixture.detectChanges();

    const corrEl = fixture.debugElement.query(By.css('[data-testid="correlation-id"]'));
    expect(corrEl).toBeTruthy();
    expect(corrEl.nativeElement.textContent).toContain('7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60');
  });

  it('has role="alert" and aria-live="assertive"', () => {
    component.errorCode = 'SERVICE_UNAVAILABLE';
    component.correlationId = '';
    fixture.detectChanges();

    const container = fixture.debugElement.query(By.css('[data-testid="error-retry-banner"]'));
    expect(container.nativeElement.getAttribute('role')).toBe('alert');
    expect(container.nativeElement.getAttribute('aria-live')).toBe('assertive');
  });

  it('retry button emits retry event', () => {
    component.errorCode = 'SERVICE_UNAVAILABLE';
    component.correlationId = '';
    fixture.detectChanges();

    let retryEmitted = false;
    component.retry.subscribe(() => { retryEmitted = true; });

    const retryBtn = fixture.debugElement.query(By.css('[data-testid="error-retry-btn"]'));
    retryBtn.nativeElement.click();

    expect(retryEmitted).toBeTrue();
  });

  it('error copy never contains service names or technical details (Security C-2)', () => {
    component.errorCode = 'SERVICE_UNAVAILABLE';
    component.correlationId = 'corr-123';
    fixture.detectChanges();

    const html = fixture.debugElement.nativeElement.innerHTML;
    // Security C-2: no upstream service names in UI
    expect(html).not.toContain('AccountClient');
    expect(html).not.toContain('Redis');
    expect(html).not.toContain('503');
    expect(html).not.toContain('Service Unavailable');
  });

  it('correlation ID area is absent when correlationId is empty', () => {
    component.errorCode = 'SERVICE_UNAVAILABLE';
    component.correlationId = '';
    fixture.detectChanges();

    const corrEl = fixture.debugElement.query(By.css('[data-testid="correlation-id"]'));
    expect(corrEl).toBeNull();
  });
});
