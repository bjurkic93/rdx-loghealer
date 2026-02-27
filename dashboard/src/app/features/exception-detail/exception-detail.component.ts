import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { ExceptionGroup } from '../../core/models/exception.model';
import { AiAnalysisResponse } from '../../core/models/ai.model';

@Component({
  selector: 'app-exception-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './exception-detail.component.html',
  styleUrl: './exception-detail.component.scss'
})
export class ExceptionDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private apiService = inject(ApiService);

  exception: ExceptionGroup | null = null;
  loading = true;
  error: string | null = null;
  
  // AI Analysis
  aiAnalysis: AiAnalysisResponse | null = null;
  aiLoading = false;
  aiError: string | null = null;
  selectedProvider: 'openai' | 'claude' = 'claude';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadException(id);
    }
  }

  loadException(id: string): void {
    this.loading = true;
    this.error = null;

    this.apiService.getException(id).subscribe({
      next: (exception) => {
        this.exception = exception;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load exception details';
        this.loading = false;
        console.error(err);
      }
    });
  }

  getStatusClass(status: string): string {
    return status?.toLowerCase().replace('_', '-') || 'new';
  }

  formatTimestamp(ts: string): string {
    if (!ts) return '-';
    try {
      return new Date(ts).toLocaleString();
    } catch {
      return ts;
    }
  }

  // AI Analysis methods
  analyzeWithAi(provider: 'openai' | 'claude' = 'claude'): void {
    if (!this.exception?.id) return;
    
    this.selectedProvider = provider;
    this.aiLoading = true;
    this.aiError = null;

    this.apiService.analyzeException(this.exception.id, provider, true).subscribe({
      next: (response) => {
        this.aiAnalysis = response;
        this.aiLoading = false;
      },
      error: (err) => {
        this.aiError = 'AI analysis failed. Please check API keys configuration.';
        this.aiLoading = false;
        console.error('AI analysis error:', err);
      }
    });
  }

  getSeverityClass(severity: string): string {
    return `severity-${severity?.toLowerCase() || 'medium'}`;
  }

  getConfidencePercent(confidence: number): string {
    return `${Math.round((confidence || 0) * 100)}%`;
  }

  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text).then(() => {
      // Could show a toast notification here
    });
  }
}
