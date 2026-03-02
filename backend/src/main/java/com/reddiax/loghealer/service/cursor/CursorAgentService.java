package com.reddiax.loghealer.service.cursor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reddiax.loghealer.document.ExceptionGroupDocument;
import com.reddiax.loghealer.dto.CursorAgentResponse;
import com.reddiax.loghealer.entity.CursorAgentTask;
import com.reddiax.loghealer.entity.Project;
import com.reddiax.loghealer.repository.jpa.CursorAgentTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CursorAgentService {

    private final ObjectMapper objectMapper;
    private final CursorAgentTaskRepository taskRepository;

    @Value("${loghealer.ai.cursor.api-key:}")
    private String cursorApiKey;

    @Value("${loghealer.ai.cursor.webhook-secret:}")
    private String webhookSecret;

    @Value("${loghealer.base-url:https://loghealer.reddia-x.com}")
    private String baseUrl;

    private final WebClient cursorWebClient = WebClient.builder()
            .baseUrl("https://api.cursor.com")
            .build();

    public CursorAgentResponse launchFixAgent(ExceptionGroupDocument exception, Project project) {
        if (cursorApiKey == null || cursorApiKey.isEmpty()) {
            return CursorAgentResponse.builder()
                    .status("ERROR")
                    .message("Cursor API key not configured")
                    .build();
        }

        if (project.getRepoUrl() == null || project.getRepoUrl().isEmpty()) {
            return CursorAgentResponse.builder()
                    .status("ERROR")
                    .message("Project has no GitHub repository configured")
                    .build();
        }

        String prompt = buildPrompt(exception);
        String branchName = "fix/loghealer-" + exception.getId().substring(0, 8);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("prompt", Map.of("text", prompt));
            requestBody.put("source", Map.of(
                    "repository", project.getRepoUrl(),
                    "ref", project.getDefaultBranch() != null ? project.getDefaultBranch() : "main"
            ));
            requestBody.put("target", Map.of(
                    "autoCreatePr", true,
                    "branchName", branchName,
                    "openAsCursorGithubApp", false
            ));
            
            if (webhookSecret != null && webhookSecret.length() >= 32) {
                requestBody.put("webhook", Map.of(
                        "url", baseUrl + "/api/v1/webhooks/cursor-agent",
                        "secret", webhookSecret
                ));
            }

            log.info("Launching Cursor agent for exception {} on repo {}", 
                    exception.getId(), project.getRepoUrl());

            String response = cursorWebClient.post()
                    .uri("/v0/agents")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodeApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode responseNode = objectMapper.readTree(response);
            String agentId = responseNode.path("id").asText();
            String status = responseNode.path("status").asText();
            String agentUrl = responseNode.path("target").path("url").asText();

            CursorAgentTask task = CursorAgentTask.builder()
                    .cursorAgentId(agentId)
                    .exceptionGroupId(exception.getId())
                    .projectId(project.getId())
                    .status(status)
                    .branchName(branchName)
                    .agentUrl(agentUrl)
                    .createdAt(Instant.now())
                    .build();
            taskRepository.save(task);

            log.info("Cursor agent launched: {} with status {}", agentId, status);

            return CursorAgentResponse.builder()
                    .agentId(agentId)
                    .status(status)
                    .agentUrl(agentUrl)
                    .branchName(branchName)
                    .message("Agent launched successfully. It will analyze the code and create a PR.")
                    .build();

        } catch (Exception e) {
            log.error("Failed to launch Cursor agent", e);
            return CursorAgentResponse.builder()
                    .status("ERROR")
                    .message("Failed to launch agent: " + e.getMessage())
                    .build();
        }
    }

    public CursorAgentResponse getAgentStatus(String agentId) {
        try {
            String response = cursorWebClient.get()
                    .uri("/v0/agents/{id}", agentId)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodeApiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            JsonNode responseNode = objectMapper.readTree(response);
            String status = responseNode.path("status").asText();
            String summary = responseNode.path("summary").asText(null);
            String prUrl = responseNode.path("target").path("prUrl").asText(null);
            String agentUrl = responseNode.path("target").path("url").asText(null);

            taskRepository.findByCursorAgentId(agentId).ifPresent(task -> {
                task.setStatus(status);
                if (prUrl != null) task.setPrUrl(prUrl);
                if (summary != null) task.setSummary(summary);
                task.setUpdatedAt(Instant.now());
                taskRepository.save(task);
            });

            return CursorAgentResponse.builder()
                    .agentId(agentId)
                    .status(status)
                    .summary(summary)
                    .prUrl(prUrl)
                    .agentUrl(agentUrl)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get agent status for {}", agentId, e);
            return CursorAgentResponse.builder()
                    .agentId(agentId)
                    .status("ERROR")
                    .message("Failed to get status: " + e.getMessage())
                    .build();
        }
    }

    public CursorAgentResponse getAgentConversation(String agentId) {
        try {
            String response = cursorWebClient.get()
                    .uri("/v0/agents/{id}/conversation", agentId)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodeApiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            JsonNode responseNode = objectMapper.readTree(response);
            JsonNode messagesNode = responseNode.path("messages");

            List<CursorAgentResponse.ConversationMessage> messages = new ArrayList<>();
            for (JsonNode msg : messagesNode) {
                messages.add(CursorAgentResponse.ConversationMessage.builder()
                        .id(msg.path("id").asText())
                        .type(msg.path("type").asText())
                        .text(msg.path("text").asText())
                        .build());
            }

            return CursorAgentResponse.builder()
                    .agentId(agentId)
                    .status("OK")
                    .conversation(messages)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get agent conversation for {}", agentId, e);
            return CursorAgentResponse.builder()
                    .agentId(agentId)
                    .status("ERROR")
                    .message("Failed to get conversation: " + e.getMessage())
                    .build();
        }
    }

    public CursorAgentResponse sendFollowUp(String agentId, String message) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "prompt", Map.of("text", message)
            );

            cursorWebClient.post()
                    .uri("/v0/agents/{id}/followup", agentId)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodeApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            log.info("Follow-up sent to agent {}", agentId);

            return CursorAgentResponse.builder()
                    .agentId(agentId)
                    .status("RUNNING")
                    .message("Follow-up sent successfully")
                    .build();

        } catch (Exception e) {
            log.error("Failed to send follow-up to agent {}", agentId, e);
            return CursorAgentResponse.builder()
                    .agentId(agentId)
                    .status("ERROR")
                    .message("Failed to send follow-up: " + e.getMessage())
                    .build();
        }
    }

    private String buildPrompt(ExceptionGroupDocument exception) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Fix this exception that occurred in production:\n\n");
        prompt.append("## Exception Type\n");
        prompt.append(exception.getExceptionClass()).append("\n\n");
        prompt.append("## Message\n");
        prompt.append(exception.getMessage()).append("\n\n");
        
        if (exception.getSampleStackTrace() != null) {
            prompt.append("## Stack Trace\n```\n");
            prompt.append(exception.getSampleStackTrace());
            prompt.append("\n```\n\n");
        }

        prompt.append("## Instructions\n");
        prompt.append("1. Analyze the stack trace and identify the root cause\n");
        prompt.append("2. Find the affected source files in this repository\n");
        prompt.append("3. Implement a proper fix\n");
        prompt.append("4. Make sure the fix handles edge cases\n");
        prompt.append("5. Do NOT modify external library code - only fix application code\n");
        
        return prompt.toString();
    }

    private String encodeApiKey() {
        return Base64.getEncoder().encodeToString((cursorApiKey + ":").getBytes(StandardCharsets.UTF_8));
    }
}
