/**
 * dashboard.service.ts — BalanceDashboardFacade
 * Orchestrates API calls with retry logic (1s/2s/4s x3 on 503 only).
 * Emits DashboardState transitions; handles 401/403/503/empty responses.
 *
 * Retry strategy from task-plan §Step 2:
 *   503 → exponential backoff 1s / 2s / 4s, max 3 attempts
 *   401 → emit unauthorized (existing interceptor handles redirect)
 *   403 → emit forbidden (no auto-retry)
 *   Empty accounts[] → emit empty state
 */

import { Injectable, inject } from '@angular/core';
import { Observable, throwError, timer, of } from 'rxjs';
import { catchError, map, mergeMap, retryWhen } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { BalanceDashboardApiService } from '../api/balance-dashboard-api.service';
import {
  DashboardState,
  BalanceDashboardResponse,
  ErrorCode,
} from '../models/balance-dashboard.model';

const RETRY_DELAYS_MS = [1000, 2000, 4000];

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly api = inject(BalanceDashboardApiService);

  /**
   * Returns Observable<DashboardState> stream.
   * Subscribers receive loading → loaded/empty/error/unauthorized/forbidden transitions.
   */
  loadDashboard(): Observable<DashboardState> {
    return this.api.getDashboard().pipe(
      // Apply 503-only exponential retry BEFORE mapping
      retryWhen((errors) =>
        errors.pipe(
          mergeMap((err: HttpErrorResponse, attempt) => {
            if (err.status !== 503 || attempt >= 3) {
              return throwError(() => err);
            }
            return timer(RETRY_DELAYS_MS[attempt] ?? 4000);
          })
        )
      ),
      map((response: BalanceDashboardResponse): DashboardState => {
        if (response.accounts.length === 0) {
          return { kind: 'empty', meta: response.meta };
        }
        return { kind: 'loaded', data: response };
      }),
      catchError((err: HttpErrorResponse): Observable<DashboardState> => {
        if (err.status === 401) {
          return of({ kind: 'unauthorized' } as DashboardState);
        }
        if (err.status === 403) {
          const correlationId =
            (err.error as { correlationId?: string })?.correlationId ?? '';
          return of({
            kind: 'forbidden',
            correlationId,
          } as DashboardState);
        }
        // 503 after all retries, or any other error
        const problemDetail = err.error as { code?: ErrorCode; correlationId?: string };
        const code: ErrorCode =
          problemDetail?.code ?? 'SERVICE_UNAVAILABLE';
        const correlationId = problemDetail?.correlationId ?? '';
        return of({
          kind: 'error',
          code,
          correlationId,
          retryable: code === 'SERVICE_UNAVAILABLE',
        } as DashboardState);
      })
    );
  }
}
