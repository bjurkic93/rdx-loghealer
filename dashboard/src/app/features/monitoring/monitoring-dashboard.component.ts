import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { DashboardSummary, MonitoredService, AlertHistory, ServiceStatus } from '../../core/models/monitoring.model';
import { interval, Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-monitoring-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="monitoring-dashboard">
      <div class="page-header">
        <h1>Service Monitoring</h1>
        <div class="header-actions">
          <span class="last-updated" *ngIf="lastUpdated">
            Last updated: {{ lastUpdated | date:'HH:mm:ss' }}
          </span>
          <button class="btn btn-secondary" (click)="refresh()">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8"/>
              <path d="M21 3v5h-5"/>
            </svg>
            Refresh
          </button>
          <a routerLink="/monitoring/services" class="btn btn-primary">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
              <line x1="8" y1="21" x2="16" y2="21"/>
              <line x1="12" y1="17" x2="12" y2="21"/>
            </svg>
            Manage Services
          </a>
        </div>
      </div>

      @if (loading && !summary) {
        <div class="loading-container">
          <div class="loading-spinner"></div>
        </div>
      } @else if (summary) {
        <div class="stats-grid">
          <div class="stat-card info">
            <div class="stat-icon">
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
                <line x1="8" y1="21" x2="16" y2="21"/>
                <line x1="12" y1="17" x2="12" y2="21"/>
              </svg>
            </div>
            <div class="stat-content">
              <span class="stat-label">Total Services</span>
              <span class="stat-value">{{ summary.totalServices }}</span>
            </div>
          </div>
          <div class="stat-card success">
            <div class="stat-icon">
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
            </div>
            <div class="stat-content">
              <span class="stat-label">Services Up</span>
              <span class="stat-value">{{ summary.servicesUp }}</span>
            </div>
          </div>
          <div class="stat-card danger">
            <div class="stat-icon">
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="15" y1="9" x2="9" y2="15"/>
                <line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
            </div>
            <div class="stat-content">
              <span class="stat-label">Services Down</span>
              <span class="stat-value">{{ summary.servicesDown }}</span>
            </div>
          </div>
          <div class="stat-card" [class.warning]="summary.activeAlerts > 0" [class.success]="summary.activeAlerts === 0">
            <div class="stat-icon">
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </div>
            <div class="stat-content">
              <span class="stat-label">Active Alerts</span>
              <span class="stat-value">{{ summary.activeAlerts }}</span>
            </div>
          </div>
        </div>

        <div class="uptime-banner" *ngIf="summary.overallUptime !== null">
          <span class="uptime-label">Overall Uptime (24h)</span>
          <span class="uptime-value" 
                [class.good]="summary.overallUptime >= 99" 
                [class.warning]="summary.overallUptime >= 95 && summary.overallUptime < 99" 
                [class.bad]="summary.overallUptime < 95">
            {{ summary.overallUptime | number:'1.2-2' }}%
          </span>
        </div>

        <div class="dashboard-grid">
          <section class="services-section">
            <div class="section-header">
              <h2>Services</h2>
              <a routerLink="/monitoring/services" class="btn btn-secondary btn-sm">View All</a>
            </div>
            <div class="services-list">
              @for (service of summary.services; track service.id) {
                <div class="service-card" [routerLink]="['/monitoring/services', service.id]">
                  <div class="service-header">
                    <h3>{{ service.name }}</h3>
                    <span class="status-badge" [class]="service.currentStatus.toLowerCase()">
                      <span class="dot"></span>
                      {{ service.currentStatus }}
                    </span>
                  </div>
                  <p class="service-url">{{ service.url }}</p>
                  <div class="service-stats">
                    <div class="stat">
                      <span class="stat-label">Response Time</span>
                      <span class="stat-value">
                        {{ service.lastResponseTimeMs !== null ? service.lastResponseTimeMs + 'ms' : 'N/A' }}
                      </span>
                    </div>
                    <div class="stat">
                      <span class="stat-label">Uptime (24h)</span>
                      <span class="stat-value" 
                            [class.good]="service.uptimePercentage && service.uptimePercentage >= 99"
                            [class.warning]="service.uptimePercentage && service.uptimePercentage >= 95 && service.uptimePercentage < 99"
                            [class.bad]="service.uptimePercentage && service.uptimePercentage < 95">
                        {{ service.uptimePercentage !== null ? (service.uptimePercentage | number:'1.1-1') + '%' : 'N/A' }}
                      </span>
                    </div>
                  </div>
                </div>
              } @empty {
                <div class="empty-state">
                  <h3>No services configured</h3>
                  <p>Add services to start monitoring</p>
                  <a routerLink="/monitoring/services" class="btn btn-primary">Add Service</a>
                </div>
              }
            </div>
          </section>

          <section class="alerts-section">
            <div class="section-header">
              <h2>Active Alerts</h2>
              <a routerLink="/monitoring/alerts" class="btn btn-secondary btn-sm">View All</a>
            </div>
            <div class="alerts-list">
              @if (summary.recentAlerts.length === 0) {
                <div class="empty-state success">
                  <span class="success-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                      <polyline points="22 4 12 14.01 9 11.01"/>
                    </svg>
                  </span>
                  <h3>All Clear</h3>
                  <p>No active alerts</p>
                </div>
              } @else {
                @for (alert of summary.recentAlerts; track alert.id) {
                  <div class="alert-item" [class.resolved]="alert.isResolved">
                    <div class="alert-icon" [class]="alert.alertType.toLowerCase()">
                      <svg *ngIf="alert.alertType === 'DOWNTIME'" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
                      </svg>
                      <svg *ngIf="alert.alertType === 'SLOW_RESPONSE'" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                      </svg>
                      <svg *ngIf="alert.alertType === 'ERROR_RATE'" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                      </svg>
                    </div>
                    <div class="alert-content">
                      <div class="alert-header">
                        <span class="alert-service">{{ alert.serviceName }}</span>
                        <span class="alert-type">{{ alert.alertType }}</span>
                      </div>
                      <p class="alert-message">{{ alert.message }}</p>
                      <span class="alert-time">{{ alert.triggeredAt | date:'MMM d, HH:mm' }}</span>
                    </div>
                  </div>
                }
              }
            </div>
          </section>
        </div>
      }
    </div>
  `,
  styles: [`
    .monitoring-dashboard {
      padding: 24px;
      max-width: 1600px;
      margin: 0 auto;
    }
    
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 32px;
      
      h1 {
        font-size: 28px;
        font-weight: 700;
        color: var(--text-primary);
      }
    }
    
    .header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    
    .last-updated {
      color: var(--text-muted);
      font-size: 13px;
      padding: 8px 12px;
      background: var(--bg-tertiary);
      border-radius: 6px;
    }
    
    .loading-container {
      display: flex;
      justify-content: center;
      padding: 64px;
    }
    
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 20px;
      margin-bottom: 28px;
    }
    
    .stat-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 24px;
      background: var(--bg-secondary);
      border-radius: 16px;
      border: 1px solid var(--border-color);
      transition: all 0.2s ease;
      
      &:hover {
        border-color: var(--border-hover);
        transform: translateY(-2px);
      }
    }
    
    .stat-icon {
      width: 52px;
      height: 52px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    
    .stat-card.info .stat-icon {
      background: rgba(99, 102, 241, 0.15);
      color: var(--primary);
    }
    
    .stat-card.success .stat-icon {
      background: rgba(34, 197, 94, 0.15);
      color: var(--success);
    }
    
    .stat-card.danger .stat-icon {
      background: rgba(239, 68, 68, 0.15);
      color: var(--danger);
    }
    
    .stat-card.warning .stat-icon {
      background: rgba(245, 158, 11, 0.15);
      color: var(--warning);
    }
    
    .stat-content {
      display: flex;
      flex-direction: column;
    }
    
    .stat-label {
      font-size: 13px;
      color: var(--text-secondary);
      margin-bottom: 6px;
      font-weight: 500;
    }
    
    .stat-value {
      font-size: 32px;
      font-weight: 700;
      color: var(--text-primary);
    }
    
    .uptime-banner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 24px 28px;
      background: linear-gradient(135deg, var(--bg-secondary) 0%, var(--bg-tertiary) 100%);
      border-radius: 16px;
      border: 1px solid var(--border-color);
      margin-bottom: 28px;
    }
    
    .uptime-label {
      font-size: 16px;
      font-weight: 600;
      color: var(--text-secondary);
    }
    
    .uptime-value {
      font-size: 36px;
      font-weight: 700;
      
      &.good { color: var(--success); }
      &.warning { color: var(--warning); }
      &.bad { color: var(--danger); }
    }
    
    .dashboard-grid {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 24px;
    }
    
    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
      
      h2 {
        font-size: 18px;
        font-weight: 600;
        color: var(--text-primary);
      }
    }
    
    .services-list {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    
    .service-card {
      padding: 20px 24px;
      background: var(--bg-secondary);
      border-radius: 14px;
      border: 1px solid var(--border-color);
      cursor: pointer;
      transition: all 0.2s ease;
      
      &:hover {
        border-color: var(--primary);
        transform: translateY(-2px);
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
      }
    }
    
    .service-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
      
      h3 {
        font-size: 16px;
        font-weight: 600;
        color: var(--text-primary);
      }
    }
    
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 5px 12px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      
      .dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
      }
      
      &.up {
        background: rgba(34, 197, 94, 0.15);
        color: var(--success);
        .dot { background: var(--success); }
      }
      
      &.down {
        background: rgba(239, 68, 68, 0.15);
        color: var(--danger);
        .dot { background: var(--danger); }
      }
      
      &.degraded {
        background: rgba(245, 158, 11, 0.15);
        color: var(--warning);
        .dot { background: var(--warning); }
      }
      
      &.unknown {
        background: rgba(148, 163, 184, 0.15);
        color: var(--text-secondary);
        .dot { background: var(--text-secondary); }
      }
    }
    
    .service-url {
      font-size: 13px;
      color: var(--text-muted);
      margin-bottom: 16px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    
    .service-stats {
      display: flex;
      gap: 32px;
    }
    
    .service-stats .stat {
      display: flex;
      flex-direction: column;
    }
    
    .service-stats .stat-label {
      font-size: 12px;
      color: var(--text-muted);
      margin-bottom: 4px;
      font-weight: 500;
    }
    
    .service-stats .stat-value {
      font-size: 16px;
      font-weight: 600;
      color: var(--text-primary);
      
      &.good { color: var(--success); }
      &.warning { color: var(--warning); }
      &.bad { color: var(--danger); }
    }
    
    .alerts-list {
      background: var(--bg-secondary);
      border-radius: 14px;
      border: 1px solid var(--border-color);
      overflow: hidden;
    }
    
    .empty-state {
      padding: 48px 24px;
      text-align: center;
      
      &.success .success-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 56px;
        height: 56px;
        background: rgba(34, 197, 94, 0.15);
        color: var(--success);
        border-radius: 50%;
        margin-bottom: 16px;
      }
      
      h3 {
        font-size: 16px;
        font-weight: 600;
        margin-bottom: 6px;
        color: var(--text-primary);
      }
      
      p {
        font-size: 14px;
        color: var(--text-muted);
      }
    }
    
    .alert-item {
      display: flex;
      gap: 14px;
      padding: 16px 20px;
      border-bottom: 1px solid var(--border-color);
      transition: background 0.15s ease;
      
      &:hover {
        background: rgba(99, 102, 241, 0.05);
      }
      
      &:last-child {
        border-bottom: none;
      }
      
      &.resolved {
        opacity: 0.6;
      }
    }
    
    .alert-icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      
      &.downtime {
        background: rgba(239, 68, 68, 0.15);
        color: var(--danger);
      }
      
      &.slow_response {
        background: rgba(245, 158, 11, 0.15);
        color: var(--warning);
      }
      
      &.error_rate {
        background: rgba(239, 68, 68, 0.15);
        color: var(--danger);
      }
    }
    
    .alert-content {
      flex: 1;
      min-width: 0;
    }
    
    .alert-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6px;
    }
    
    .alert-service {
      font-weight: 600;
      font-size: 14px;
      color: var(--text-primary);
    }
    
    .alert-type {
      font-size: 10px;
      padding: 3px 8px;
      background: var(--bg-tertiary);
      border-radius: 4px;
      color: var(--text-secondary);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    
    .alert-message {
      font-size: 13px;
      color: var(--text-secondary);
      margin-bottom: 6px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    
    .alert-time {
      font-size: 12px;
      color: var(--text-muted);
    }
    
    @media (max-width: 1200px) {
      .stats-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }
    
    @media (max-width: 1024px) {
      .dashboard-grid {
        grid-template-columns: 1fr;
      }
    }
    
    @media (max-width: 768px) {
      .stats-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class MonitoringDashboardComponent implements OnInit, OnDestroy {
  private apiService = inject(ApiService);
  private destroy$ = new Subject<void>();
  
  summary: DashboardSummary | null = null;
  loading = false;
  lastUpdated: Date | null = null;

  ngOnInit(): void {
    this.loadDashboard();
    
    interval(30000)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadDashboard());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadDashboard(): void {
    this.loading = true;
    this.apiService.getMonitoringDashboard().subscribe({
      next: (data) => {
        this.summary = data;
        this.lastUpdated = new Date();
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load monitoring dashboard:', err);
        this.loading = false;
      }
    });
  }

  refresh(): void {
    this.loadDashboard();
  }

  getAlertIcon(alertType: string): string {
    switch (alertType) {
      case 'DOWNTIME': return '!';
      case 'SLOW_RESPONSE': return '~';
      case 'ERROR_RATE': return '%';
      default: return '*';
    }
  }
}
