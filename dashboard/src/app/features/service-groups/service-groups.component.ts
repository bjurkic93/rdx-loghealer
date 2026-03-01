import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { ServiceGroup, ServiceGroupRequest, DatabaseConnectionRequest, Project, ProjectRequest } from '../../core/models/service-group.model';
import { GitHubRepository } from '../../core/models/github.model';

type ViewMode = 'list' | 'create' | 'edit';
type CreateStep = 'basics' | 'projects' | 'databases' | 'review';

@Component({
  selector: 'app-service-groups',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './service-groups.component.html',
  styleUrl: './service-groups.component.scss'
})
export class ServiceGroupsComponent implements OnInit {
  private apiService = inject(ApiService);

  serviceGroups: ServiceGroup[] = [];
  projects: Project[] = [];
  githubRepos: GitHubRepository[] = [];
  githubConnected = false;
  githubUsername = '';
  loadingRepos = false;
  loading = false;
  error: string | null = null;
  successMessage: string | null = null;

  // View mode instead of modals
  viewMode: ViewMode = 'list';
  editingGroup: ServiceGroup | null = null;

  // Stepper
  currentStep: CreateStep = 'basics';
  steps: CreateStep[] = ['basics', 'projects', 'databases', 'review'];

  // Project creation inline
  showAddProject = false;
  repoSearchTerm = '';
  selectedRepo: GitHubRepository | null = null;

  formData: ServiceGroupRequest = {
    name: '',
    description: '',
    projectIds: [],
    databases: []
  };

  newDatabase: DatabaseConnectionRequest = {
    name: '',
    dbType: 'POSTGRESQL'
  };

  newProject: ProjectRequest = {
    name: '',
    projectKey: '',
    repoUrl: '',
    gitProvider: 'GITHUB',
    defaultBranch: 'main',
    packagePrefix: ''
  };

  dbTypes = ['POSTGRESQL', 'MYSQL', 'MARIADB', 'MONGODB', 'REDIS', 'ELASTICSEARCH'];
  gitProviders = ['GITHUB', 'GITLAB', 'BITBUCKET'];

  ngOnInit(): void {
    this.loadServiceGroups();
    this.loadProjects();
    this.checkGitHubStatus();
  }

  loadServiceGroups(): void {
    this.loading = true;
    this.error = null;

    this.apiService.getServiceGroups().subscribe({
      next: (groups) => {
        this.serviceGroups = groups;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load service groups';
        this.loading = false;
        console.error('Error loading service groups:', err);
      }
    });
  }

  loadProjects(): void {
    this.apiService.getProjects().subscribe({
      next: (projects) => {
        this.projects = projects;
      },
      error: (err) => {
        console.error('Error loading projects:', err);
      }
    });
  }

  checkGitHubStatus(): void {
    this.apiService.getGitHubStatus().subscribe({
      next: (status) => {
        this.githubConnected = status.connected;
        this.githubUsername = status.githubUsername || '';
        if (status.connected) {
          this.loadGitHubRepos();
        }
      },
      error: (err) => {
        console.error('Error checking GitHub status:', err);
      }
    });
  }

  loadGitHubRepos(): void {
    this.loadingRepos = true;
    this.apiService.getGitHubRepositories().subscribe({
      next: (repos) => {
        this.githubRepos = repos;
        this.loadingRepos = false;
      },
      error: (err) => {
        console.error('Error loading GitHub repos:', err);
        this.loadingRepos = false;
      }
    });
  }

  get filteredRepos(): GitHubRepository[] {
    if (!this.repoSearchTerm) return this.githubRepos.slice(0, 10);
    const term = this.repoSearchTerm.toLowerCase();
    return this.githubRepos
      .filter(r => r.fullName.toLowerCase().includes(term) || r.name.toLowerCase().includes(term))
      .slice(0, 10);
  }

  selectRepo(repo: GitHubRepository): void {
    this.selectedRepo = repo;
    this.newProject.projectKey = repo.name;
    this.newProject.name = this.formatRepoNameAsDisplayName(repo.name);
    this.newProject.repoUrl = `https://github.com/${repo.fullName}`;
    this.newProject.defaultBranch = repo.defaultBranch;
    this.repoSearchTerm = repo.fullName;
  }

  // Navigation
  startCreate(): void {
    this.formData = {
      name: '',
      description: '',
      projectIds: [],
      databases: []
    };
    this.currentStep = 'basics';
    this.viewMode = 'create';
    this.editingGroup = null;
    this.error = null;
    this.successMessage = null;
  }

  startEdit(group: ServiceGroup): void {
    this.editingGroup = group;
    this.formData = {
      name: group.name,
      description: group.description,
      projectIds: group.projects.map(p => p.id),
      databases: group.databases.map(d => ({
        name: d.name,
        dbType: d.dbType,
        host: d.host,
        port: d.port,
        databaseName: d.databaseName,
        schemaName: d.schemaName
      }))
    };
    this.currentStep = 'basics';
    this.viewMode = 'edit';
    this.error = null;
    this.successMessage = null;
  }

  backToList(): void {
    this.viewMode = 'list';
    this.editingGroup = null;
    this.showAddProject = false;
    this.resetNewProject();
  }

  // Stepper navigation
  get currentStepIndex(): number {
    return this.steps.indexOf(this.currentStep);
  }

