import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { TraceTimeline, TraceEvent } from '../../core/models/service-group.model';

@Component({
  selector: 'app-trace-explorer',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './trace-explorer.component.html',
  styleUrl: './trace-explorer.component.scss'
})
export class TraceExplorerComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private apiService = inject(ApiService);

  traceId: string = '';
  timeline: TraceTimeline | null = null;
  loading = false;
  error: string | null = null;

  selectedEvent: TraceEvent | null = null;

  ngOnInit(): void {
    this.traceId = this.route.snapshot.paramMap.get('traceId') || '';
    if (this.traceId) {
      this.loadTimeline();
    }
  }

  loadTimeline(): void {
    this.loading = true;
    this.error = null;

    this.apiService.getTraceTimeline(this.traceId).subscribe({
      next: (timeline) => {
        this.timeline = timeline;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load trace timeline';
        this.loading = false;
        console.error('Error loading trace:', err);
      }
    });
  }

  selectEvent(event: TraceEvent): void {
    this.selectedEvent = this.selectedEvent?.id === event.id ? null : event;
  }

  formatTimestamp(ts: number): string {
    return new Date(ts).toISOString().replace('T', ' ').substring(0, 23);
  }

  formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`;
    return `${(ms / 60000).toFixed(2)}m`;
  }

  getEventOffset(event: TraceEvent): number {
    if (!this.timeline || this.timeline.durationMs === 0) return 0;
    return ((event.timestamp - this.timeline.startTime) / this.timeline.durationMs) * 100;
  }

  getLevelClass(level: string): string {
    switch (level?.toUpperCase()) {
      case 'ERROR': return 'level-error';
      case 'WARN': return 'level-warn';
      case 'INFO': return 'level-info';
      case 'DEBUG': return 'level-debug';
      default: return 'level-default';
    }
  }
}
