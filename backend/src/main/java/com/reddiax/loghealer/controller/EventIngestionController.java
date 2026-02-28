package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.service.EventIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
public class EventIngestionController {

    private static final Logger log = LoggerFactory.getLogger(EventIngestionController.class);

    private final EventIngestionService eventIngestionService;

    public EventIngestionController(EventIngestionService eventIngestionService) {
        this.eventIngestionService = eventIngestionService;
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> ingestEvents(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestBody EventBatchRequest request) {

        log.debug("Received {} events", request.events() != null ? request.events().size() : 0);

        int processed = eventIngestionService.processEvents(apiKey, request.events());

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "processed", processed
        ));
    }

    public record EventBatchRequest(List<Map<String, Object>> events) {}
}
