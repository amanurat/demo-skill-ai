/**
 * auth.guard.ts
 * Reused from money-transfer feature.
 * Redirects to /login on 401 (unauthenticated).
 * Source: task-plan §Step 5.
 *
 * NOTE: The actual token validation happens via HTTP interceptor (AuthInterceptor).
 * This guard provides a synchronous route protection layer based on stored auth state.
 * The HTTP 401 response from the API is handled by DashboardService (emits unauthorized state).
 *
 * R-FE-003 fix: removed localStorage fallback (OWASP anti-pattern for banking).
 * Auth state is now delegated to AuthService which uses sessionStorage only.
 */

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * AuthGuard — functional guard.
 * Delegates auth state check to AuthService (sessionStorage only — no localStorage).
 * For banking use: the HTTP interceptor is the authoritative 401/403 handler.
 * This guard prevents route activation without any session token.
 */
export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  const authService = inject(AuthService);

  // R-FE-003: use AuthService — sessionStorage only, never localStorage
  if (!authService.isAuthenticatedSnapshot()) {
    return router.createUrlTree(['/login']);
  }

  return true;
};
