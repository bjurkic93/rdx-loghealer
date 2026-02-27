package com.reddiax.loghealer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubConnectionRequest {
    private String code;
    private String repositoryFullName; // e.g., "owner/repo"
}
