package com.reddiax.loghealer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    @Column(name = "project_key", nullable = false)
    private String projectKey;

    @Column(name = "git_provider")
    @Enumerated(EnumType.STRING)
    private GitProvider gitProvider;

    @Column(name = "repo_url")
    private String repoUrl;

    @Column(name = "default_branch")
    private String defaultBranch;

    @Column(name = "package_prefix")
    private String packagePrefix;

    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (apiKey == null) {
            apiKey = "lh_" + UUID.randomUUID().toString().replace("-", "");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum GitProvider {
        GITHUB, GITLAB, BITBUCKET
    }
}
