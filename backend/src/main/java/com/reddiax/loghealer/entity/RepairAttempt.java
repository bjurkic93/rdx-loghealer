package com.reddiax.loghealer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repair_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepairAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_analysis_id", nullable = false)
    private AiAnalysis aiAnalysis;

    @Column(name = "git_provider", nullable = false)
    @Enumerated(EnumType.STRING)
    private Project.GitProvider gitProvider;

    @Column(name = "pr_url")
    private String prUrl;

    @Column(name = "branch_name", nullable = false)
    private String branchName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RepairStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = RepairStatus.PENDING;
        }
    }

    public enum RepairStatus {
        PENDING,
        BRANCH_CREATED,
        PR_CREATED,
        MERGED,
        CLOSED,
        FAILED
    }
}
