import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { ExceptionGroup, ExceptionStatus } from '../../core/models/exception.model';

@Component({
  selector: 'app-exceptions',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './exceptions.component.html',
  styleUrl: './exceptions.component.scss'
})
export class ExceptionsComponent implements OnInit {
  private apiService = inject(ApiService);

  exceptions: ExceptionGroup[] = [];
  loading = true;
  error: string | null = null;
  currentPage = 0;
  pageSize = 20;

  selectedStatus: ExceptionStatus | null = null;
  statuses: ExceptionStatus[] = ['NEW', 'IN_PROGRESS', 'RESOLVED', 'IGNORED'];

  ngOnInit(): void {
    this.loadExceptions();
  }

  loadExceptions(): void {
    this.loading = true;
    this.error = null;

    this.apiService.getExceptions(
      undefined, 
      this.selectedStatus || undefined, 
      this.currentPage, 
      this.pageSize
    ).subscribe({
      next: (exceptions) => {
        this.exceptions = exceptions;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load exceptions';
        this.loading = false;
        console.error(err);
      }
    });
  }

  filterByStatus(status: ExceptionStatus | null): void {
    this.selectedStatus = status;
    this.currentPage = 0;
    this.loadExceptions();
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

  getTimeAgo(ts: string | number | null | undefined): string {
    if (!ts) return '-';
    try {
      let date: Date;
      
      if (typeof ts === 'number') {
        date = new Date(ts);
      } else if (typeof ts === 'string') {
        // Try parsing as number first (epoch millis as string)
        const numValue = Number(ts);
        if (!isNaN(numValue) && numValue > 1000000000000) {
          date = new Date(numValue);
        } else {
          // Parse as ISO string
          date = new Date(ts);
        }
      } else {
        return '-';
      }
      
      if (isNaN(date.getTime())) return '-';
      
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffMins = Math.floor(diffMs / 60000);
      const diffHours = Math.floor(diffMs / 3600000);
      const diffDays = Math.floor(diffMs / 86400000);

      if (diffMins < 1) return 'just now';
      if (diffMins < 60) return `${diffMins}m ago`;
      if (diffHours < 24) return `${diffHours}h ago`;
      if (diffDays < 30) return `${diffDays}d ago`;
      return date.toLocaleDateString();
    } catch {
      return '-';
    }
  }
}
