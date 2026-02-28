import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected title = 'LogHealer';
  
  private authService = inject(AuthService);
  private router = inject(Router);
  
  get isAuthenticated(): boolean {
    return this.authService.isAuthenticated;
  }
  
  get showSidebar(): boolean {
    const currentUrl = this.router.url;
    return !currentUrl.includes('/login') && !currentUrl.includes('/auth/callback');
  }
  
  get currentUser() {
    return this.authService.currentUser;
  }
  
  logout(): void {
    this.authService.logout();
  }
}
