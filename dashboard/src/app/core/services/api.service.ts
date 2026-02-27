import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardStats } from '../models/dashboard.model';
import { LogSearchRequest, LogSearchResponse } from '../models/log.model';
import { ExceptionGroup } from '../models/exception.model';
import { AiAnalysisResponse, AiProviders } from '../models/ai.model';

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
}
