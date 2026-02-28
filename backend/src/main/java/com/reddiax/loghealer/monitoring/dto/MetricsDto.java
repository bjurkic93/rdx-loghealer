package com.reddiax.loghealer.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsDto {
    private Long serviceId;
    private String serviceName;
    private Double uptimePercentage24h;
    private Double uptimePercentage7d;
    private Double uptimePercentage30d;
    private Double avgResponseTime24h;
    private Double avgResponseTime7d;
    private Integer minResponseTime24h;
    private Integer maxResponseTime24h;
    private Long totalChecks24h;
    private Long failedChecks24h;
    private List<HealthCheckDto> recentChecks;
    private List<TimeSeriesDataPoint> responseTimeHistory;
    private List<TimeSeriesDataPoint> uptimeHistory;
}
