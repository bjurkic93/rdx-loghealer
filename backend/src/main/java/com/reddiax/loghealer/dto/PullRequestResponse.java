package com.reddiax.loghealer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestResponse {
    private String id;
    private int prNumber;
    private String title;
    private String description;
    private String htmlUrl;
    private String branchName;
    private String status;
    private String exceptionGroupId;
    private String repositoryFullName;
    private Instant createdAt;
}
