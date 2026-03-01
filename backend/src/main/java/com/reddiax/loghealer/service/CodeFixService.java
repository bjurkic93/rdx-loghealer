package com.reddiax.loghealer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reddiax.loghealer.dto.CodeFixRequest;
import com.reddiax.loghealer.dto.CodeFixResponse;
import com.reddiax.loghealer.entity.FixConversation;
import com.reddiax.loghealer.entity.FixConversationMessage;
import com.reddiax.loghealer.entity.Project;
import com.reddiax.loghealer.repository.elasticsearch.ExceptionGroupRepository;
import com.reddiax.loghealer.repository.jpa.FixConversationRepository;
import com.reddiax.loghealer.repository.jpa.ProjectRepository;
import com.reddiax.loghealer.service.github.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeFixService {

    private final FixConversationRepository conversationRepository;
    private final ExceptionGroupRepository exceptionGroupRepository;
    private final ProjectRepository projectRepository;
    private final GitHubService gitHubService;
    private final ObjectMapper objectMapper;

    @Value("${ai.claude.api-key:}")
    private String claudeApiKey;

    @Value("${ai.claude.model:claude-sonnet-4-20250514}")
    private String claudeModel;

    private final WebClient claudeWebClient;

    private static final Pattern STACK_TRACE_FILE_PATTERN = Pattern.compile(
            "at\\s+([\\w.]+)\\.([\\w$]+)\\.([\\w$]+)\\(([\\w]+\\.java):(\\d+)\\)"
    );

    @Transactional
    public CodeFixResponse analyzeAndFix(CodeFixRequest request) {
        log.info("Starting Codex-style fix for exception: {}", request.getExceptionGroupId());

        var exceptionOpt = exceptionGroupRepository.findById(request.getExceptionGroupId());
        if (exceptionOpt.isEmpty()) {
            return CodeFixResponse.builder()
                    .status("ERROR")
                    .message("Exception not found: " + request.getExceptionGroupId())
                    .build();
        }

        var exception = exceptionOpt.get();
        var projectOpt = projectRepository.findById(UUID.fromString(request.getProjectId()));
        if (projectOpt.isEmpty()) {
            return CodeFixResponse.builder()
                    .status("ERROR")
                    .message("Project not found: " + request.getProjectId())
                    .build();
        }

        var project = projectOpt.get();
        if (project.getRepoUrl() == null || project.getRepoUrl().isEmpty()) {
            return CodeFixResponse.builder()
                    .status("ERROR")
                    .message("Project has no GitHub repository configured")
                    .build();
        }

        FixConversation conversation;
        if (request.getConversationId() != null) {
            conversation = conversationRepository.findByIdWithMessages(UUID.fromString(request.getConversationId()))
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));
        } else {
            conversation = FixConversation.builder()
                    .exceptionGroupId(request.getExceptionGroupId())
                    .projectId(request.getProjectId())
                    .repositoryFullName(extractRepoFullName(project.getRepoUrl()))
                    .status(FixConversation.ConversationStatus.ACTIVE)
                    .build();
            conversationRepository.save(conversation);
        }

        List<String> affectedFiles = extractAffectedFiles(exception.getSampleStackTrace(), project.getPackagePrefix());
        
        Map<String, String> sourceFiles = new HashMap<>();
        String repoFullName = conversation.getRepositoryFullName();
        String defaultBranch = project.getDefaultBranch() != null ? project.getDefaultBranch() : "main";
        
        for (String filePath : affectedFiles) {
            try {
                String content = gitHubService.getFileContent(repoFullName, filePath, defaultBranch);
                if (content != null) {
                    sourceFiles.put(filePath, content);
                }
            } catch (Exception e) {
                log.warn("Could not fetch file {}: {}", filePath, e.getMessage());
            }
        }

        if (sourceFiles.isEmpty()) {
            return CodeFixResponse.builder()
                    .conversationId(conversation.getId().toString())
                    .status("ERROR")
                    .message("Could not fetch any source files from repository. Check package prefix configuration.")
                    .build();
        }

        String userMessage = request.getUserMessage();
        if (userMessage == null || userMessage.isEmpty()) {
            userMessage = "Please analyze this exception and provide a fix.";
        }

        conversation.addMessage(FixConversationMessage.builder()
                .role(FixConversationMessage.MessageRole.USER)
                .content(userMessage)
                .build());

        String aiResponse = callClaudeForFix(exception, sourceFiles, conversation.getMessages(), userMessage);
        
        conversation.addMessage(FixConversationMessage.builder()
                .role(FixConversationMessage.MessageRole.ASSISTANT)
                .content(aiResponse)
                .build());

        conversationRepository.save(conversation);

        CodeFixResponse.Analysis analysis = parseAnalysis(aiResponse);
        List<CodeFixResponse.FileChange> changes = parseChanges(aiResponse);

        return CodeFixResponse.builder()
                .conversationId(conversation.getId().toString())
                .status("ANALYZED")
                .message("Analysis complete. Review the changes and confirm to create PR.")
                .analysis(analysis)
                .changes(changes)
                .build();
    }

    @Transactional
    public CodeFixResponse continueConversation(String conversationId, String userMessage) {
        var conversation = conversationRepository.findByIdWithMessages(UUID.fromString(conversationId))
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        var exceptionOpt = exceptionGroupRepository.findById(conversation.getExceptionGroupId());
        if (exceptionOpt.isEmpty()) {
            return CodeFixResponse.builder()
                    .conversationId(conversationId)
                    .status("ERROR")
                    .message("Exception not found")
                    .build();
        }

        var project = projectRepository.findById(UUID.fromString(conversation.getProjectId()))
                .orElseThrow(() -> new RuntimeException("Project not found"));

        List<String> affectedFiles = extractAffectedFiles(exceptionOpt.get().getSampleStackTrace(), project.getPackagePrefix());
        Map<String, String> sourceFiles = new HashMap<>();
        String defaultBranch = project.getDefaultBranch() != null ? project.getDefaultBranch() : "main";

        for (String filePath : affectedFiles) {
            try {
                String content = gitHubService.getFileContent(conversation.getRepositoryFullName(), filePath, defaultBranch);
                if (content != null) {
                    sourceFiles.put(filePath, content);
                }
            } catch (Exception e) {
                log.warn("Could not fetch file {}: {}", filePath, e.getMessage());
            }
        }

        conversation.addMessage(FixConversationMessage.builder()
                .role(FixConversationMessage.MessageRole.USER)
                .content(userMessage)
                .build());

        String aiResponse = callClaudeForFix(exceptionOpt.get(), sourceFiles, conversation.getMessages(), userMessage);

        conversation.addMessage(FixConversationMessage.builder()
                .role(FixConversationMessage.MessageRole.ASSISTANT)
                .content(aiResponse)
                .build());

        conversationRepository.save(conversation);

        CodeFixResponse.Analysis analysis = parseAnalysis(aiResponse);
        List<CodeFixResponse.FileChange> changes = parseChanges(aiResponse);

        CodeFixResponse.PullRequestInfo prInfo = null;
        if (conversation.getPrNumber() != null) {
            prInfo = CodeFixResponse.PullRequestInfo.builder()
                    .prNumber(conversation.getPrNumber())
                    .htmlUrl(conversation.getPrUrl())
                    .branchName(conversation.getBranchName())
                    .build();
        }

        return CodeFixResponse.builder()
                .conversationId(conversationId)
                .status("ANALYZED")
                .message("Updated analysis based on your feedback.")
                .analysis(analysis)
                .changes(changes)
                .pullRequest(prInfo)
                .build();
    }

    @Transactional
    public CodeFixResponse createPullRequest(String conversationId, List<CodeFixResponse.FileChange> changes) {
        var conversation = conversationRepository.findByIdWithMessages(UUID.fromString(conversationId))
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (changes == null || changes.isEmpty()) {
            return CodeFixResponse.builder()
                    .conversationId(conversationId)
                    .status("ERROR")
                    .message("No changes to apply")
                    .build();
        }

        var project = projectRepository.findById(UUID.fromString(conversation.getProjectId()))
                .orElseThrow(() -> new RuntimeException("Project not found"));

        String repoFullName = conversation.getRepositoryFullName();
        String defaultBranch = project.getDefaultBranch() != null ? project.getDefaultBranch() : "main";
        String branchName = "fix/loghealer-" + conversation.getExceptionGroupId().substring(0, 8);

        try {
            String baseSha = gitHubService.getLatestCommitShaPublic(repoFullName, defaultBranch);
            gitHubService.createBranchPublic(repoFullName, branchName, baseSha);

            for (CodeFixResponse.FileChange change : changes) {
                gitHubService.updateFileContent(
                        repoFullName,
                        branchName,
                        change.getFilePath(),
                        change.getNewCode(),
                        "fix: " + change.getChangeDescription()
                );
            }

            String prTitle = "fix: Auto-fix for " + conversation.getExceptionGroupId().substring(0, 8);
            String prBody = buildPrBody(changes, conversation);

            JsonNode prResponse = gitHubService.createPullRequestPublic(
                    repoFullName,
                    branchName,
                    defaultBranch,
                    prTitle,
                    prBody
            );

            int prNumber = prResponse.path("number").asInt();
            String prUrl = prResponse.path("html_url").asText();

            conversation.setPrNumber(prNumber);
            conversation.setPrUrl(prUrl);
            conversation.setBranchName(branchName);
            conversation.setStatus(FixConversation.ConversationStatus.PR_CREATED);
            conversationRepository.save(conversation);

            return CodeFixResponse.builder()
                    .conversationId(conversationId)
                    .status("PR_CREATED")
                    .message("Pull Request created successfully!")
                    .pullRequest(CodeFixResponse.PullRequestInfo.builder()
                            .prNumber(prNumber)
                            .title(prTitle)
                            .htmlUrl(prUrl)
                            .branchName(branchName)
                            .createdAt(Instant.now())
                            .build())
                    .changes(changes)
                    .build();

        } catch (Exception e) {
            log.error("Failed to create PR", e);
            return CodeFixResponse.builder()
                    .conversationId(conversationId)
                    .status("ERROR")
                    .message("Failed to create PR: " + e.getMessage())
                    .build();
        }
    }

    private List<String> extractAffectedFiles(String stackTrace, String packagePrefix) {
        List<String> files = new ArrayList<>();
        if (stackTrace == null) return files;

        Matcher matcher = STACK_TRACE_FILE_PATTERN.matcher(stackTrace);
        while (matcher.find()) {
            String packageName = matcher.group(1);
            String fileName = matcher.group(4);

            if (packagePrefix != null && !packageName.startsWith(packagePrefix)) {
                continue;
            }

            if (packageName.startsWith("java.") || packageName.startsWith("javax.") ||
                    packageName.startsWith("org.springframework.") || packageName.startsWith("org.apache.")) {
                continue;
            }

            String filePath = "src/main/java/" + packageName.replace('.', '/') + "/" + fileName;
            if (!files.contains(filePath)) {
                files.add(filePath);
            }

            if (files.size() >= 5) break;
        }

        return files;
    }

    private String extractRepoFullName(String repoUrl) {
        if (repoUrl == null) return null;
        String url = repoUrl.replace("https://github.com/", "").replace(".git", "");
        return url;
    }

    private String callClaudeForFix(Object exception, Map<String, String> sourceFiles, 
                                     List<FixConversationMessage> history, String userMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a code fixing assistant. Analyze the following exception and source code, then provide a fix.\n\n");

        prompt.append("## Exception Details\n");
        try {
            prompt.append(objectMapper.writeValueAsString(exception));
        } catch (Exception e) {
            prompt.append(exception.toString());
        }
        prompt.append("\n\n");

        prompt.append("## Source Files\n");
        for (Map.Entry<String, String> entry : sourceFiles.entrySet()) {
            prompt.append("### ").append(entry.getKey()).append("\n```java\n");
            prompt.append(entry.getValue());
            prompt.append("\n```\n\n");
        }

        prompt.append("## Instructions\n");
        prompt.append("Provide your response in the following JSON format:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"analysis\": {\n");
        prompt.append("    \"rootCause\": \"explanation of the root cause\",\n");
        prompt.append("    \"severity\": \"CRITICAL|HIGH|MEDIUM|LOW\",\n");
        prompt.append("    \"explanation\": \"detailed explanation\"\n");
        prompt.append("  },\n");
        prompt.append("  \"changes\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"filePath\": \"path/to/file.java\",\n");
        prompt.append("      \"language\": \"java\",\n");
        prompt.append("      \"oldCode\": \"exact code to replace\",\n");
        prompt.append("      \"newCode\": \"new code\",\n");
        prompt.append("      \"startLine\": 45,\n");
        prompt.append("      \"endLine\": 50,\n");
        prompt.append("      \"changeDescription\": \"what this change does\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        prompt.append("User request: ").append(userMessage);

        List<Map<String, String>> messages = new ArrayList<>();
        
        for (FixConversationMessage msg : history) {
            if (msg.getRole() == FixConversationMessage.MessageRole.USER) {
                messages.add(Map.of("role", "user", "content", msg.getContent()));
            } else if (msg.getRole() == FixConversationMessage.MessageRole.ASSISTANT) {
                messages.add(Map.of("role", "assistant", "content", msg.getContent()));
            }
        }

        if (messages.isEmpty() || !messages.get(messages.size() - 1).get("content").equals(userMessage)) {
            messages.add(Map.of("role", "user", "content", prompt.toString()));
        } else {
            messages.set(messages.size() - 1, Map.of("role", "user", "content", prompt.toString()));
        }

        try {
            String response = claudeWebClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", claudeApiKey)
                    .header("anthropic-version", "2023-06-01")
                    .bodyValue(Map.of(
                            "model", claudeModel,
                            "max_tokens", 4096,
                            "messages", messages
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();

            JsonNode responseNode = objectMapper.readTree(response);
            return responseNode.path("content").get(0).path("text").asText();
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            throw new RuntimeException("AI analysis failed: " + e.getMessage());
        }
    }

    private CodeFixResponse.Analysis parseAnalysis(String aiResponse) {
        try {
            String json = extractJson(aiResponse);
            JsonNode root = objectMapper.readTree(json);
            JsonNode analysis = root.path("analysis");
            
            return CodeFixResponse.Analysis.builder()
                    .rootCause(analysis.path("rootCause").asText())
                    .severity(analysis.path("severity").asText())
                    .explanation(analysis.path("explanation").asText())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse analysis from AI response", e);
            return CodeFixResponse.Analysis.builder()
                    .rootCause("Could not parse AI response")
                    .severity("MEDIUM")
                    .explanation(aiResponse)
                    .build();
        }
    }

    private List<CodeFixResponse.FileChange> parseChanges(String aiResponse) {
        List<CodeFixResponse.FileChange> changes = new ArrayList<>();
        try {
            String json = extractJson(aiResponse);
            JsonNode root = objectMapper.readTree(json);
            JsonNode changesNode = root.path("changes");
            
            for (JsonNode change : changesNode) {
                changes.add(CodeFixResponse.FileChange.builder()
                        .filePath(change.path("filePath").asText())
                        .language(change.path("language").asText("java"))
                        .oldCode(change.path("oldCode").asText())
                        .newCode(change.path("newCode").asText())
                        .startLine(change.path("startLine").asInt(0))
                        .endLine(change.path("endLine").asInt(0))
                        .changeDescription(change.path("changeDescription").asText())
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to parse changes from AI response", e);
        }
        return changes;
    }

    private String extractJson(String text) {
        int start = text.indexOf("```json");
        if (start != -1) {
            start = text.indexOf("\n", start) + 1;
            int end = text.indexOf("```", start);
            if (end != -1) {
                return text.substring(start, end).trim();
            }
        }
        
        start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1) {
            return text.substring(start, end + 1);
        }
        
        return text;
    }

    private String buildPrBody(List<CodeFixResponse.FileChange> changes, FixConversation conversation) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🤖 LogHealer Auto-Fix\n\n");
        sb.append("This PR was automatically generated by LogHealer AI to fix exception `")
                .append(conversation.getExceptionGroupId().substring(0, 8)).append("`.\n\n");
        
        sb.append("### Changes\n\n");
        for (CodeFixResponse.FileChange change : changes) {
            sb.append("#### `").append(change.getFilePath()).append("`\n");
            sb.append(change.getChangeDescription()).append("\n\n");
        }
        
        sb.append("---\n");
        sb.append("*Generated by [LogHealer](https://loghealer.reddia-x.com) - AI-powered exception analysis and auto-fix*");
        
        return sb.toString();
    }
}
