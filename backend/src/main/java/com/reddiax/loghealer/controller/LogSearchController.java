package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.dto.LogSearchRequest;
import com.reddiax.loghealer.dto.LogSearchResponse;
import com.reddiax.loghealer.service.search.LogSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Tag(name = "Log Search", description = "APIs for searching and exploring logs")
public class LogSearchController {

    private final LogSearchService logSearchService;

    @PostMapping("/search")
    @Operation(summary = "Search logs with filters")
    public ResponseEntity<LogSearchResponse> searchLogs(@RequestBody LogSearchRequest request) {
        return ResponseEntity.ok(logSearchService.search(request));
    }

    @GetMapping("/search")
    @Operation(summary = "Search logs with query parameters")
    public ResponseEntity<LogSearchResponse> searchLogsGet(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> levels,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String logger,
            @RequestParam(required = false) String exceptionClass,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        LogSearchRequest request = LogSearchRequest.builder()
            .query(query)
            .levels(levels)
            .projectId(projectId)
            .logger(logger)
            .exceptionClass(exceptionClass)
            .environment(environment)
            .fromTimestamp(from)
            .toTimestamp(to)
            .page(page)
            .size(size)
            .sortBy(sortBy)
            .sortOrder(sortOrder)
            .build();

        return ResponseEntity.ok(logSearchService.search(request));
    }
}
