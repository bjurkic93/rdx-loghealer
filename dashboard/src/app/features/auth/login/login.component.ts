import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <div class="login-container">
      <div class="login-card">
        <div class="logo">
          <img src="assets/logo.png" alt="LogHealer" class="logo-img">
          <h1>LogHealer</h1>
        </div>
        <p class="subtitle">Redirecting to login...</p>
        <div class="spinner"></div>
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
    
    .logo-img {
      width: 48px;
      height: 48px;
      border-radius: 12px;
    }
    
    h1 {
      font-size: 24px;
      font-weight: 700;
      color: var(--text-primary);
      margin: 0;
    }
    
    .subtitle {
      color: var(--text-secondary);
      margin-bottom: 24px;
    }
    
    .spinner {
      width: 32px;
      height: 32px;
      border: 3px solid var(--border-color);
      border-top-color: var(--primary);
      border-radius: 50%;
      margin: 0 auto;
      animation: spin 1s linear infinite;
    }
    
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
export class LoginComponent implements OnInit {
  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.authService.initiateLogin();
  }
}
