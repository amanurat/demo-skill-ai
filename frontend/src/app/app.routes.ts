/**
 * app.routes.ts — Application-level routing configuration.
 * Registers lazy route for balance-comparison feature.
 * Source: task-plan §Step 5.
 *
 * Routes:
 *   /balance-dashboard → lazy-loaded DashboardPageComponent (with AuthGuard)
 *   /login → LoginComponent (placeholder — from shared auth module)
 *   /  → redirect to /balance-dashboard (default for v1)
 */

import { Routes } from '@angular/router';

export const APP_ROUTES: Routes = [
  {
    path: '',
    redirectTo: 'balance-dashboard',
    pathMatch: 'full',
  },
  {
    path: 'balance-dashboard',
    loadChildren: () =>
      import('./features/balance-comparison/balance-comparison.module').then(
        (m) => m.BalanceComparisonModule
      ),
    title: 'บัญชีของฉัน | My Accounts',
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(
        (m) => m.LoginComponent
      ),
    title: 'เข้าสู่ระบบ | Login',
  },
  {
    path: '**',
    redirectTo: 'balance-dashboard',
  },
];
