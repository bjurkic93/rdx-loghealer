package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.entity.Project;
import com.reddiax.loghealer.entity.Tenant;
import com.reddiax.loghealer.repository.jpa.ProjectRepository;
import com.reddiax.loghealer.repository.jpa.TenantRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Slf4j
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final TenantRepository tenantRepository;

    private static final String DEFAULT_TENANT_NAME = "reddia-x";

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAllProjects() {
        log.info("Getting all projects");
        Tenant tenant = getOrCreateDefaultTenant();
        List<ProjectResponse> projects = projectRepository.findByTenantId(tenant.getId()).stream()
                .filter(Project::isActive)
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id) {
        log.info("Getting project: {}", id);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id));
        return ResponseEntity.ok(toResponse(project));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectRequest request) {
        log.info("Creating project: {}", request.getName());
        Tenant tenant = getOrCreateDefaultTenant();

        Project project = Project.builder()
                .tenant(tenant)
                .name(request.getName())
                .repoUrl(request.getRepoUrl())
                .gitProvider(request.getGitProvider() != null ? 
                        Project.GitProvider.valueOf(request.getGitProvider()) : null)
                .defaultBranch(request.getDefaultBranch() != null ? 
                        request.getDefaultBranch() : "main")
                .build();

        project = projectRepository.save(project);
        log.info("Created project: {} with API key: {}", project.getName(), project.getApiKey());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(project));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectRequest request) {
        log.info("Updating project: {}", id);
        
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id));

        project.setName(request.getName());
        project.setRepoUrl(request.getRepoUrl());
        project.setGitProvider(request.getGitProvider() != null ? 
                Project.GitProvider.valueOf(request.getGitProvider()) : null);
        project.setDefaultBranch(request.getDefaultBranch());

        project = projectRepository.save(project);
        return ResponseEntity.ok(toResponse(project));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        log.info("Deleting project: {}", id);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id));
        
        project.setActive(false);
        projectRepository.save(project);
        return ResponseEntity.noContent().build();
    }

    private Tenant getOrCreateDefaultTenant() {
        return tenantRepository.findAll().stream()
                .filter(t -> DEFAULT_TENANT_NAME.equals(t.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Tenant tenant = Tenant.builder()
                            .name(DEFAULT_TENANT_NAME)
                            .slug(DEFAULT_TENANT_NAME)
                            .build();
                    return tenantRepository.save(tenant);
                });
    }

    private ProjectResponse toResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .repoUrl(project.getRepoUrl())
                .gitProvider(project.getGitProvider() != null ? project.getGitProvider().name() : null)
                .defaultBranch(project.getDefaultBranch())
                .apiKey(project.getApiKey())
                .active(project.isActive())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    @Data
    public static class ProjectRequest {
        @NotBlank
        private String name;
        private String repoUrl;
        private String gitProvider;
        private String defaultBranch;
    }

    @Data
    @lombok.Builder
    public static class ProjectResponse {
        private UUID id;
        private String name;
        private String repoUrl;
        private String gitProvider;
        private String defaultBranch;
        private String apiKey;
        private boolean active;
        private java.time.Instant createdAt;
        private java.time.Instant updatedAt;
    }
}
