package com.reddiax.loghealer.monitoring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "alert_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 50)
    private AlertRuleType ruleType;

    @Column(name = "threshold_value", nullable = false)
    private Integer thresholdValue;

    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private Integer consecutiveFailures = 1;

    @Column(name = "notify_emails", nullable = false, columnDefinition = "text")
    private String notifyEmails;

    @Column(name = "cooldown_minutes", nullable = false)
    @Builder.Default
    private Integer cooldownMinutes = 15;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public List<String> getEmailList() {
        return Arrays.asList(notifyEmails.split(","));
    }

    public void setEmailList(List<String> emails) {
        this.notifyEmails = String.join(",", emails);
    }
}
