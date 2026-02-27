package com.reddiax.loghealer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisRequest {
    private String exceptionGroupId;
    private String provider; // "openai" or "claude"
    private boolean generateFix;
    private boolean analyzePerformance;
    private String additionalContext;
}
