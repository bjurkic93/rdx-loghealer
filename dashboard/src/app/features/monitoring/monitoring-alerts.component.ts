import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AlertRule, AlertRuleCreateDto, AlertHistory, MonitoredService, Page, AlertRuleType } from '../../core/models/monitoring.model';

@Component({
  selector: 'app-monitoring-alerts',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="monitoring-alerts">
      <div class="page-header">
        <div class="breadcrumb">
          <a routerLink="/monitoring">Monitoring</a>
          <span>/</span>
          <span>Alerts</span>
        </div>
        <button class="btn btn-primary" (click)="openAddRuleModal()">Add Alert Rule</button>
      </div>

      <div class="tabs">
        <button class="tab" [class.active]="activeTab === 'rules'" (click)="activeTab = 'rules'">
          Alert Rules
        </button>
        <button class="tab" [class.active]="activeTab === 'history'" (click)="activeTab = 'history'; loadHistory()">
          Alert History
        </button>
        <button class="tab" [class.active]="activeTab === 'active'" (click)="activeTab = 'active'; loadActiveAlerts()">
          Active Alerts
          @if (activeAlerts.length > 0) {
            <span class="badge">{{ activeAlerts.length }}</span>
          }
        </button>
      </div>

      @if (activeTab === 'rules') {
        <div class="rules-section card">
          @if (loadingRules) {
            <div class="loading-container">
              <div class="loading-spinner"></div>
            </div>
          } @else {
            <table class="table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Service</th>
                  <th>Type</th>
                  <th>Threshold</th>
                  <th>Notify</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (rule of rules; track rule.id) {
                  <tr [class.inactive]="!rule.isActive">
                    <td>{{ rule.name }}</td>
                    <td>{{ rule.serviceName }}</td>
                    <td>
                      <span class="rule-type" [class]="rule.ruleType.toLowerCase()">
                        {{ formatRuleType(rule.ruleType) }}
                      </span>
                    </td>
                    <td>{{ formatThreshold(rule) }}</td>
                    <td class="email-cell">{{ rule.notifyEmails.join(', ') }}</td>
                    <td>
                      <span class="status-pill" [class.active]="rule.isActive">
                        {{ rule.isActive ? 'Active' : 'Inactive' }}
                      </span>
                    </td>
                    <td>
                      <div class="actions">
                        <button class="btn btn-secondary btn-sm" (click)="toggleRule(rule)">
                          {{ rule.isActive ? 'Disable' : 'Enable' }}
                        </button>
                        <button class="btn btn-danger btn-sm" (click)="deleteRule(rule)">Delete</button>
                      </div>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="7">
                      <div class="empty-state">
                        <h3>No alert rules configured</h3>
                        <p>Click "Add Alert Rule" to create one</p>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>
      }

      @if (activeTab === 'history') {
        <div class="history-section card">
          @if (loadingHistory) {
            <div class="loading-container">
              <div class="loading-spinner"></div>
            </div>
          } @else {
            <table class="table">
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Service</th>
                  <th>Alert</th>
                  <th>Message</th>
                  <th>Status</th>
                  <th>Resolved</th>
                </tr>
              </thead>
              <tbody>
                @for (alert of history; track alert.id) {
                  <tr [class.resolved]="alert.isResolved">
                    <td>{{ alert.triggeredAt | date:'MMM d, HH:mm' }}</td>
                    <td>{{ alert.serviceName }}</td>
                    <td>{{ alert.ruleName }}</td>
                    <td class="message-cell">{{ alert.message }}</td>
                    <td>
                      <span class="notification-status" [class.sent]="alert.notificationSent">
                        {{ alert.notificationSent ? 'Sent' : 'Pending' }}
                      </span>
                    </td>
                    <td>
                      @if (alert.isResolved) {
                        {{ alert.resolvedAt | date:'MMM d, HH:mm' }}
                      } @else {
                        <span class="unresolved">Unresolved</span>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="6">
                      <div class="empty-state">
                        <h3>No alert history</h3>
                        <p>Alerts will appear here when triggered</p>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
            
            @if (historyPage && historyPage.totalPages > 1) {
              <div class="pagination">
                <button class="btn btn-secondary btn-sm" [disabled]="currentPage === 0" (click)="loadHistory(currentPage - 1)">
                  Previous
                </button>
                <span>Page {{ currentPage + 1 }} of {{ historyPage.totalPages }}</span>
                <button class="btn btn-secondary btn-sm" [disabled]="currentPage >= historyPage.totalPages - 1" (click)="loadHistory(currentPage + 1)">
                  Next
                </button>
              </div>
            }
          }
        </div>
      }

      @if (activeTab === 'active') {
        <div class="active-alerts-section">
          @if (loadingActive) {
            <div class="loading-container card">
              <div class="loading-spinner"></div>
            </div>
          } @else if (activeAlerts.length === 0) {
            <div class="card empty-state">
              <span class="success-icon">OK</span>
              <h3>All Clear</h3>
              <p>No active alerts at this time</p>
            </div>
          } @else {
            @for (alert of activeAlerts; track alert.id) {
              <div class="active-alert-card card">
                <div class="alert-icon" [class]="alert.alertType.toLowerCase()">
                  {{ getAlertIcon(alert.alertType) }}
                </div>
                <div class="alert-content">
                  <div class="alert-header">
                    <h3>{{ alert.serviceName }}</h3>
                    <span class="alert-type-badge">{{ formatRuleType(alert.alertType) }}</span>
                  </div>
                  <p class="alert-message">{{ alert.message }}</p>
                  <div class="alert-meta">
                    <span>Rule: {{ alert.ruleName }}</span>
                    <span>Triggered: {{ alert.triggeredAt | date:'MMM d, HH:mm:ss' }}</span>
                  </div>
                </div>
              </div>
            }
          }
        </div>
      }

      @if (showModal) {
        <div class="modal-overlay" (click)="closeModal()">
          <div class="modal" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h2>Add Alert Rule</h2>
              <button class="close-btn" (click)="closeModal()">&times;</button>
            </div>
            
            <form (ngSubmit)="saveRule()">
              <div class="form-group">
                <label>Service</label>
                <select class="select" [(ngModel)]="formData.serviceId" name="serviceId" required>
                  <option [ngValue]="0">Select a service</option>
                  @for (service of services; track service.id) {
                    <option [ngValue]="service.id">{{ service.name }}</option>
                  }
                </select>
              </div>
              
              <div class="form-group">
                <label>Rule Name</label>
                <input type="text" class="input" [(ngModel)]="formData.name" name="name" required placeholder="e.g., API Downtime Alert">
              </div>
              
              <div class="form-group">
                <label>Alert Type</label>
                <select class="select" [(ngModel)]="formData.ruleType" name="ruleType" required>
                  <option value="DOWNTIME">Downtime</option>
                  <option value="SLOW_RESPONSE">Slow Response</option>
                  <option value="ERROR_RATE">Error Rate</option>
                </select>
              </div>
              
              <div class="form-group">
                <label>Threshold Value</label>
                <input type="number" class="input" [(ngModel)]="formData.thresholdValue" name="thresholdValue" required min="1">
                <small class="hint">
                  @switch (formData.ruleType) {
                    @case ('DOWNTIME') { Number of consecutive failures }
                    @case ('SLOW_RESPONSE') { Response time in milliseconds }
                    @case ('ERROR_RATE') { Error rate percentage }
                  }
                </small>
              </div>
              
              <div class="form-row">
                <div class="form-group">
                  <label>Consecutive Failures</label>
                  <input type="number" class="input" [(ngModel)]="formData.consecutiveFailures" name="consecutiveFailures" min="1">
                </div>
                
                <div class="form-group">
                  <label>Cooldown (minutes)</label>
                  <input type="number" class="input" [(ngModel)]="formData.cooldownMinutes" name="cooldownMinutes" min="1">
                </div>
              </div>
              
              <div class="form-group">
                <label>Notify Emails (comma separated)</label>
                <input type="text" class="input" [(ngModel)]="emailsInput" name="emails" required placeholder="email1@example.com, email2@example.com">
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
    .monitoring-alerts {
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
    
    .tabs {
      display: flex;
      gap: 8px;
      margin-bottom: 24px;
    }
    
    .tab {
      padding: 10px 20px;
      border-radius: 8px;
      background: var(--bg-secondary);
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s ease;
      display: flex;
      align-items: center;
      gap: 8px;
      
      &:hover { border-color: var(--primary); }
      
      &.active {
        background: var(--primary);
        border-color: var(--primary);
        color: white;
      }
      
      .badge {
        background: rgba(255,255,255,0.2);
        padding: 2px 8px;
        border-radius: 10px;
        font-size: 12px;
      }
    }
    
    .loading-container {
      display: flex;
      justify-content: center;
      padding: 48px;
    }
    
    .rule-type {
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 500;
      
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
    
    .email-cell {
      max-width: 150px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 13px;
      color: var(--text-secondary);
    }
    
    .status-pill {
      padding: 4px 12px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 500;
      background: rgba(148, 163, 184, 0.15);
      color: var(--text-secondary);
      
      &.active {
        background: rgba(34, 197, 94, 0.15);
        color: var(--success);
      }
    }
    
    .inactive { opacity: 0.6; }
    .actions { display: flex; gap: 8px; }
    
    .message-cell {
      max-width: 250px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 13px;
    }
    
    .notification-status {
      font-size: 12px;
      color: var(--text-muted);
      &.sent { color: var(--success); }
    }
    
    .unresolved {
      color: var(--warning);
      font-weight: 500;
    }
    
    .resolved { opacity: 0.7; }
    
    .pagination {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 16px;
      padding: 16px;
      border-top: 1px solid var(--border-color);
    }
    
    .active-alerts-section {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    
    .active-alert-card {
      display: flex;
      gap: 16px;
      border-left: 4px solid var(--danger);
    }
    
    .alert-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
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
    
    .alert-content { flex: 1; }
    
    .alert-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      
      h3 { font-size: 18px; font-weight: 600; }
    }
    
    .alert-type-badge {
      padding: 4px 12px;
      background: var(--bg-tertiary);
      border-radius: 4px;
      font-size: 12px;
      color: var(--text-secondary);
    }
    
    .alert-message {
      color: var(--text-secondary);
      margin-bottom: 12px;
    }
    
    .alert-meta {
      display: flex;
      gap: 24px;
      font-size: 13px;
      color: var(--text-muted);
    }
    
    .empty-state {
      padding: 48px;
      text-align: center;
      
      h3 { margin-bottom: 8px; }
      p { color: var(--text-muted); }
      
      .success-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 64px;
        height: 64px;
        background: rgba(34, 197, 94, 0.15);
        color: var(--success);
        border-radius: 50%;
        font-weight: 700;
        font-size: 24px;
        margin-bottom: 16px;
      }
    }
    
    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
    
    .hint {
      display: block;
      margin-top: 4px;
      font-size: 12px;
      color: var(--text-muted);
    }
  `]
})
export class MonitoringAlertsComponent implements OnInit {
  private apiService = inject(ApiService);
  
  activeTab: 'rules' | 'history' | 'active' = 'rules';
  
  rules: AlertRule[] = [];
  history: AlertHistory[] = [];
  activeAlerts: AlertHistory[] = [];
  services: MonitoredService[] = [];
  historyPage: Page<AlertHistory> | null = null;
  currentPage = 0;
  
  loadingRules = false;
  loadingHistory = false;
  loadingActive = false;
  
  showModal = false;
  saving = false;
  emailsInput = '';
  
  formData: AlertRuleCreateDto = {
    serviceId: 0,
    name: '',
    ruleType: 'DOWNTIME',
    thresholdValue: 1,
    consecutiveFailures: 2,
    notifyEmails: [],
    cooldownMinutes: 15
  };

  ngOnInit(): void {
    this.loadRules();
    this.loadServices();
    this.loadActiveAlerts();
  }

  loadRules(): void {
    this.loadingRules = true;
    this.apiService.getAlertRules().subscribe({
      next: (data) => {
        this.rules = data;
        this.loadingRules = false;
      },
      error: (err) => {
        console.error('Failed to load rules:', err);
        this.loadingRules = false;
      }
    });
  }

  loadServices(): void {
    this.apiService.getMonitoredServices().subscribe({
      next: (data) => this.services = data
    });
  }

  loadHistory(page = 0): void {
    this.loadingHistory = true;
    this.currentPage = page;
    this.apiService.getAlertHistory(page, 20).subscribe({
      next: (data) => {
        this.historyPage = data;
        this.history = data.content;
        this.loadingHistory = false;
      },
      error: (err) => {
        console.error('Failed to load history:', err);
        this.loadingHistory = false;
      }
    });
  }

  loadActiveAlerts(): void {
    this.loadingActive = true;
    this.apiService.getActiveAlerts().subscribe({
      next: (data) => {
        this.activeAlerts = data;
        this.loadingActive = false;
      },
      error: (err) => {
        console.error('Failed to load active alerts:', err);
        this.loadingActive = false;
      }
    });
  }

  openAddRuleModal(): void {
    this.formData = {
      serviceId: 0,
      name: '',
      ruleType: 'DOWNTIME',
      thresholdValue: 1,
      consecutiveFailures: 2,
      notifyEmails: [],
      cooldownMinutes: 15
    };
    this.emailsInput = '';
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
  }

  saveRule(): void {
    this.formData.notifyEmails = this.emailsInput.split(',').map(e => e.trim()).filter(e => e);
    
    this.saving = true;
    this.apiService.createAlertRule(this.formData).subscribe({
      next: () => {
        this.closeModal();
        this.loadRules();
        this.saving = false;
      },
      error: (err) => {
        console.error('Failed to save rule:', err);
        this.saving = false;
      }
    });
  }

  toggleRule(rule: AlertRule): void {
    this.apiService.toggleAlertRule(rule.id).subscribe({
      next: () => this.loadRules(),
      error: (err) => console.error('Failed to toggle rule:', err)
    });
  }

  deleteRule(rule: AlertRule): void {
    if (confirm(`Are you sure you want to delete "${rule.name}"?`)) {
      this.apiService.deleteAlertRule(rule.id).subscribe({
        next: () => this.loadRules(),
        error: (err) => console.error('Failed to delete rule:', err)
      });
    }
  }

  formatRuleType(type: AlertRuleType | string): string {
    switch (type) {
      case 'DOWNTIME': return 'Downtime';
      case 'SLOW_RESPONSE': return 'Slow Response';
      case 'ERROR_RATE': return 'Error Rate';
      default: return type;
    }
  }

  formatThreshold(rule: AlertRule): string {
    switch (rule.ruleType) {
      case 'DOWNTIME': return `${rule.thresholdValue} failures`;
      case 'SLOW_RESPONSE': return `${rule.thresholdValue}ms`;
      case 'ERROR_RATE': return `${rule.thresholdValue}%`;
      default: return String(rule.thresholdValue);
    }
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
