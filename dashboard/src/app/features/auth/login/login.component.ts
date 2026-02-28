import { Component } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <div class="login-container">
      <div class="login-card">
        <div class="logo">
          <span class="logo-icon">L</span>
          <h1>LogHealer</h1>
        </div>
        <p class="subtitle">AI-Powered Log Analysis & Error Resolution</p>
        <button class="btn btn-primary login-btn" (click)="login()">
          Sign in with RdX Account
        </button>
        <p class="note">Only SUPER_ADMIN users can access this dashboard</p>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: var(--bg-primary);
    }
    
    .login-card {
      background: var(--bg-secondary);
      border-radius: 16px;
      padding: 48px;
      text-align: center;
      max-width: 400px;
      width: 100%;
      border: 1px solid var(--border-color);
    }
    
    .logo {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      margin-bottom: 8px;
    }
    
    .logo-icon {
      width: 48px;
      height: 48px;
      background: var(--primary);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 24px;
      color: white;
    }
    
    h1 {
      font-size: 24px;
      font-weight: 700;
      color: var(--text-primary);
      margin: 0;
    }
    
    .subtitle {
      color: var(--text-secondary);
      margin-bottom: 32px;
    }
    
    .login-btn {
      width: 100%;
      padding: 14px 24px;
      font-size: 16px;
      background: var(--primary);
      color: white;
      border: none;
      border-radius: 8px;
      cursor: pointer;
      font-weight: 500;
      transition: background 0.2s;
    }
    
    .login-btn:hover {
      background: var(--primary-hover);
    }
    
    .note {
      margin-top: 24px;
      font-size: 12px;
      color: var(--text-muted);
    }
  `]
})
export class LoginComponent {
  constructor(private authService: AuthService) {}

  login(): void {
    this.authService.initiateLogin();
  }
}
