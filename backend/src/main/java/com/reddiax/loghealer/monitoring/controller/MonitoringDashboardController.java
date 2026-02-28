package com.reddiax.loghealer.monitoring.controller;

import com.reddiax.loghealer.monitoring.dto.DashboardSummaryDto;
import com.reddiax.loghealer.monitoring.dto.MetricsDto;
import com.reddiax.loghealer.monitoring.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/monitoring/dashboard")
@RequiredArgsConstructor
@Tag(name = "Monitoring Dashboard", description = "Dashboard and metrics endpoints")
public class MonitoringDashboardController {

    private final MetricsService metricsService;

    @GetMapping("/summary")
    @Operation(summary = "Get dashboard summary", description = "Returns overall system status and recent alerts")
    public ResponseEntity<DashboardSummaryDto> getDashboardSummary() {
        return ResponseEntity.ok(metricsService.getDashboardSummary());
    }

    @GetMapping("/metrics/{serviceId}")
    @Operation(summary = "Get service metrics", description = "Returns detailed metrics for a specific service")
    public ResponseEntity<MetricsDto> getServiceMetrics(@PathVariable Long serviceId) {
        return ResponseEntity.ok(metricsService.getServiceMetrics(serviceId));
    }
}
