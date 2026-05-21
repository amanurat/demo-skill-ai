/**
 * auth.guard.spec.ts
 * Routing guard tests from task-plan §Step 5.
 *
 * R-FE-003 fix: removed test case for localStorage fallback.
 * AuthGuard now delegates to AuthService (sessionStorage only — no localStorage).
 * localStorage is no longer a valid auth source for banking apps (OWASP anti-pattern).
 */

import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

describe('authGuard', () => {
  let router: Router;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [AuthService],
    });
    router = TestBed.inject(Router);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    sessionStorage.clear();
    // R-FE-003: localStorage is never used for auth — no need to clear
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
    authService.setAuthenticated(false);

    const result = runGuard();

    // Should return UrlTree to /login (not true)
    expect(result).not.toBe(true);
  });

  it('authenticated with sessionStorage token — allows route activation', () => {
    sessionStorage.setItem('auth_token', 'mock-jwt-token');
    authService.setAuthenticated(true);

    const result = runGuard();
    expect(result).toBe(true);
  });

  // R-FE-003: localStorage fallback test REMOVED.
  // Storing JWTs in localStorage is an OWASP anti-pattern for banking apps.
  // The guard now uses AuthService (sessionStorage only) — no localStorage path exists.
});
