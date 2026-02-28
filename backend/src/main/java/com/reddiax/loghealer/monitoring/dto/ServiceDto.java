package com.reddiax.loghealer.monitoring.dto;

import com.reddiax.loghealer.monitoring.entity.ServiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDto {
    private Long id;
    private String name;
    private String description;
    private String url;
    private String healthEndpoint;
    private Integer checkIntervalSeconds;
    private Integer timeoutMs;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    
    private ServiceStatus currentStatus;
    private Integer lastResponseTimeMs;
    private Instant lastCheckedAt;
    private Double uptimePercentage;
    private Double avgResponseTimeMs;
}
