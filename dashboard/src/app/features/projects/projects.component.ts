import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Project, ProjectRequest } from '../../core/models/service-group.model';
import { GitHubRepository } from '../../core/models/github.model';

@Component({
  selector: 'app-projects',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './projects.component.html',
  styleUrl: './projects.component.scss'
})
export class ProjectsComponent implements OnInit {
  private apiService = inject(ApiService);

  projects: Project[] = [];
  loading = true;
  error: string | null = null;
  successMessage: string | null = null;

  // View mode
  viewMode: 'list' | 'create' | 'edit' = 'list';
  editingProject: Project | null = null;

  // Form
  formData: ProjectRequest = {
    name: '',
    projectKey: '',
    repoUrl: '',
    gitProvider: 'GITHUB',
    defaultBranch: 'main',
    packagePrefix: ''
  };

  // GitHub repo search
  githubRepos: GitHubRepository[] = [];
  repoSearchQuery = '';
  loadingRepos = false;
  showRepoDropdown = false;

  ngOnInit(): void {
    this.loadProjects();
    this.loadGitHubRepos();
  }

  loadProjects(): void {
    this.loading = true;
    this.error = null;

    this.apiService.getProjects().subscribe({
      next: (projects) => {
        this.projects = projects;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load projects';
        this.loading = false;
        console.error(err);
      }
    });
  }

  loadGitHubRepos(): void {
    this.loadingRepos = true;
    this.apiService.getGitHubRepositories().subscribe({
      next: (repos: GitHubRepository[]) => {
        this.githubRepos = repos;
        this.loadingRepos = false;
      },
      error: () => {
        this.loadingRepos = false;
      }
    });
  }

  get filteredRepos(): GitHubRepository[] {
    if (!this.repoSearchQuery) return this.githubRepos.slice(0, 10);
    const query = this.repoSearchQuery.toLowerCase();
    return this.githubRepos
      .filter(r => r.name.toLowerCase().includes(query) || r.fullName.toLowerCase().includes(query))
      .slice(0, 10);
  }

  selectRepo(repo: GitHubRepository): void {
    this.formData.repoUrl = repo.htmlUrl;
    this.formData.defaultBranch = repo.defaultBranch;
    // Auto-fill project key from repo name (this is the identifier used in logs)
    if (!this.formData.projectKey) {
      this.formData.projectKey = repo.name;
    }
    // Auto-fill display name if empty
    if (!this.formData.name) {
      this.formData.name = this.formatRepoNameAsDisplayName(repo.name);
    }
    this.showRepoDropdown = false;
    this.repoSearchQuery = repo.fullName;
  }

  formatRepoNameAsDisplayName(repoName: string): string {
    // Convert "rdxs-backend" to "Rdxs Backend"
    return repoName
      .split(/[-_]/)
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }

  startCreate(): void {
    this.viewMode = 'create';
    this.editingProject = null;
    this.resetForm();
  }

  startEdit(project: Project): void {
    this.viewMode = 'edit';
    this.editingProject = project;
    this.formData = {
      name: project.name,
      projectKey: project.projectKey,
      repoUrl: project.repoUrl || '',
      gitProvider: project.gitProvider || 'GITHUB',
      defaultBranch: project.defaultBranch || 'main',
      packagePrefix: project.packagePrefix || ''
    };
    this.repoSearchQuery = project.repoUrl || '';
  }

  backToList(): void {
    this.viewMode = 'list';
    this.editingProject = null;
    this.resetForm();
  }

  resetForm(): void {
    this.formData = {
      name: '',
      projectKey: '',
      repoUrl: '',
      gitProvider: 'GITHUB',
      defaultBranch: 'main',
      packagePrefix: ''
    };
    this.repoSearchQuery = '';
  }

  saveProject(): void {
    if (!this.formData.name || !this.formData.projectKey) return;

    this.loading = true;

    if (this.viewMode === 'edit' && this.editingProject) {
      this.apiService.updateProject(this.editingProject.id, this.formData).subscribe({
        next: () => {
          this.successMessage = 'Project updated successfully';
          this.loadProjects();
          this.backToList();
          setTimeout(() => this.successMessage = null, 3000);
        },
        error: (err) => {
          this.error = 'Failed to update project';
          this.loading = false;
          console.error(err);
        }
      });
    } else {
      this.apiService.createProject(this.formData).subscribe({
        next: () => {
          this.successMessage = 'Project created successfully';
          this.loadProjects();
          this.backToList();
          setTimeout(() => this.successMessage = null, 3000);
        },
        error: (err) => {
          this.error = 'Failed to create project';
          this.loading = false;
          console.error(err);
        }
      });
    }
  }

  deleteProject(project: Project): void {
    if (!confirm(`Are you sure you want to delete "${project.name}"?`)) return;

    this.apiService.deleteProject(project.id).subscribe({
      next: () => {
        this.successMessage = 'Project deleted successfully';
        this.loadProjects();
        setTimeout(() => this.successMessage = null, 3000);
      },
      error: (err) => {
        this.error = 'Failed to delete project';
        console.error(err);
      }
    });
  }

  copyApiKey(apiKey: string): void {
    navigator.clipboard.writeText(apiKey);
    this.successMessage = 'API Key copied to clipboard';
    setTimeout(() => this.successMessage = null, 2000);
  }

  onRepoInputFocus(): void {
    this.showRepoDropdown = true;
  }

  onRepoInputBlur(): void {
    setTimeout(() => this.showRepoDropdown = false, 200);
  }
}
