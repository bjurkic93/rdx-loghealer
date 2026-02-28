package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.document.LogEntryDocument;
import com.reddiax.loghealer.dto.TraceTimelineResponse;
import com.reddiax.loghealer.service.trace.TraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/traces")
@RequiredArgsConstructor
@Slf4j
public class TraceController {

    private final TraceService traceService;

    @GetMapping("/{traceId}")
    public ResponseEntity<TraceTimelineResponse> getTraceTimeline(@PathVariable String traceId) {
        log.info("Getting trace timeline for: {}", traceId);
        return ResponseEntity.ok(traceService.getTraceTimeline(traceId));
    }

    @GetMapping("/{traceId}/service-group/{serviceGroupId}")
    public ResponseEntity<TraceTimelineResponse> getTraceTimelineForServiceGroup(
            @PathVariable String traceId,
            @PathVariable UUID serviceGroupId) {
        log.info("Getting trace timeline for: {} in service group: {}", traceId, serviceGroupId);
        return ResponseEntity.ok(traceService.getTraceTimelineForServiceGroup(traceId, serviceGroupId));
    }

    @GetMapping("/exception/{exceptionGroupId}/related")
    public ResponseEntity<List<TraceTimelineResponse>> getRelatedTraces(
            @PathVariable String exceptionGroupId,
            @RequestParam(defaultValue = "5") int limit) {
        log.info("Finding related traces for exception: {}", exceptionGroupId);
        return ResponseEntity.ok(traceService.findRelatedTraces(exceptionGroupId, limit));
    }

    @GetMapping("/{traceId}/correlated/{serviceGroupId}")
    public ResponseEntity<Map<String, List<LogEntryDocument>>> getCorrelatedLogs(
            @PathVariable String traceId,
            @PathVariable UUID serviceGroupId) {
        log.info("Getting correlated logs for trace: {} in service group: {}", traceId, serviceGroupId);
        return ResponseEntity.ok(traceService.getCorrelatedLogsForServiceGroup(serviceGroupId, traceId));
    }
}
