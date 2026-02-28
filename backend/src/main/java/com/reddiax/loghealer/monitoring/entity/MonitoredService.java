package com.reddiax.loghealer.monitoring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "monitored_service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "health_endpoint", nullable = false, length = 200)
    private String healthEndpoint;

    @Column(name = "check_interval_seconds", nullable = false)
    @Builder.Default
    private Integer checkIntervalSeconds = 30;

    @Column(name = "timeout_ms", nullable = false)
    @Builder.Default
    private Integer timeoutMs = 5000;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<HealthCheck> healthChecks = new ArrayList<>();

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AlertRule> alertRules = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getFullHealthUrl() {
        String baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        String endpoint = healthEndpoint.startsWith("/") ? healthEndpoint : "/" + healthEndpoint;
        return baseUrl + endpoint;
    }
}
