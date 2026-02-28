package com.reddiax.loghealer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceGroupResponse {

    private UUID id;
    private String name;
    private String description;
    private List<ProjectSummary> projects;
    private List<DatabaseConnectionSummary> databases;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectSummary {
        private UUID id;
        private String name;
        private String repoUrl;
        private String gitProvider;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseConnectionSummary {
        private UUID id;
        private String name;
        private String dbType;
        private String host;
        private Integer port;
        private String databaseName;
        private String schemaName;
    }
}
