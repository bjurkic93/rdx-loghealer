package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.dto.AiAnalysisRequest;
import com.reddiax.loghealer.dto.AiAnalysisResponse;
import com.reddiax.loghealer.service.ai.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    @PostMapping("/analyze/{exceptionGroupId}")
    public ResponseEntity<AiAnalysisResponse> analyzeException(
            @PathVariable String exceptionGroupId,
            @RequestParam(required = false, defaultValue = "claude") String provider,
            @RequestParam(required = false, defaultValue = "true") boolean generateFix) {
        
        log.info("AI analysis requested for exception: {} with provider: {}", exceptionGroupId, provider);
        
        AiAnalysisResponse response = aiAnalysisService.analyzeException(exceptionGroupId, provider, generateFix);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/analyze")
    public ResponseEntity<AiAnalysisResponse> analyzeWithRequest(@RequestBody AiAnalysisRequest request) {
        log.info("AI analysis requested: {}", request);
        
        AiAnalysisResponse response = aiAnalysisService.analyzeException(
                request.getExceptionGroupId(),
                request.getProvider(),
                request.isGenerateFix()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/quick-analyze")
    public ResponseEntity<AiAnalysisResponse> quickAnalyze(@RequestBody Map<String, String> request) {
        String exceptionClass = request.get("exceptionClass");
        String message = request.get("message");
        String stackTrace = request.get("stackTrace");

        log.info("Quick AI analysis for: {}", exceptionClass);
        
        AiAnalysisResponse response = aiAnalysisService.quickAnalyze(exceptionClass, message, stackTrace);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getProviders() {
        return ResponseEntity.ok(Map.of(
                "providers", new String[]{"openai", "claude"},
                "default", "claude",
                "models", Map.of(
                        "openai", "gpt-4o",
                        "claude", "claude-sonnet-4-20250514"
                )
        ));
    }
}
