/**
 * balance-comparison-routing.module.ts
 * Lazy routing for balance-comparison feature.
 * Source: task-plan §Step 5.
 *
 * Route: /balance-dashboard → DashboardPageComponent (lazy)
 * Guard: AuthGuard (reused from money-transfer)
 * Lazy chunk: balance-dashboard.chunk.js via standalone component
 */

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { authGuard } from '../../guards/auth.guard';

const routes: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./containers/dashboard-page/dashboard-page.component').then(
        (m) => m.DashboardPageComponent
      ),
    title: 'บัญชีของฉัน | My Accounts',
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class BalanceComparisonRoutingModule {}
