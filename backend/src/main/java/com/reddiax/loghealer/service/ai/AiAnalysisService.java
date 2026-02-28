package com.reddiax.loghealer.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reddiax.loghealer.document.ExceptionGroupDocument;
import com.reddiax.loghealer.dto.AiAnalysisResponse;
import com.reddiax.loghealer.service.search.LogSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final WebClient openaiWebClient;
    private final WebClient claudeWebClient;
    private final LogSearchService logSearchService;
    private final ObjectMapper objectMapper;

    @Value("${loghealer.ai.openai.model:gpt-4o}")
    private String openaiModel;

    @Value("${loghealer.ai.claude.model:claude-sonnet-4-6}")
    private String claudeModel;

    @Value("${loghealer.ai.default-provider:claude}")
    private String defaultProvider;

    public AiAnalysisResponse analyzeException(String exceptionGroupId, String provider, boolean generateFix) {
        long startTime = System.currentTimeMillis();
        
        ExceptionGroupDocument exception = logSearchService.getExceptionGroup(exceptionGroupId);
        if (exception == null) {
            throw new IllegalArgumentException("Exception group not found: " + exceptionGroupId);
        }

        String effectiveProvider = provider != null ? provider : defaultProvider;
        String prompt = buildAnalysisPrompt(exception, generateFix);

        log.info("Analyzing exception {} with provider: {}", exceptionGroupId, effectiveProvider);

        try {
            AiAnalysisResponse response;
            if ("openai".equalsIgnoreCase(effectiveProvider)) {
                response = analyzeWithOpenAi(prompt, generateFix);
                response.setProvider("openai");
                response.setModel(openaiModel);
            } else {
                response = analyzeWithClaude(prompt, generateFix);
                response.setProvider("claude");
                response.setModel(claudeModel);
            }

            response.setId(UUID.randomUUID().toString());
            response.setExceptionGroupId(exceptionGroupId);
            response.setAnalyzedAt(Instant.now());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);

            log.info("Analysis completed in {}ms using {} tokens", 
                    response.getProcessingTimeMs(), response.getTokensUsed());

            return response;
        } catch (Exception e) {
            log.error("AI analysis failed for exception {}", exceptionGroupId, e);
            throw new RuntimeException("AI analysis failed: " + e.getMessage(), e);
        }
    }

    private String buildAnalysisPrompt(ExceptionGroupDocument exception, boolean generateFix) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert Java developer and debugging specialist. ");
        prompt.append("Analyze the following exception and provide a detailed analysis.\n\n");
        
        prompt.append("## Exception Details\n");
        prompt.append("- **Type**: ").append(exception.getExceptionClass()).append("\n");
        prompt.append("- **Message**: ").append(exception.getMessage()).append("\n");
        prompt.append("- **Occurrences**: ").append(exception.getCount()).append("\n");
        prompt.append("- **First Seen**: ").append(exception.getFirstSeen()).append("\n");
        prompt.append("- **Last Seen**: ").append(exception.getLastSeen()).append("\n");
        
        if (exception.getSampleStackTrace() != null) {
            prompt.append("\n## Stack Trace\n```\n");
            prompt.append(exception.getSampleStackTrace());
            prompt.append("\n```\n");
        }

        prompt.append("\n## Required Analysis\n");
        prompt.append("Provide your analysis in the following JSON format:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"rootCause\": \"Clear explanation of why this exception occurs\",\n");
        prompt.append("  \"severity\": \"CRITICAL|HIGH|MEDIUM|LOW\",\n");
        prompt.append("  \"impact\": \"Description of the impact on the application\",\n");
        prompt.append("  \"preventionTips\": [\"tip1\", \"tip2\"],\n");
        prompt.append("  \"similarPatterns\": [\"pattern1\", \"pattern2\"]");
        
        if (generateFix) {
            prompt.append(",\n");
            prompt.append("  \"suggestedFix\": {\n");
            prompt.append("    \"description\": \"What the fix does\",\n");
            prompt.append("    \"codeSnippet\": \"The actual code fix\",\n");
            prompt.append("    \"language\": \"java\",\n");
            prompt.append("    \"confidence\": 0.85\n");
            prompt.append("  }");
        }
        
        prompt.append(",\n");
        prompt.append("  \"performanceInsight\": {\n");
        prompt.append("    \"isPerformanceRelated\": true|false,\n");
        prompt.append("    \"bottleneck\": \"description if performance related\",\n");
        prompt.append("    \"optimization\": \"suggested optimization\",\n");
        prompt.append("    \"estimatedImprovement\": \"e.g., 30% faster\"\n");
        prompt.append("  }\n");
        prompt.append("}\n");
        prompt.append("```\n");
        prompt.append("\nRespond ONLY with the JSON, no additional text.");

        return prompt.toString();
    }

    private AiAnalysisResponse analyzeWithOpenAi(String prompt, boolean generateFix) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openaiModel);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "You are a senior software engineer specializing in debugging and code optimization. Always respond with valid JSON only."),
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 2000);

        String responseBody = openaiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .block();

        return parseOpenAiResponse(responseBody);
    }

    private AiAnalysisResponse analyzeWithClaude(String prompt, boolean generateFix) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", claudeModel);
        requestBody.put("max_tokens", 2000);
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        String responseBody = claudeWebClient.post()
                .uri("/messages")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .block();

        return parseClaudeResponse(responseBody);
    }

    private AiAnalysisResponse parseOpenAiResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).path("message").path("content").asText();
                int tokensUsed = root.path("usage").path("total_tokens").asInt(0);
                
                return parseAnalysisJson(content, tokensUsed);
            }
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response", e);
        }
        return AiAnalysisResponse.builder().build();
    }

    private AiAnalysisResponse parseClaudeResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (content.isArray() && content.size() > 0) {
                String text = content.get(0).path("text").asText();
                int inputTokens = root.path("usage").path("input_tokens").asInt(0);
                int outputTokens = root.path("usage").path("output_tokens").asInt(0);
                
                return parseAnalysisJson(text, inputTokens + outputTokens);
            }
        } catch (Exception e) {
            log.error("Failed to parse Claude response", e);
        }
        return AiAnalysisResponse.builder().build();
    }

    private AiAnalysisResponse parseAnalysisJson(String jsonContent, int tokensUsed) {
        try {
            String cleanJson = jsonContent.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            JsonNode analysis = objectMapper.readTree(cleanJson);

            AiAnalysisResponse.AiAnalysisResponseBuilder builder = AiAnalysisResponse.builder()
                    .rootCause(analysis.path("rootCause").asText(""))
                    .severity(analysis.path("severity").asText("MEDIUM"))
                    .impact(analysis.path("impact").asText(""))
                    .tokensUsed(tokensUsed);

            // Prevention tips
            List<String> tips = new ArrayList<>();
            JsonNode tipsNode = analysis.path("preventionTips");
            if (tipsNode.isArray()) {
                tipsNode.forEach(tip -> tips.add(tip.asText()));
            }
            builder.preventionTips(tips);

            // Similar patterns
            List<String> patterns = new ArrayList<>();
            JsonNode patternsNode = analysis.path("similarPatterns");
            if (patternsNode.isArray()) {
                patternsNode.forEach(pattern -> patterns.add(pattern.asText()));
            }
            builder.similarPatterns(patterns);

            // Suggested fix
            JsonNode fixNode = analysis.path("suggestedFix");
            if (!fixNode.isMissingNode() && !fixNode.isNull()) {
                builder.suggestedFix(AiAnalysisResponse.SuggestedFix.builder()
                        .description(fixNode.path("description").asText(""))
                        .codeSnippet(fixNode.path("codeSnippet").asText(""))
                        .language(fixNode.path("language").asText("java"))
                        .fileName(fixNode.path("fileName").asText(""))
                        .lineNumber(fixNode.path("lineNumber").asInt(0))
                        .confidence(fixNode.path("confidence").asDouble(0.5))
                        .build());
            }

            // Performance insight
            JsonNode perfNode = analysis.path("performanceInsight");
            if (!perfNode.isMissingNode() && !perfNode.isNull()) {
                builder.performanceInsight(AiAnalysisResponse.PerformanceInsight.builder()
                        .isPerformanceRelated(perfNode.path("isPerformanceRelated").asBoolean(false))
                        .bottleneck(perfNode.path("bottleneck").asText(""))
                        .optimization(perfNode.path("optimization").asText(""))
                        .estimatedImprovement(perfNode.path("estimatedImprovement").asText(""))
                        .build());
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Failed to parse analysis JSON: {}", jsonContent, e);
            return AiAnalysisResponse.builder()
                    .rootCause("Failed to parse AI response")
                    .severity("UNKNOWN")
                    .tokensUsed(tokensUsed)
                    .build();
        }
    }

    public AiAnalysisResponse quickAnalyze(String exceptionClass, String message, String stackTrace) {
        long startTime = System.currentTimeMillis();
        
        String prompt = buildQuickAnalysisPrompt(exceptionClass, message, stackTrace);
        
        try {
            AiAnalysisResponse response = analyzeWithOpenAi(prompt, true);
            response.setId(UUID.randomUUID().toString());
            response.setProvider("openai");
            response.setModel(openaiModel);
            response.setAnalyzedAt(Instant.now());
            response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return response;
        } catch (Exception e) {
            log.error("Quick analysis failed", e);
            throw new RuntimeException("Quick analysis failed: " + e.getMessage(), e);
        }
    }

    private String buildQuickAnalysisPrompt(String exceptionClass, String message, String stackTrace) {
        return String.format("""
            Analyze this Java exception quickly:
            
            Exception: %s
            Message: %s
            Stack Trace:
            ```
            %s
            ```
            
            Respond with JSON:
            {
              "rootCause": "brief explanation",
              "severity": "CRITICAL|HIGH|MEDIUM|LOW",
              "suggestedFix": {
                "description": "what to fix",
                "codeSnippet": "the fix code",
                "language": "java",
                "confidence": 0.8
              }
            }
            """, exceptionClass, message, stackTrace != null ? stackTrace : "N/A");
    }
}
