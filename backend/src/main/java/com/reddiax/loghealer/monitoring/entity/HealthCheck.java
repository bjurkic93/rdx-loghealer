package com.reddiax.loghealer.monitoring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "health_check")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "checked_at", nullable = false)
    @Builder.Default
    private Instant checkedAt = Instant.now();
}
