import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { ApiService } from '../../core/services/api.service';
import { DashboardStats, TopException } from '../../core/models/dashboard.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, BaseChartDirective],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private apiService = inject(ApiService);

  stats: DashboardStats | null = null;
  loading = true;
  error: string | null = null;
  selectedTimeRange = '24h';

  timeRanges = [
    { label: '1h', value: '1h' },
    { label: '6h', value: '6h' },
    { label: '24h', value: '24h' },
    { label: '7d', value: '7d' },
    { label: '30d', value: '30d' }
  ];

  pieChartData: ChartData<'doughnut'> = {
    labels: [],
    datasets: [{
      data: [],
      backgroundColor: ['#f85149', '#d29922', '#58a6ff', '#8b949e', '#3fb950']
    }]
  };

  pieChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'right',
        labels: {
          color: '#c9d1d9',
          padding: 16
        }
      }
    }
  };

  ngOnInit(): void {
    this.loadStats();
  }

  loadStats(): void {
    this.loading = true;
    this.error = null;

    this.apiService.getDashboardStats(undefined, this.selectedTimeRange).subscribe({
      next: (stats) => {
        this.stats = stats;
        this.updateChartData(stats);
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load dashboard stats';
        this.loading = false;
        console.error(err);
      }
    });
  }

  private updateChartData(stats: DashboardStats): void {
    const levelOrder = ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'];
    const colors: Record<string, string> = {
      'ERROR': '#f85149',
      'WARN': '#d29922',
      'INFO': '#58a6ff',
      'DEBUG': '#8b949e',
      'TRACE': '#6e7681'
    };

    const sortedLevels = [...stats.logsByLevel].sort((a, b) => 
      levelOrder.indexOf(a.level) - levelOrder.indexOf(b.level)
    );

    this.pieChartData = {
      labels: sortedLevels.map(l => l.level),
      datasets: [{
        data: sortedLevels.map(l => l.count),
        backgroundColor: sortedLevels.map(l => colors[l.level] || '#8b949e')
      }]
    };
  }

  onTimeRangeChange(range: string): void {
    this.selectedTimeRange = range;
    this.loadStats();
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
