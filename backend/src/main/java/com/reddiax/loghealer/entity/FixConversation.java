package com.reddiax.loghealer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "fix_conversation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exception_group_id", nullable = false)
    private String exceptionGroupId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "repository_full_name")
    private String repositoryFullName;

    @Column(name = "pr_number")
    private Integer prNumber;

    @Column(name = "pr_url")
    private String prUrl;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FixConversationMessage> messages = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addMessage(FixConversationMessage message) {
        messages.add(message);
        message.setConversation(this);
    }

    public enum ConversationStatus {
        ACTIVE,
        PR_CREATED,
        PR_MERGED,
        CLOSED
    }
}
