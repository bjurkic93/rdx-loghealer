package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.dto.CodeFixRequest;
import com.reddiax.loghealer.dto.CodeFixResponse;
import com.reddiax.loghealer.service.CodeFixService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/codefix")
@RequiredArgsConstructor
@Slf4j
public class CodeFixController {

    private final CodeFixService codeFixService;

    @PostMapping("/analyze")
    public ResponseEntity<CodeFixResponse> analyzeAndFix(@RequestBody CodeFixRequest request) {
        log.info("Received code fix request for exception: {}, project: {}", 
                request.getExceptionGroupId(), request.getProjectId());
        
        CodeFixResponse response = codeFixService.analyzeAndFix(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/conversation/{conversationId}/message")
    public ResponseEntity<CodeFixResponse> continueConversation(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> body) {
        
        String userMessage = body.get("message");
        log.info("Continuing conversation {} with message: {}", conversationId, 
                userMessage != null ? userMessage.substring(0, Math.min(50, userMessage.length())) : "null");
        
        CodeFixResponse response = codeFixService.continueConversation(conversationId, userMessage);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/conversation/{conversationId}/create-pr")
    public ResponseEntity<CodeFixResponse> createPullRequest(
            @PathVariable String conversationId,
            @RequestBody Map<String, Object> body) {
        
        log.info("Creating PR for conversation {}", conversationId);

        @SuppressWarnings("unchecked")
        List<CodeFixResponse.FileChange> changes = ((List<Map<String, Object>>) body.get("changes"))
                .stream()
                .map(this::mapToFileChange)
                .toList();
        
        CodeFixResponse response = codeFixService.createPullRequest(conversationId, changes);
        return ResponseEntity.ok(response);
    }

    private CodeFixResponse.FileChange mapToFileChange(Map<String, Object> map) {
        return CodeFixResponse.FileChange.builder()
                .filePath((String) map.get("filePath"))
                .language((String) map.getOrDefault("language", "java"))
                .oldCode((String) map.get("oldCode"))
                .newCode((String) map.get("newCode"))
                .startLine(map.get("startLine") != null ? ((Number) map.get("startLine")).intValue() : 0)
                .endLine(map.get("endLine") != null ? ((Number) map.get("endLine")).intValue() : 0)
                .changeDescription((String) map.get("changeDescription"))
                .build();
    }
}
