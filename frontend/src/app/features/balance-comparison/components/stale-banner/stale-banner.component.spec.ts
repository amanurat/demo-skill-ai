/**
 * stale-banner.component.spec.ts
 * Tests from task-plan §Step 3 BannerTest.
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StaleBannerComponent } from './stale-banner.component';
import { By } from '@angular/platform-browser';

describe('StaleBannerComponent', () => {
  let fixture: ComponentFixture<StaleBannerComponent>;
  let component: StaleBannerComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StaleBannerComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(StaleBannerComponent);
    component = fixture.componentInstance;
  });

  it('freshness=snapshot — banner is visible', () => {
    component.freshness = 'snapshot';
    fixture.detectChanges();

    const banner = fixture.debugElement.query(By.css('[data-testid="stale-banner"]'));
    expect(banner).toBeTruthy();
  });

  it('freshness=stale — banner is visible', () => {
    component.freshness = 'stale';
    fixture.detectChanges();

    const banner = fixture.debugElement.query(By.css('[data-testid="stale-banner"]'));
    expect(banner).toBeTruthy();
  });

  it('freshness=live — banner is hidden (not rendered in DOM)', () => {
    component.freshness = 'live';
    fixture.detectChanges();

    const banner = fixture.debugElement.query(By.css('[data-testid="stale-banner"]'));
    expect(banner).toBeNull();
  });

  it('banner has role="status" and aria-live="polite" (accessibility-final §6)', () => {
    component.freshness = 'snapshot';
    fixture.detectChanges();

    const banner = fixture.debugElement.query(By.css('[data-testid="stale-banner"]'));
    expect(banner.nativeElement.getAttribute('role')).toBe('status');
    expect(banner.nativeElement.getAttribute('aria-live')).toBe('polite');
  });

  it('snapshot — shows correct title copy', () => {
    component.freshness = 'snapshot';
    fixture.detectChanges();

    const title = fixture.debugElement.query(By.css('.stale-banner__title'));
    expect(title.nativeElement.textContent).toContain('ยอดเงินอาจไม่เป็นปัจจุบัน');
  });

  it('stale — shows stronger urgency copy', () => {
    component.freshness = 'stale';
    fixture.detectChanges();

    const title = fixture.debugElement.query(By.css('.stale-banner__title'));
    expect(title.nativeElement.textContent).toContain('ข้อมูลอาจค้างนาน');
  });

  it('retry button emits retry event', () => {
    component.freshness = 'snapshot';
    fixture.detectChanges();

    let retryEmitted = false;
    component.retry.subscribe(() => { retryEmitted = true; });

    const retryBtn = fixture.debugElement.query(By.css('[data-testid="stale-banner-retry"]'));
    retryBtn.nativeElement.click();

    expect(retryEmitted).toBeTrue();
  });

  it('dismiss button emits dismiss event', () => {
    component.freshness = 'snapshot';
    fixture.detectChanges();

    let dismissEmitted = false;
    component.dismiss.subscribe(() => { dismissEmitted = true; });

    const dismissBtn = fixture.debugElement.query(By.css('[data-testid="stale-banner-dismiss"]'));
    dismissBtn.nativeElement.click();

    expect(dismissEmitted).toBeTrue();
  });

  it('dismiss button has aria-label="ปิดข้อความ"', () => {
    component.freshness = 'snapshot';
    fixture.detectChanges();

    const dismissBtn = fixture.debugElement.query(By.css('[data-testid="stale-banner-dismiss"]'));
    expect(dismissBtn.nativeElement.getAttribute('aria-label')).toBe('ปิดข้อความ');
  });
});
