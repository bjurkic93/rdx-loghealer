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
          <button class="btn btn-secondary" (click)="refresh()">Refresh</button>
          <a routerLink="/monitoring/services" class="btn btn-primary">Manage Services</a>
        </div>
      </div>

      @if (loading && !summary) {
        <div class="loading-container">
          <div class="loading-spinner"></div>
        </div>
      } @else if (summary) {
        <div class="stats-grid">
          <div class="stat-card" [class.info]="true">
            <div class="stat-icon">S</div>
            <div class="stat-content">
              <span class="stat-label">Total Services</span>
              <span class="stat-value">{{ summary.totalServices }}</span>
            </div>
          </div>
          <div class="stat-card" [class.success]="true">
            <div class="stat-icon">+</div>
            <div class="stat-content">
              <span class="stat-label">Services Up</span>
              <span class="stat-value">{{ summary.servicesUp }}</span>
            </div>
          </div>
          <div class="stat-card" [class.danger]="true">
            <div class="stat-icon">-</div>
            <div class="stat-content">
              <span class="stat-label">Services Down</span>
              <span class="stat-value">{{ summary.servicesDown }}</span>
            </div>
          </div>
          <div class="stat-card" [class.warning]="summary.activeAlerts > 0" [class.success]="summary.activeAlerts === 0">
            <div class="stat-icon">!</div>
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
                  <span class="success-icon">OK</span>
                  <h3>All Clear</h3>
                  <p>No active alerts</p>
                </div>
              } @else {
                @for (alert of summary.recentAlerts; track alert.id) {
                  <div class="alert-item" [class.resolved]="alert.isResolved">
                    <div class="alert-icon" [class]="alert.alertType.toLowerCase()">
                      {{ getAlertIcon(alert.alertType) }}
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
    }
    
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      
      h1 {
        font-size: 28px;
        font-weight: 700;
      }
    }
    
    .header-actions {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    
    .last-updated {
      color: var(--text-muted);
      font-size: 13px;
    }
    
    .loading-container {
      display: flex;
      justify-content: center;
      padding: 64px;
    }
    
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;
      margin-bottom: 24px;
    }
    
    .stat-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px;
      background: var(--bg-secondary);
      border-radius: 12px;
      border: 1px solid var(--border-color);
    }
    
    .stat-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      font-weight: 700;
    }
    
    .stat-card.info .stat-icon {
      background: rgba(59, 130, 246, 0.15);
      color: var(--info);
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
      margin-bottom: 4px;
    }
    
    .stat-value {
      font-size: 28px;
      font-weight: 700;
    }
    
    .uptime-banner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 20px 24px;
      background: linear-gradient(135deg, var(--bg-secondary) 0%, var(--bg-tertiary) 100%);
      border-radius: 12px;
      border: 1px solid var(--border-color);
      margin-bottom: 24px;
    }
    
    .uptime-label {
      font-size: 16px;
      font-weight: 500;
      color: var(--text-secondary);
    }
    
    .uptime-value {
      font-size: 32px;
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
      margin-bottom: 16px;
      
      h2 {
        font-size: 18px;
        font-weight: 600;
      }
    }
    
    .services-list {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    
    .service-card {
      padding: 20px;
      background: var(--bg-secondary);
      border-radius: 12px;
      border: 1px solid var(--border-color);
      cursor: pointer;
      transition: all 0.2s ease;
      
      &:hover {
        border-color: var(--primary);
        transform: translateY(-2px);
      }
    }
    
    .service-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      
      h3 {
        font-size: 16px;
        font-weight: 600;
      }
    }
    
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 500;
      
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
      gap: 24px;
    }
    
    .service-stats .stat {
      display: flex;
      flex-direction: column;
    }
    
    .service-stats .stat-label {
      font-size: 12px;
      color: var(--text-muted);
      margin-bottom: 4px;
    }
    
    .service-stats .stat-value {
      font-size: 16px;
      font-weight: 600;
      
      &.good { color: var(--success); }
      &.warning { color: var(--warning); }
      &.bad { color: var(--danger); }
    }
    
    .alerts-list {
      background: var(--bg-secondary);
      border-radius: 12px;
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
        width: 48px;
        height: 48px;
        background: rgba(34, 197, 94, 0.15);
        color: var(--success);
        border-radius: 50%;
        font-weight: 700;
        margin-bottom: 12px;
      }
      
      h3 {
        font-size: 16px;
        margin-bottom: 4px;
      }
      
      p {
        font-size: 13px;
        color: var(--text-muted);
      }
    }
    
    .alert-item {
      display: flex;
      gap: 12px;
      padding: 16px;
      border-bottom: 1px solid var(--border-color);
      
      &:last-child {
        border-bottom: none;
      }
      
      &.resolved {
        opacity: 0.6;
      }
    }
    
    .alert-icon {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
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
      margin-bottom: 4px;
    }
    
    .alert-service {
      font-weight: 600;
      font-size: 14px;
    }
    
    .alert-type {
      font-size: 11px;
      padding: 2px 8px;
      background: var(--bg-tertiary);
      border-radius: 4px;
      color: var(--text-secondary);
    }
    
    .alert-message {
      font-size: 13px;
      color: var(--text-secondary);
      margin-bottom: 4px;
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
