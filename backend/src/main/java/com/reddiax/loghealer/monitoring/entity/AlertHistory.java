package com.reddiax.loghealer.monitoring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "alert_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 50)
    private AlertRuleType alertType;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "triggered_at", nullable = false)
    @Builder.Default
    private Instant triggeredAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "notification_sent", nullable = false)
    @Builder.Default
    private Boolean notificationSent = false;

    @Column(name = "notification_sent_at")
    private Instant notificationSentAt;

    public boolean isResolved() {
        return resolvedAt != null;
    }
}
