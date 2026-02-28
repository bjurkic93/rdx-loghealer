package com.reddiax.loghealer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceGroupRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255)
    private String name;

    @Size(max = 500)
    private String description;

    private Set<UUID> projectIds;

    private Set<DatabaseConnectionDto> databases;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseConnectionDto {
        @NotBlank
        private String name;
        @NotBlank
        private String dbType;
        private String host;
        private Integer port;
        private String databaseName;
        private String schemaName;
    }
}
