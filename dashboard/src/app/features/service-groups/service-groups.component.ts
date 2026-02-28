import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { ServiceGroup, ServiceGroupRequest, DatabaseConnectionRequest } from '../../core/models/service-group.model';

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
  loading = false;
  error: string | null = null;

  showCreateModal = false;
  showEditModal = false;
  editingGroup: ServiceGroup | null = null;

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

  dbTypes = ['POSTGRESQL', 'MYSQL', 'MARIADB', 'MONGODB', 'REDIS', 'ELASTICSEARCH'];

  ngOnInit(): void {
    this.loadServiceGroups();
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
    this.editingGroup = null;
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
