import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { ExceptionGroup } from '../../core/models/exception.model';

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
}
