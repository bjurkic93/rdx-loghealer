package com.reddiax.loghealer.service;

import com.reddiax.loghealer.dto.ServiceGroupRequest;
import com.reddiax.loghealer.dto.ServiceGroupResponse;
import com.reddiax.loghealer.entity.DatabaseConnection;
import com.reddiax.loghealer.entity.Project;
import com.reddiax.loghealer.entity.ServiceGroup;
import com.reddiax.loghealer.entity.Tenant;
import com.reddiax.loghealer.repository.jpa.DatabaseConnectionRepository;
import com.reddiax.loghealer.repository.jpa.ProjectRepository;
import com.reddiax.loghealer.repository.jpa.ServiceGroupRepository;
import com.reddiax.loghealer.repository.jpa.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceGroupService {

    private final ServiceGroupRepository serviceGroupRepository;
    private final ProjectRepository projectRepository;
    private final DatabaseConnectionRepository databaseConnectionRepository;
    private final TenantRepository tenantRepository;

    private static final String DEFAULT_TENANT_ID = "reddia-x";

    @Transactional(readOnly = true)
    public List<ServiceGroupResponse> getAllServiceGroups() {
        Tenant tenant = getOrCreateDefaultTenant();
        return serviceGroupRepository.findByTenantIdWithProjects(tenant.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceGroupResponse getServiceGroup(UUID id) {
        ServiceGroup serviceGroup = serviceGroupRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Service group not found: " + id));
        return toResponse(serviceGroup);
    }

    @Transactional
    public ServiceGroupResponse createServiceGroup(ServiceGroupRequest request) {
        Tenant tenant = getOrCreateDefaultTenant();

        ServiceGroup serviceGroup = ServiceGroup.builder()
                .tenant(tenant)
                .name(request.getName())
                .description(request.getDescription())
                .build();

        if (request.getProjectIds() != null && !request.getProjectIds().isEmpty()) {
            Set<Project> projects = new HashSet<>(projectRepository.findAllById(request.getProjectIds()));
            serviceGroup.setProjects(projects);
        }

        serviceGroup = serviceGroupRepository.save(serviceGroup);

        if (request.getDatabases() != null) {
            for (ServiceGroupRequest.DatabaseConnectionDto dbDto : request.getDatabases()) {
                DatabaseConnection db = DatabaseConnection.builder()
                        .serviceGroup(serviceGroup)
                        .name(dbDto.getName())
                        .dbType(DatabaseConnection.DatabaseType.valueOf(dbDto.getDbType()))
                        .host(dbDto.getHost())
                        .port(dbDto.getPort())
                        .databaseName(dbDto.getDatabaseName())
                        .schemaName(dbDto.getSchemaName())
                        .build();
                databaseConnectionRepository.save(db);
            }
        }

        log.info("Created service group: {} with {} projects", serviceGroup.getName(),
                serviceGroup.getProjects().size());

        return getServiceGroup(serviceGroup.getId());
    }

    @Transactional
    public ServiceGroupResponse updateServiceGroup(UUID id, ServiceGroupRequest request) {
        ServiceGroup serviceGroup = serviceGroupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service group not found: " + id));

        serviceGroup.setName(request.getName());
        serviceGroup.setDescription(request.getDescription());

        if (request.getProjectIds() != null) {
            Set<Project> projects = new HashSet<>(projectRepository.findAllById(request.getProjectIds()));
            serviceGroup.setProjects(projects);
        }

        if (request.getDatabases() != null) {
            databaseConnectionRepository.deleteAll(
                    databaseConnectionRepository.findByServiceGroupId(id)
            );

            for (ServiceGroupRequest.DatabaseConnectionDto dbDto : request.getDatabases()) {
                DatabaseConnection db = DatabaseConnection.builder()
                        .serviceGroup(serviceGroup)
                        .name(dbDto.getName())
                        .dbType(DatabaseConnection.DatabaseType.valueOf(dbDto.getDbType()))
                        .host(dbDto.getHost())
                        .port(dbDto.getPort())
                        .databaseName(dbDto.getDatabaseName())
                        .schemaName(dbDto.getSchemaName())
                        .build();
                databaseConnectionRepository.save(db);
            }
        }

        serviceGroupRepository.save(serviceGroup);
        log.info("Updated service group: {}", serviceGroup.getName());

        return getServiceGroup(id);
    }

    @Transactional
    public void deleteServiceGroup(UUID id) {
        ServiceGroup serviceGroup = serviceGroupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service group not found: " + id));
        
        serviceGroup.setActive(false);
        serviceGroupRepository.save(serviceGroup);
        log.info("Deactivated service group: {}", serviceGroup.getName());
    }

    @Transactional(readOnly = true)
    public List<UUID> getProjectIdsForServiceGroup(UUID serviceGroupId) {
        ServiceGroup serviceGroup = serviceGroupRepository.findByIdWithDetails(serviceGroupId)
                .orElseThrow(() -> new RuntimeException("Service group not found: " + serviceGroupId));
        
        return serviceGroup.getProjects().stream()
                .map(Project::getId)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ServiceGroup> findServiceGroupByProjectId(UUID projectId) {
        List<ServiceGroup> groups = serviceGroupRepository.findByProjectId(projectId);
        return groups.isEmpty() ? Optional.empty() : Optional.of(groups.get(0));
    }

    private Tenant getOrCreateDefaultTenant() {
        return tenantRepository.findAll().stream()
                .filter(t -> DEFAULT_TENANT_ID.equals(t.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Tenant tenant = Tenant.builder()
                            .name(DEFAULT_TENANT_ID)
                            .build();
                    return tenantRepository.save(tenant);
                });
    }

    private ServiceGroupResponse toResponse(ServiceGroup serviceGroup) {
        List<ServiceGroupResponse.ProjectSummary> projects = serviceGroup.getProjects().stream()
                .map(p -> ServiceGroupResponse.ProjectSummary.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .repoUrl(p.getRepoUrl())
                        .gitProvider(p.getGitProvider() != null ? p.getGitProvider().name() : null)
                        .build())
                .collect(Collectors.toList());

        List<ServiceGroupResponse.DatabaseConnectionSummary> databases = serviceGroup.getDatabases().stream()
                .map(d -> ServiceGroupResponse.DatabaseConnectionSummary.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .dbType(d.getDbType().name())
                        .host(d.getHost())
                        .port(d.getPort())
                        .databaseName(d.getDatabaseName())
                        .schemaName(d.getSchemaName())
                        .build())
                .collect(Collectors.toList());

        return ServiceGroupResponse.builder()
                .id(serviceGroup.getId())
                .name(serviceGroup.getName())
                .description(serviceGroup.getDescription())
                .projects(projects)
                .databases(databases)
                .active(serviceGroup.isActive())
                .createdAt(serviceGroup.getCreatedAt())
                .updatedAt(serviceGroup.getUpdatedAt())
                .build();
    }
}
