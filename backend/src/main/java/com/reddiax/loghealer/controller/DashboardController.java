package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.dto.DashboardStatsResponse;
import com.reddiax.loghealer.service.search.LogSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard statistics and metrics")
public class DashboardController {

    private final LogSearchService logSearchService;

    @GetMapping("/stats")
    @Operation(summary = "Get dashboard statistics")
    public ResponseEntity<DashboardStatsResponse> getStats(
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "24h") String timeRange) {
        
        return ResponseEntity.ok(logSearchService.getDashboardStats(projectId, timeRange));
    }
}
