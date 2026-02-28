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
public class HealthCheckDto {
    private Long id;
    private Long serviceId;
    private String serviceName;
    private ServiceStatus status;
    private Integer responseTimeMs;
    private Integer statusCode;
    private String errorMessage;
    private Instant checkedAt;
}
