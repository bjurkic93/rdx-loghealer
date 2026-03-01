import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardStats } from '../models/dashboard.model';
import { LogSearchRequest, LogSearchResponse } from '../models/log.model';
import { ExceptionGroup } from '../models/exception.model';
import { AiAnalysisResponse, AiProviders } from '../models/ai.model';
import { GitHubConnection, PullRequestResponse, GitHubRepository } from '../models/github.model';
import { ServiceGroup, ServiceGroupRequest, TraceTimeline, Project, ProjectRequest, DiscoveryRequest, DiscoveryResponse } from '../models/service-group.model';
import { CodeFixRequest, CodeFixResponse, FileChange } from '../models/codefix.model';
import {
  MonitoredService,
  ServiceCreateDto,
  HealthCheck,
  AlertRule,
  AlertRuleCreateDto,
  AlertHistory,
  DashboardSummary,
  ServiceMetrics,
  Page
} from '../models/monitoring.model';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiUrl;

  getDashboardStats(projectId?: string, timeRange = '24h'): Observable<DashboardStats> {
    let params = new HttpParams().set('timeRange', timeRange);
    if (projectId) {
      params = params.set('projectId', projectId);
    }
    return this.http.get<DashboardStats>(`${this.baseUrl}/dashboard/stats`, { params });
  }

  searchLogs(request: LogSearchRequest): Observable<LogSearchResponse> {
    let params = new HttpParams();
    
    if (request.query) params = params.set('query', request.query);
    if (request.levels?.length) {
      request.levels.forEach(level => {
        params = params.append('levels', level);
      });
    }
    if (request.projectId) params = params.set('projectId', request.projectId);
    if (request.logger) params = params.set('logger', request.logger);
    if (request.exceptionClass) params = params.set('exceptionClass', request.exceptionClass);
    if (request.environment) params = params.set('environment', request.environment);
    if (request.from) params = params.set('from', request.from);
    if (request.to) params = params.set('to', request.to);
    params = params.set('page', String(request.page ?? 0));
    params = params.set('size', String(request.size ?? 50));
    if (request.sortBy) params = params.set('sortBy', request.sortBy);
    if (request.sortOrder) params = params.set('sortOrder', request.sortOrder);

    return this.http.get<LogSearchResponse>(`${this.baseUrl}/logs/search`, { params });
  }

  getExceptions(projectId?: string, status?: string, page = 0, size = 20): Observable<ExceptionGroup[]> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));
    
    if (projectId) params = params.set('projectId', projectId);
    if (status) params = params.set('status', status);

    return this.http.get<ExceptionGroup[]>(`${this.baseUrl}/exceptions`, { params });
  }

  getException(id: string): Observable<ExceptionGroup> {
    return this.http.get<ExceptionGroup>(`${this.baseUrl}/exceptions/${id}`);
  }

  getHealth(): Observable<Record<string, string>> {
    return this.http.get<Record<string, string>>(`${this.baseUrl}/health`);
  }

  // AI Analysis endpoints
  analyzeException(exceptionGroupId: string, provider: 'openai' | 'claude' = 'claude', generateFix = true): Observable<AiAnalysisResponse> {
    const params = new HttpParams()
      .set('provider', provider)
      .set('generateFix', String(generateFix));
    return this.http.post<AiAnalysisResponse>(`${this.baseUrl}/ai/analyze/${exceptionGroupId}`, null, { params });
  }

  quickAnalyze(exceptionClass: string, message: string, stackTrace?: string): Observable<AiAnalysisResponse> {
    return this.http.post<AiAnalysisResponse>(`${this.baseUrl}/ai/quick-analyze`, {
      exceptionClass,
      message,
      stackTrace
    });
  }

  getAiProviders(): Observable<AiProviders> {
    return this.http.get<AiProviders>(`${this.baseUrl}/ai/providers`);
  }

  // GitHub Integration
  getGitHubAuthUrl(projectId: string): Observable<{ authorizationUrl: string }> {
    return this.http.get<{ authorizationUrl: string }>(`${this.baseUrl}/github/authorize/${projectId}`);
  }

  getGitHubConnection(projectId: string): Observable<GitHubConnection> {
    return this.http.get<GitHubConnection>(`${this.baseUrl}/github/connection/${projectId}`);
  }

  getGitHubStatus(): Observable<{ connected: boolean; githubUsername?: string; connectionId?: string }> {
    return this.http.get<{ connected: boolean; githubUsername?: string; connectionId?: string }>(`${this.baseUrl}/github/status`);
  }

  getGitHubRepositories(): Observable<GitHubRepository[]> {
    return this.http.get<GitHubRepository[]>(`${this.baseUrl}/github/repositories`);
  }

  connectRepository(connectionId: string, repositoryFullName: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/github/connect/${connectionId}`, { repositoryFullName });
  }

  createFixPullRequest(exceptionGroupId: string, projectId: string = 'demo-project', provider: 'openai' | 'claude' = 'claude'): Observable<PullRequestResponse> {
    const params = new HttpParams()
      .set('projectId', projectId)
      .set('provider', provider);
    return this.http.post<PullRequestResponse>(`${this.baseUrl}/github/create-pr/${exceptionGroupId}`, null, { params });
  }

  analyzeAndCreatePr(exceptionGroupId: string, projectId: string = 'demo-project', provider: 'openai' | 'claude' = 'claude'): Observable<{ analysis: AiAnalysisResponse; pullRequest: PullRequestResponse }> {
    const params = new HttpParams()
      .set('projectId', projectId)
      .set('provider', provider);
    return this.http.post<{ analysis: AiAnalysisResponse; pullRequest: PullRequestResponse }>(`${this.baseUrl}/github/analyze-and-pr/${exceptionGroupId}`, null, { params });
  }

  // Projects
  getProjects(): Observable<Project[]> {
    return this.http.get<Project[]>(`${this.baseUrl}/projects`);
  }

  getProject(id: string): Observable<Project> {
    return this.http.get<Project>(`${this.baseUrl}/projects/${id}`);
  }

  createProject(request: ProjectRequest): Observable<Project> {
    return this.http.post<Project>(`${this.baseUrl}/projects`, request);
  }

  updateProject(id: string, request: ProjectRequest): Observable<Project> {
    return this.http.put<Project>(`${this.baseUrl}/projects/${id}`, request);
  }

  deleteProject(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/projects/${id}`);
  }

  discoverProjectFromLog(request: DiscoveryRequest): Observable<DiscoveryResponse> {
    return this.http.post<DiscoveryResponse>(`${this.baseUrl}/projects/discover`, request);
  }

  discoverProjectFromPackage(packageName: string): Observable<DiscoveryResponse> {
    const params = new HttpParams().set('packageName', packageName);
    return this.http.get<DiscoveryResponse>(`${this.baseUrl}/projects/discover/package`, { params });
  }

  // Service Groups
  getServiceGroups(): Observable<ServiceGroup[]> {
    return this.http.get<ServiceGroup[]>(`${this.baseUrl}/service-groups`);
  }

  getServiceGroup(id: string): Observable<ServiceGroup> {
    return this.http.get<ServiceGroup>(`${this.baseUrl}/service-groups/${id}`);
  }

  createServiceGroup(request: ServiceGroupRequest): Observable<ServiceGroup> {
    return this.http.post<ServiceGroup>(`${this.baseUrl}/service-groups`, request);
  }

  updateServiceGroup(id: string, request: ServiceGroupRequest): Observable<ServiceGroup> {
    return this.http.put<ServiceGroup>(`${this.baseUrl}/service-groups/${id}`, request);
  }

  deleteServiceGroup(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/service-groups/${id}`);
  }

  // Traces
  getTraceTimeline(traceId: string): Observable<TraceTimeline> {
    return this.http.get<TraceTimeline>(`${this.baseUrl}/traces/${traceId}`);
  }

  getTraceTimelineForServiceGroup(traceId: string, serviceGroupId: string): Observable<TraceTimeline> {
    return this.http.get<TraceTimeline>(`${this.baseUrl}/traces/${traceId}/service-group/${serviceGroupId}`);
  }

  getRelatedTraces(exceptionGroupId: string, limit = 5): Observable<TraceTimeline[]> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<TraceTimeline[]>(`${this.baseUrl}/traces/exception/${exceptionGroupId}/related`, { params });
  }

  // Monitoring
  getMonitoringDashboard(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.baseUrl}/monitoring/dashboard/summary`);
  }

  getMonitoringServiceMetrics(serviceId: number): Observable<ServiceMetrics> {
    return this.http.get<ServiceMetrics>(`${this.baseUrl}/monitoring/dashboard/metrics/${serviceId}`);
  }

  getMonitoredServices(): Observable<MonitoredService[]> {
    return this.http.get<MonitoredService[]>(`${this.baseUrl}/monitoring/services`);
  }

  getMonitoredService(id: number): Observable<MonitoredService> {
    return this.http.get<MonitoredService>(`${this.baseUrl}/monitoring/services/${id}`);
  }

  createMonitoredService(dto: ServiceCreateDto): Observable<MonitoredService> {
    return this.http.post<MonitoredService>(`${this.baseUrl}/monitoring/services`, dto);
  }

  updateMonitoredService(id: number, dto: ServiceCreateDto): Observable<MonitoredService> {
    return this.http.put<MonitoredService>(`${this.baseUrl}/monitoring/services/${id}`, dto);
  }

  deleteMonitoredService(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/monitoring/services/${id}`);
  }

  toggleMonitoredService(id: number): Observable<MonitoredService> {
    return this.http.patch<MonitoredService>(`${this.baseUrl}/monitoring/services/${id}/toggle`, {});
  }

  triggerHealthCheck(id: number): Observable<HealthCheck> {
    return this.http.post<HealthCheck>(`${this.baseUrl}/monitoring/services/${id}/check`, {});
  }

  getHealthCheckHistory(serviceId: number, limit = 100): Observable<HealthCheck[]> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<HealthCheck[]>(`${this.baseUrl}/monitoring/services/${serviceId}/history`, { params });
  }

  getAlertRules(): Observable<AlertRule[]> {
    return this.http.get<AlertRule[]>(`${this.baseUrl}/monitoring/alerts/rules`);
  }

  getAlertRulesForService(serviceId: number): Observable<AlertRule[]> {
    return this.http.get<AlertRule[]>(`${this.baseUrl}/monitoring/alerts/rules/service/${serviceId}`);
  }

  createAlertRule(dto: AlertRuleCreateDto): Observable<AlertRule> {
    return this.http.post<AlertRule>(`${this.baseUrl}/monitoring/alerts/rules`, dto);
  }

  updateAlertRule(id: number, dto: AlertRuleCreateDto): Observable<AlertRule> {
    return this.http.put<AlertRule>(`${this.baseUrl}/monitoring/alerts/rules/${id}`, dto);
  }

  deleteAlertRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/monitoring/alerts/rules/${id}`);
  }

  toggleAlertRule(id: number): Observable<AlertRule> {
    return this.http.patch<AlertRule>(`${this.baseUrl}/monitoring/alerts/rules/${id}/toggle`, {});
  }

  getAlertHistory(page = 0, size = 20): Observable<Page<AlertHistory>> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<Page<AlertHistory>>(`${this.baseUrl}/monitoring/alerts/history`, { params });
  }

  getActiveAlerts(): Observable<AlertHistory[]> {
    return this.http.get<AlertHistory[]>(`${this.baseUrl}/monitoring/alerts/active`);
  }

  // Codex-style Code Fix
  analyzeAndFixCode(request: CodeFixRequest): Observable<CodeFixResponse> {
    return this.http.post<CodeFixResponse>(`${this.baseUrl}/codefix/analyze`, request);
  }

  continueFixConversation(conversationId: string, message: string): Observable<CodeFixResponse> {
    return this.http.post<CodeFixResponse>(`${this.baseUrl}/codefix/conversation/${conversationId}/message`, { message });
  }

  createCodeFixPullRequest(conversationId: string, changes: FileChange[]): Observable<CodeFixResponse> {
    return this.http.post<CodeFixResponse>(`${this.baseUrl}/codefix/conversation/${conversationId}/create-pr`, { changes });
  }
}
