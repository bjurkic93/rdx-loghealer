import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { ServiceGroup, ServiceGroupRequest, DatabaseConnectionRequest, Project, ProjectRequest } from '../../core/models/service-group.model';
import { GitHubRepository } from '../../core/models/github.model';

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

  showCreateModal = false;
  showEditModal = false;
  showProjectModal = false;
  editingGroup: ServiceGroup | null = null;

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
    this.newProject.name = repo.name;
    this.newProject.repoUrl = `https://github.com/${repo.fullName}`;
    this.newProject.defaultBranch = repo.defaultBranch;
    this.repoSearchTerm = repo.fullName;
  }

  openCreateModal(): void {
    this.formData = {
      name: '',
      description: '',
      projectIds: [],
      databases: []
    };
    this.showCreateModal = true;
  }

  openEditModal(group: ServiceGroup): void {
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
    this.showEditModal = true;
  }

  closeModals(): void {
    this.showCreateModal = false;
    this.showEditModal = false;
    this.showProjectModal = false;
    this.editingGroup = null;
  }

  openProjectModal(): void {
    this.newProject = {
      name: '',
      repoUrl: '',
      gitProvider: 'GITHUB',
      defaultBranch: 'main',
      packagePrefix: ''
    };
    this.showProjectModal = true;
  }

  createProject(): void {
    if (!this.newProject.name || !this.newProject.repoUrl) return;

    this.apiService.createProject(this.newProject).subscribe({
      next: (project) => {
        this.projects.push(project);
        this.formData.projectIds = this.formData.projectIds || [];
        this.formData.projectIds.push(project.id);
        this.showProjectModal = false;
        this.newProject = { name: '', repoUrl: '', gitProvider: 'GITHUB', defaultBranch: 'main', packagePrefix: '' };
      },
      error: (err) => {
        this.error = 'Failed to create project';
        console.error('Error creating project:', err);
      }
    });
  }

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

  createServiceGroup(): void {
    this.loading = true;
    this.apiService.createServiceGroup(this.formData).subscribe({
      next: () => {
        this.closeModals();
        this.loadServiceGroups();
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
    this.apiService.updateServiceGroup(this.editingGroup.id, this.formData).subscribe({
      next: () => {
        this.closeModals();
        this.loadServiceGroups();
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
      },
      error: (err) => {
        this.error = 'Failed to delete service group';
        console.error('Error deleting service group:', err);
      }
    });
  }
}
