export type ServiceStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN';
export type AlertRuleType = 'DOWNTIME' | 'SLOW_RESPONSE' | 'ERROR_RATE';

export interface MonitoredService {
  id: number;
  name: string;
  description: string;
  url: string;
  healthEndpoint: string;
  checkIntervalSeconds: number;
  timeoutMs: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  currentStatus: ServiceStatus;
  lastResponseTimeMs: number | null;
  lastCheckedAt: string | null;
  uptimePercentage: number | null;
  avgResponseTimeMs: number | null;
}

export interface ServiceCreateDto {
  name: string;
  description?: string;
  url: string;
  healthEndpoint: string;
  checkIntervalSeconds?: number;
  timeoutMs?: number;
}

export interface HealthCheck {
  id: number;
  serviceId: number;
  serviceName: string;
  status: ServiceStatus;
  responseTimeMs: number | null;
  statusCode: number | null;
  errorMessage: string | null;
  checkedAt: string;
}

export interface AlertRule {
  id: number;
  serviceId: number;
  serviceName: string;
  name: string;
  ruleType: AlertRuleType;
  thresholdValue: number;
  consecutiveFailures: number;
  notifyEmails: string[];
  cooldownMinutes: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AlertRuleCreateDto {
  serviceId: number;
  name: string;
  ruleType: AlertRuleType;
  thresholdValue: number;
  consecutiveFailures?: number;
  notifyEmails: string[];
  cooldownMinutes?: number;
}

export interface AlertHistory {
  id: number;
  ruleId: number;
  ruleName: string;
  serviceId: number;
  serviceName: string;
  alertType: AlertRuleType;
  message: string;
  triggeredAt: string;
  resolvedAt: string | null;
  notificationSent: boolean;
  notificationSentAt: string | null;
  isResolved: boolean;
}

export interface DashboardSummary {
  totalServices: number;
  servicesUp: number;
  servicesDown: number;
  servicesDegraded: number;
  activeAlerts: number;
  overallUptime: number | null;
  services: MonitoredService[];
  recentAlerts: AlertHistory[];
}

export interface TimeSeriesDataPoint {
  timestamp: string;
  value: number;
}

export interface ServiceMetrics {
  serviceId: number;
  serviceName: string;
  uptimePercentage24h: number | null;
  uptimePercentage7d: number | null;
  uptimePercentage30d: number | null;
  avgResponseTime24h: number | null;
  avgResponseTime7d: number | null;
  minResponseTime24h: number | null;
  maxResponseTime24h: number | null;
  totalChecks24h: number;
  failedChecks24h: number;
  recentChecks: HealthCheck[];
  responseTimeHistory: TimeSeriesDataPoint[];
  uptimeHistory: TimeSeriesDataPoint[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
