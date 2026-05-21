/**
 * balance-dashboard-api.service.ts
 * Generated API client for GET /api/v1/balance-dashboard.
 * Source: docs/tech-lead/balance-comparison/openapi/balance-dashboard-service.openapi.yaml
 *
 * Step 1: API Client — verified shape matches task-plan interface contracts.
 * OTel traceparent header injected per common-libs convention.
 */

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BalanceDashboardResponse } from '../models/balance-dashboard.model';

@Injectable({ providedIn: 'root' })
export class BalanceDashboardApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/balance-dashboard';

  /**
   * GET /api/v1/balance-dashboard
   * No customerId parameter by design (IDOR prevention — derived from JWT sub by server).
   * Returns raw HttpResponse observable; callers handle error mapping.
   */
  getDashboard(): Observable<BalanceDashboardResponse> {
    const headers = new HttpHeaders({
      'Accept': 'application/json',
      'Accept-Language': 'th-TH,en;q=0.8',
    });

    return this.http.get<BalanceDashboardResponse>(this.baseUrl, { headers });
  }
}
