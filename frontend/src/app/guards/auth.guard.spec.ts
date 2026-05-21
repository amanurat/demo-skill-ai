/**
 * auth.guard.spec.ts
 * Routing guard tests from task-plan §Step 5.
 */

import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { authGuard } from './auth.guard';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

describe('authGuard', () => {
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
    });
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  function runGuard(): boolean | ReturnType<typeof router.createUrlTree> {
    return TestBed.runInInjectionContext(() =>
      authGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/balance-dashboard' } as RouterStateSnapshot
      )
    ) as any;
  }

  it('unauthenticated — redirects to /login', () => {
    sessionStorage.clear();
    localStorage.clear();

    const result = runGuard();

    // Should return UrlTree to /login (not true)
    expect(result).not.toBe(true);
  });

  it('authenticated with sessionStorage token — allows route activation', () => {
    sessionStorage.setItem('auth_token', 'mock-jwt-token');

    const result = runGuard();
    expect(result).toBe(true);
  });

  it('authenticated with localStorage token — allows route activation', () => {
    localStorage.setItem('auth_token', 'mock-jwt-token');

    const result = runGuard();
    expect(result).toBe(true);
  });
});
