/**
 * auth.guard.ts
 * Reused from money-transfer feature.
 * Redirects to /login on 401 (unauthenticated).
 * Source: task-plan §Step 5.
 *
 * NOTE: The actual token validation happens via HTTP interceptor (AuthInterceptor).
 * This guard provides a synchronous route protection layer based on stored auth state.
 * The HTTP 401 response from the API is handled by DashboardService (emits unauthorized state).
 */

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

/**
 * AuthGuard — functional guard.
 * Checks for presence of auth token in session storage.
 * For banking use: the HTTP interceptor is the authoritative 401/403 handler.
 * This guard prevents route activation without any session token.
 */
export const authGuard: CanActivateFn = () => {
  const router = inject(Router);

  // Check session storage for auth token
  const token = sessionStorage.getItem('auth_token') || localStorage.getItem('auth_token');

  if (!token) {
    return router.createUrlTree(['/login']);
  }

  return true;
};
