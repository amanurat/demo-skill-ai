/**
 * login.component.ts — LoginComponent
 * Placeholder login page. Full implementation is deferred to the auth feature sprint.
 * Routes here when AuthGuard redirects unauthenticated users.
 *
 * AC coverage: AC-AUTH-01 (redirect destination).
 */

import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule],
  template: `
    <main class="login-page" role="main">
      <h1 i18n="@@login.title">เข้าสู่ระบบ | Login</h1>
      <p i18n="@@login.placeholder">กรุณาเข้าสู่ระบบเพื่อดูข้อมูลบัญชี</p>
    </main>
  `,
  styles: [`
    .login-page {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      padding: 1rem;
    }
  `],
})
export class LoginComponent {}