  get canGoNext(): boolean {
    switch (this.currentStep) {
      case 'basics':
        return !!this.formData.name?.trim();
      default:
        return true;
    }
  }

  nextStep(): void {
    const idx = this.currentStepIndex;
    if (idx < this.steps.length - 1) {
      this.currentStep = this.steps[idx + 1];
    }
  }

  prevStep(): void {
    const idx = this.currentStepIndex;
    if (idx > 0) {
      this.currentStep = this.steps[idx - 1];
    }
  }

  goToStep(step: CreateStep): void {
    // Only allow going to previous steps or current step
    const targetIdx = this.steps.indexOf(step);
    if (targetIdx <= this.currentStepIndex) {
      this.currentStep = step;
    }
  }

  isStepComplete(step: CreateStep): boolean {
    const stepIdx = this.steps.indexOf(step);
    return stepIdx < this.currentStepIndex;
  }

  isStepActive(step: CreateStep): boolean {
    return step === this.currentStep;
  }

  // Project management
  toggleProjectSelection(projectId: string): void {
    this.formData.projectIds = this.formData.projectIds || [];
    const index = this.formData.projectIds.indexOf(projectId);
    if (index === -1) {
      this.formData.projectIds.push(projectId);
    } else {
      this.formData.projectIds.splice(index, 1);
    }
  }

  isProjectSelected(projectId: string): boolean {
    return this.formData.projectIds?.includes(projectId) ?? false;
  }

  getProjectById(id: string): Project | undefined {
    return this.projects.find(p => p.id === id);
  }

  getSelectedProjects(): Project[] {
    return this.projects.filter(p => this.formData.projectIds?.includes(p.id));
  }

  toggleAddProject(): void {
    this.showAddProject = !this.showAddProject;
    if (!this.showAddProject) {
      this.resetNewProject();
    }
  }

  resetNewProject(): void {
    this.newProject = {
      name: '',
      projectKey: '',
      repoUrl: '',
      gitProvider: 'GITHUB',
      defaultBranch: 'main',
      packagePrefix: ''
    };
    this.selectedRepo = null;
    this.repoSearchTerm = '';
  }

  formatRepoNameAsDisplayName(repoName: string): string {
    return repoName
      .split(/[-_]/)
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }

  createProject(): void {
    if (!this.newProject.name || !this.newProject.projectKey || !this.newProject.repoUrl) return;

    this.loading = true;
    this.apiService.createProject(this.newProject).subscribe({
      next: (project) => {
        this.projects.push(project);
        this.formData.projectIds = this.formData.projectIds || [];
        this.formData.projectIds.push(project.id);
        this.showAddProject = false;
        this.resetNewProject();
        this.loading = false;
        this.successMessage = `Project "${project.name}" created and selected`;
        setTimeout(() => this.successMessage = null, 3000);
      },
      error: (err) => {
        this.error = 'Failed to create project';
        this.loading = false;
        console.error('Error creating project:', err);
      }
    });
  }

  // Database management
  addDatabase(): void {
    if (this.newDatabase.name) {
      this.formData.databases = this.formData.databases || [];
      this.formData.databases.push({ ...this.newDatabase });
      this.newDatabase = { name: '', dbType: 'POSTGRESQL' };
    }
  }

  removeDatabase(index: number): void {
    this.formData.databases?.splice(index, 1);
  }

  // Submit
  submitForm(): void {
    if (this.editingGroup) {
      this.updateServiceGroup();
    } else {
      this.createServiceGroup();
    }
  }

  createServiceGroup(): void {
    this.loading = true;
    this.error = null;
    
    this.apiService.createServiceGroup(this.formData).subscribe({
      next: () => {
        this.loadServiceGroups();
        this.backToList();
        this.successMessage = `Service Group "${this.formData.name}" created successfully`;
        setTimeout(() => this.successMessage = null, 5000);
      },
      error: (err) => {
        this.error = 'Failed to create service group';
        this.loading = false;
        console.error('Error creating service group:', err);
      }
    });
  }

  updateServiceGroup(): void {
    if (!this.editingGroup) return;

    this.loading = true;
    this.error = null;
    
    this.apiService.updateServiceGroup(this.editingGroup.id, this.formData).subscribe({
      next: () => {
        this.loadServiceGroups();
        this.backToList();
        this.successMessage = `Service Group "${this.formData.name}" updated successfully`;
        setTimeout(() => this.successMessage = null, 5000);
      },
      error: (err) => {
        this.error = 'Failed to update service group';
        this.loading = false;
        console.error('Error updating service group:', err);
      }
    });
  }

  deleteServiceGroup(group: ServiceGroup): void {
    if (!confirm(`Are you sure you want to delete "${group.name}"?`)) return;

    this.apiService.deleteServiceGroup(group.id).subscribe({
      next: () => {
        this.loadServiceGroups();
        this.successMessage = `Service Group "${group.name}" deleted`;
        setTimeout(() => this.successMessage = null, 3000);
      },
      error: (err) => {
        this.error = 'Failed to delete service group';
        console.error('Error deleting service group:', err);
      }
    });
  }

  formatDate(dateStr: string | null | undefined): string {
    if (!dateStr) return '-';
    try {
      return new Date(dateStr).toLocaleDateString();
    } catch {
      return '-';
    }
  }
}
