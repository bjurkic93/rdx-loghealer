package com.reddiax.loghealer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResponse {
    private String id;
    private String exceptionGroupId;
    private String provider;
    private String model;
    
    private String rootCause;
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW
    private String impact;
    
    private SuggestedFix suggestedFix;
    private List<String> preventionTips;
    private List<String> similarPatterns;
    
    private PerformanceInsight performanceInsight;
    
    private Instant analyzedAt;
    private int tokensUsed;
    private long processingTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestedFix {
        private String description;
        private String codeSnippet;
        private String language;
        private String fileName;
        private int lineNumber;
        private double confidence; // 0.0 - 1.0
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceInsight {
        private boolean isPerformanceRelated;
        private String bottleneck;
        private String optimization;
        private String estimatedImprovement;
    }
}
