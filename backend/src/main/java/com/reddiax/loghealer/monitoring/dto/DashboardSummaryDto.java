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
public class DashboardSummaryDto {
    private int totalServices;
    private int servicesUp;
    private int servicesDown;
    private int servicesDegraded;
    private int activeAlerts;
    private Double overallUptime;
    private List<ServiceDto> services;
    private List<AlertHistoryDto> recentAlerts;
}
