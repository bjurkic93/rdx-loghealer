package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.dto.BatchLogRequest;
import com.reddiax.loghealer.dto.LogEntryRequest;
import com.reddiax.loghealer.dto.LogIngestionResponse;
import com.reddiax.loghealer.service.ingestion.LogIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Tag(name = "Log Ingestion", description = "APIs for ingesting logs from client applications")
public class LogIngestionController {

    private final LogIngestionService logIngestionService;

    @PostMapping
    @Operation(summary = "Ingest a single log entry")
    public ResponseEntity<LogIngestionResponse> ingestSingleLog(
            @Parameter(description = "Project API key", required = true)
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody LogEntryRequest request) {
        
        logIngestionService.ingestSingle(apiKey, request);
        return ResponseEntity.accepted().body(LogIngestionResponse.success(1));
    }

    @PostMapping("/batch")
    @Operation(summary = "Ingest multiple log entries in batch")
    public ResponseEntity<LogIngestionResponse> ingestBatchLogs(
            @Parameter(description = "Project API key", required = true)
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody BatchLogRequest request) {
        
        int count = logIngestionService.ingestBatch(apiKey, request.getLogs());
        return ResponseEntity.accepted().body(LogIngestionResponse.success(count));
    }
}
