import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { LogEntry, LogSearchRequest } from '../../core/models/log.model';

@Component({
  selector: 'app-logs',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './logs.component.html',
  styleUrl: './logs.component.scss'
})
export class LogsComponent implements OnInit {
  private apiService = inject(ApiService);
  private router = inject(Router);

  logs: LogEntry[] = [];
  loading = true;
  error: string | null = null;
  totalHits = 0;
  currentPage = 0;
  pageSize = 50;
  totalPages = 0;

  searchQuery = '';
  selectedLevels: string[] = [];
  levels = ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'];
  selectedProjectId = '';
  availableProjects: string[] = [];

  expandedLogId: string | null = null;

  ngOnInit(): void {
    this.loadProjects();
    this.loadLogs();
  }

  loadProjects(): void {
    this.apiService.getProjects().subscribe({
      next: (projects) => {
        this.availableProjects = projects.map(p => p.name);
      },
      error: () => {
        // Fallback - extract from logs later
      }
    });
  }

  loadLogs(): void {
    this.loading = true;
    this.error = null;

    const request: LogSearchRequest = {
      query: this.searchQuery || undefined,
      levels: this.selectedLevels.length > 0 ? this.selectedLevels : undefined,
      projectId: this.selectedProjectId || undefined,
      page: this.currentPage,
      size: this.pageSize,
      sortBy: 'timestamp',
      sortOrder: 'desc'
    };

    this.apiService.searchLogs(request).subscribe({
      next: (response) => {
        this.logs = response.logs;
        this.totalHits = response.totalHits;
        this.totalPages = response.totalPages;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load logs';
        this.loading = false;
        console.error(err);
      }
    });
  }

  onSearch(): void {
    this.currentPage = 0;
    this.loadLogs();
  }

  toggleLevel(level: string): void {
    const index = this.selectedLevels.indexOf(level);
    if (index > -1) {
      this.selectedLevels.splice(index, 1);
    } else {
      this.selectedLevels.push(level);
    }
    this.currentPage = 0;
    this.loadLogs();
  }

  isLevelSelected(level: string): boolean {
    return this.selectedLevels.includes(level);
  }

  toggleExpand(logId: string): void {
    this.expandedLogId = this.expandedLogId === logId ? null : logId;
  }

  isExpanded(logId: string): boolean {
    return this.expandedLogId === logId;
  }

  previousPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadLogs();
    }
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.loadLogs();
    }
  }

  goToPage(event: Event): void {
    const input = event.target as HTMLInputElement;
    const page = parseInt(input.value, 10);
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page - 1;
      this.loadLogs();
    } else {
      input.value = (this.currentPage + 1).toString();
    }
  }

  getLevelClass(level: string): string {
    return level?.toLowerCase() || 'info';
  }

  openTraceView(traceId: string, event: Event): void {
    event.stopPropagation();
    console.log('Opening trace view for:', traceId);
    this.router.navigate(['/traces', traceId]).then(
      success => console.log('Navigation success:', success),
      error => console.error('Navigation error:', error)
    );
  }

  formatTimestamp(ts: string | number | null | undefined): string {
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
      return date.toLocaleString();
    } catch {
      return '-';
    }
  }
}
