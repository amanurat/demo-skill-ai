/**
 * auth.service.ts — AuthService
 * Centralized authentication state management.
 *
 * R-FE-003 fix: removes JWT storage from localStorage (OWASP anti-pattern for banking).
 * Auth state is held in a Signal; the HTTP interceptor updates it on 401 responses.
 * Token storage uses sessionStorage ONLY (clears when tab closes) — never localStorage.
 *
 * Source: task-plan §Step 5, security review R-FE-003.
 */

import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { toObservable } from '@angular/core/rxjs-interop';

@Injectable({ providedIn: 'root' })
export class AuthService {
  /**
   * Signal-backed auth state.
   * True when a valid session token exists in sessionStorage.
   */
  private readonly _authenticated = signal<boolean>(
    !!sessionStorage.getItem('auth_token')
  );

  /**
   * Observable of the current authentication state.
   * AuthGuard subscribes to this to protect routes.
   */
  isAuthenticated(): Observable<boolean> {
    return toObservable(this._authenticated);
  }

  /**
   * Returns the current authentication state synchronously.
   * Used by the functional AuthGuard.
   */
  isAuthenticatedSnapshot(): boolean {
    return this._authenticated();
  }

  /**
   * Sets the authenticated state (called by HTTP interceptor or login flow).
   * Token storage uses sessionStorage only — never localStorage (R-FE-003).
   */
  setAuthenticated(value: boolean): void {
    this._authenticated.set(value);
  }

  /**
   * Clears authentication state and sessionStorage token.
   * Called on logout or on 401 response.
   */
  clearAuth(): void {
    sessionStorage.removeItem('auth_token');
    this._authenticated.set(false);
  }
}
