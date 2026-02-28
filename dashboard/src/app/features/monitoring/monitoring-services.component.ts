import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { MonitoredService, ServiceCreateDto } from '../../core/models/monitoring.model';

@Component({
  selector: 'app-monitoring-services',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="monitoring-services">
      <div class="page-header">
        <div class="breadcrumb">
          <a routerLink="/monitoring">Monitoring</a>
          <span>/</span>
          <span>Services</span>
        </div>
        <button class="btn btn-primary" (click)="openAddModal()">Add Service</button>
      </div>

      @if (loading) {
        <div class="loading-container">
          <div class="loading-spinner"></div>
        </div>
      } @else {
        <div class="services-table card">
          <table class="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>URL</th>
                <th>Status</th>
                <th>Response Time</th>
                <th>Uptime (24h)</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (service of services; track service.id) {
                <tr>
                  <td>
                    <a [routerLink]="['/monitoring/services', service.id]" class="service-name">
                      {{ service.name }}
                    </a>
                  </td>
                  <td class="url-cell">{{ service.url }}</td>
                  <td>
                    <span class="status-badge" [class]="service.currentStatus.toLowerCase()">
                      <span class="dot"></span>
                      {{ service.currentStatus }}
                    </span>
                  </td>
                  <td>
                    {{ service.lastResponseTimeMs !== null ? service.lastResponseTimeMs + 'ms' : '-' }}
                  </td>
                  <td>
                    @if (service.uptimePercentage !== null) {
                      <span [class.good]="service.uptimePercentage >= 99" 
                            [class.warning]="service.uptimePercentage >= 95 && service.uptimePercentage < 99" 
                            [class.bad]="service.uptimePercentage < 95">
                        {{ service.uptimePercentage | number:'1.1-1' }}%
                      </span>
                    } @else {
                      -
                    }
                  </td>
                  <td>
                    <div class="actions">
                      <button class="btn btn-secondary btn-sm" (click)="triggerCheck(service)">Check</button>
                      <button class="btn btn-secondary btn-sm" (click)="toggleService(service)">
                        {{ service.isActive ? 'Disable' : 'Enable' }}
                      </button>
                      <button class="btn btn-danger btn-sm" (click)="deleteService(service)">Delete</button>
                    </div>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="6">
                    <div class="empty-state">
                      <h3>No services configured</h3>
                      <p>Click "Add Service" to start monitoring</p>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      @if (showModal) {
        <div class="modal-overlay" (click)="closeModal()">
          <div class="modal" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h2>{{ editingService ? 'Edit Service' : 'Add Service' }}</h2>
              <button class="close-btn" (click)="closeModal()">&times;</button>
            </div>
            
            <form (ngSubmit)="saveService()">
              <div class="form-group">
                <label>Name</label>
                <input type="text" class="input" [(ngModel)]="formData.name" name="name" required>
              </div>
              
              <div class="form-group">
                <label>Description</label>
                <input type="text" class="input" [(ngModel)]="formData.description" name="description">
              </div>
              
              <div class="form-group">
                <label>URL</label>
                <input type="url" class="input" [(ngModel)]="formData.url" name="url" required placeholder="https://example.com">
              </div>
              
              <div class="form-group">
                <label>Health Endpoint</label>
                <input type="text" class="input" [(ngModel)]="formData.healthEndpoint" name="healthEndpoint" required placeholder="/actuator/health">
              </div>
              
              <div class="form-row">
                <div class="form-group">
                  <label>Check Interval (seconds)</label>
                  <input type="number" class="input" [(ngModel)]="formData.checkIntervalSeconds" name="checkIntervalSeconds" min="10">
                </div>
                
                <div class="form-group">
                  <label>Timeout (ms)</label>
                  <input type="number" class="input" [(ngModel)]="formData.timeoutMs" name="timeoutMs" min="1000">
                </div>
              </div>
              
              <div class="modal-footer">
                <button type="button" class="btn btn-secondary" (click)="closeModal()">Cancel</button>
                <button type="submit" class="btn btn-primary" [disabled]="saving">
                  {{ saving ? 'Saving...' : 'Save' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .monitoring-services {
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
        font-weight: 600;
        font-size: 20px;
      }
    }
    
    .loading-container {
      display: flex;
      justify-content: center;
      padding: 64px;
    }
    
    .services-table {
      padding: 0;
      overflow: hidden;
    }
    
    .service-name {
      color: var(--primary);
      text-decoration: none;
      font-weight: 500;
      &:hover { text-decoration: underline; }
    }
    
    .url-cell {
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: var(--text-secondary);
      font-size: 13px;
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
    
    .good { color: var(--success); }
    .warning { color: var(--warning); }
    .bad { color: var(--danger); }
    
    .actions {
      display: flex;
      gap: 8px;
    }
    
    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
    
    .empty-state {
      padding: 48px;
      text-align: center;
      
      h3 { margin-bottom: 8px; }
      p { color: var(--text-muted); }
    }
  `]
})
export class MonitoringServicesComponent implements OnInit {
  private apiService = inject(ApiService);
  
  services: MonitoredService[] = [];
  loading = false;
  showModal = false;
  saving = false;
  editingService: MonitoredService | null = null;
  
  formData: ServiceCreateDto = {
    name: '',
    description: '',
    url: '',
    healthEndpoint: '/actuator/health',
    checkIntervalSeconds: 30,
    timeoutMs: 5000
  };

  ngOnInit(): void {
    this.loadServices();
  }

  loadServices(): void {
    this.loading = true;
    this.apiService.getMonitoredServices().subscribe({
      next: (data) => {
        this.services = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load services:', err);
        this.loading = false;
      }
    });
  }

  openAddModal(): void {
    this.editingService = null;
    this.formData = {
      name: '',
      description: '',
      url: '',
      healthEndpoint: '/actuator/health',
      checkIntervalSeconds: 30,
      timeoutMs: 5000
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.editingService = null;
  }

  saveService(): void {
    this.saving = true;
    
    const request = this.editingService
      ? this.apiService.updateMonitoredService(this.editingService.id, this.formData)
      : this.apiService.createMonitoredService(this.formData);
    
    request.subscribe({
      next: () => {
        this.closeModal();
        this.loadServices();
        this.saving = false;
      },
      error: (err) => {
        console.error('Failed to save service:', err);
        this.saving = false;
      }
    });
  }

  triggerCheck(service: MonitoredService): void {
    this.apiService.triggerHealthCheck(service.id).subscribe({
      next: () => this.loadServices(),
      error: (err) => console.error('Failed to trigger check:', err)
    });
  }

  toggleService(service: MonitoredService): void {
    this.apiService.toggleMonitoredService(service.id).subscribe({
      next: () => this.loadServices(),
      error: (err) => console.error('Failed to toggle service:', err)
    });
  }

  deleteService(service: MonitoredService): void {
    if (confirm(`Are you sure you want to delete "${service.name}"?`)) {
      this.apiService.deleteMonitoredService(service.id).subscribe({
        next: () => this.loadServices(),
        error: (err) => console.error('Failed to delete service:', err)
      });
    }
  }
}
