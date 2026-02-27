import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { GitHubConnection } from '../../core/models/github.model';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit {
  private apiService = inject(ApiService);

  // GitHub
  githubConnection: GitHubConnection | null = null;
  githubLoading = false;
  githubError: string | null = null;

  // Project settings
  projectId = 'demo-project';

  ngOnInit(): void {
    this.loadGitHubConnection();
  }

  loadGitHubConnection(): void {
    this.githubLoading = true;
    this.githubError = null;

    this.apiService.getGitHubConnection(this.projectId).subscribe({
      next: (connection) => {
        this.githubConnection = connection;
        this.githubLoading = false;
      },
      error: (err) => {
        this.githubError = 'Failed to load GitHub connection status';
        this.githubLoading = false;
        console.error('GitHub connection error:', err);
      }
    });
  }

  connectGitHub(): void {
    this.githubLoading = true;
    this.apiService.getGitHubAuthUrl(this.projectId).subscribe({
      next: (response) => {
        window.location.href = response.authorizationUrl;
      },
      error: (err) => {
        this.githubError = 'Failed to start GitHub authorization';
        this.githubLoading = false;
        console.error('GitHub auth error:', err);
      }
    });
  }

  disconnectGitHub(): void {
    // TODO: Implement disconnect endpoint
    this.githubConnection = { connected: false };
  }
}
