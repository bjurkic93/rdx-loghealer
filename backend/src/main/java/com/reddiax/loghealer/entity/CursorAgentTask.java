package com.reddiax.loghealer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cursor_agent_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursorAgentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cursor_agent_id", nullable = false, unique = true)
    private String cursorAgentId;

    @Column(name = "exception_group_id", nullable = false)
    private String exceptionGroupId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String status;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "pr_url")
    private String prUrl;

    @Column(name = "agent_url")
    private String agentUrl;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
