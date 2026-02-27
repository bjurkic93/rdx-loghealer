package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.document.ExceptionGroupDocument;
import com.reddiax.loghealer.service.search.LogSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exceptions")
@RequiredArgsConstructor
@Tag(name = "Exceptions", description = "Exception groups management")
public class ExceptionController {

    private final LogSearchService logSearchService;

    @GetMapping
    @Operation(summary = "List exception groups")
    public ResponseEntity<List<ExceptionGroupDocument>> listExceptions(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        return ResponseEntity.ok(logSearchService.getExceptionGroups(projectId, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get exception group details")
    public ResponseEntity<ExceptionGroupDocument> getException(@PathVariable String id) {
        ExceptionGroupDocument group = logSearchService.getExceptionGroup(id);
        if (group == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(group);
    }
}
