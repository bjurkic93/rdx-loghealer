import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { ServiceMetrics, MonitoredService } from '../../core/models/monitoring.model';
import { Subject, takeUntil, interval } from 'rxjs';

@Component({
  selector: 'app-monitoring-service-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="service-detail">
      <div class="page-header">
        <div class="breadcrumb">
          <a routerLink="/monitoring">Monitoring</a>
          <span>/</span>
          <a routerLink="/monitoring/services">Services</a>
          <span>/</span>
          <span>{{ service?.name }}</span>
        </div>
        <button class="btn btn-secondary" (click)="triggerCheck()" [disabled]="checking">
          {{ checking ? 'Checking...' : 'Run Health Check' }}
        </button>
      </div>

      @if (loading) {
        <div class="loading-container">
          <div class="loading-spinner"></div>
        </div>
      } @else if (service && metrics) {
        <div class="service-header card">
          <div class="service-info">
            <h1>{{ service.name }}</h1>
            <p class="service-url">{{ service.url }}{{ service.healthEndpoint }}</p>
          </div>
          <span class="status-badge" [class]="service.currentStatus.toLowerCase()">
            <span class="dot"></span>
            {{ service.currentStatus }}
          </span>
        </div>

        <div class="stats-grid">
          <div class="stat-card card">
            <span class="stat-label">Uptime (24h)</span>
            <span class="stat-value" [class.good]="metrics.uptimePercentage24h && metrics.uptimePercentage24h >= 99">
              {{ metrics.uptimePercentage24h !== null ? (metrics.uptimePercentage24h | number:'1.2-2') + '%' : 'N/A' }}
            </span>
          </div>
          <div class="stat-card card">
            <span class="stat-label">Uptime (7d)</span>
            <span class="stat-value">
              {{ metrics.uptimePercentage7d !== null ? (metrics.uptimePercentage7d | number:'1.2-2') + '%' : 'N/A' }}
            </span>
          </div>
          <div class="stat-card card">
            <span class="stat-label">Avg Response (24h)</span>
            <span class="stat-value">
              {{ metrics.avgResponseTime24h !== null ? (metrics.avgResponseTime24h | number:'1.0-0') + 'ms' : 'N/A' }}
            </span>
          </div>
          <div class="stat-card card">
            <span class="stat-label">Total Checks (24h)</span>
            <span class="stat-value">{{ metrics.totalChecks24h }}</span>
          </div>
        </div>

        <div class="history-section card">
          <h3>Recent Health Checks</h3>
          <table class="table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Status</th>
                <th>Response Time</th>
                <th>HTTP Status</th>
                <th>Error</th>
              </tr>
            </thead>
            <tbody>
              @for (check of metrics.recentChecks; track check.id) {
                <tr>
                  <td>{{ check.checkedAt | date:'MMM d, HH:mm:ss' }}</td>
                  <td>
                    <span class="status-badge" [class]="check.status.toLowerCase()">
                      <span class="dot"></span>
                      {{ check.status }}
                    </span>
                  </td>
                  <td>{{ check.responseTimeMs !== null ? check.responseTimeMs + 'ms' : '-' }}</td>
                  <td>{{ check.statusCode || '-' }}</td>
                  <td class="error-cell">{{ check.errorMessage || '-' }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="empty-cell">No health checks recorded yet</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .service-detail {
      padding: 24px;
    }
    
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
    }
    
    .breadcrumb {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      
      a {
        color: var(--text-secondary);
        text-decoration: none;
        &:hover { color: var(--primary); }
      }
      
      span:last-child {
        color: var(--text-primary);
        font-weight: 500;
      }
    }
    
    .loading-container {
      display: flex;
      justify-content: center;
      padding: 64px;
    }
    
    .service-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      
      h1 {
        font-size: 24px;
        font-weight: 700;
        margin-bottom: 4px;
      }
    }
    
    .service-url {
      color: var(--text-secondary);
      font-size: 14px;
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
    
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;
      margin-bottom: 24px;
    }
    
    .stat-card {
      text-align: center;
    }
    
    .stat-label {
      display: block;
      font-size: 13px;
      color: var(--text-secondary);
      margin-bottom: 8px;
    }
    
    .stat-value {
      font-size: 28px;
      font-weight: 700;
      
      &.good { color: var(--success); }
    }
    
    .history-section {
      h3 {
        font-size: 16px;
        font-weight: 600;
        margin-bottom: 16px;
      }
    }
    
    .error-cell {
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: var(--text-muted);
      font-size: 13px;
    }
    
    .empty-cell {
      text-align: center;
      padding: 32px;
      color: var(--text-muted);
    }
    
    @media (max-width: 1024px) {
      .stats-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }
  `]
})
export class MonitoringServiceDetailComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private apiService = inject(ApiService);
  private destroy$ = new Subject<void>();
  
  service: MonitoredService | null = null;
  metrics: ServiceMetrics | null = null;
  loading = false;
  checking = false;
  private serviceId!: number;

  ngOnInit(): void {
    this.serviceId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadData();
    
    interval(30000)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadData());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadData(): void {
    this.loading = !this.service;
    
    this.apiService.getMonitoredService(this.serviceId).subscribe({
      next: (data) => {
        this.service = data;
      }
    });
    
    this.apiService.getMonitoringServiceMetrics(this.serviceId).subscribe({
      next: (data) => {
        this.metrics = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load metrics:', err);
        this.loading = false;
      }
    });
  }

  triggerCheck(): void {
    this.checking = true;
    this.apiService.triggerHealthCheck(this.serviceId).subscribe({
      next: () => {
        this.checking = false;
        this.loadData();
      },
      error: (err) => {
        console.error('Failed to trigger check:', err);
        this.checking = false;
      }
    });
  }
}
