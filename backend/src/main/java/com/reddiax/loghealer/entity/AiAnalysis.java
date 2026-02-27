package com.reddiax.loghealer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exception_group_id", nullable = false)
    private String exceptionGroupId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AiProvider provider;

    @Column(columnDefinition = "text")
    private String prompt;

    @Column(columnDefinition = "text")
    private String response;

    @Column(name = "fix_suggestion", columnDefinition = "text")
    private String fixSuggestion;

    @Column(name = "affected_files")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> affectedFiles;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public enum AiProvider {
        CLAUDE, OPENAI, LOCAL
    }
}
