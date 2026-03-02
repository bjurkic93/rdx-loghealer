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

    @Value("${loghealer.ai.claude.api-key:}")
    private String claudeApiKey;

    @Value("${loghealer.ai.claude.model:claude-sonnet-4-20250514}")
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
        
        // Find project - first try by UUID if provided, then by projectKey from exception
        Project project = null;
        if (request.getProjectId() != null && !request.getProjectId().isEmpty()) {
            try {
                project = projectRepository.findById(UUID.fromString(request.getProjectId())).orElse(null);
            } catch (IllegalArgumentException e) {
                // Not a UUID, try by projectKey
                project = projectRepository.findByProjectKey(request.getProjectId()).orElse(null);
            }
        }
        
        // If still not found, try to find by exception's projectId (which should match project.projectKey)
        if (project == null && exception.getProjectId() != null) {
            project = projectRepository.findByProjectKey(exception.getProjectId()).orElse(null);
        }
        
        if (project == null) {
            return CodeFixResponse.builder()
                    .status("ERROR")
                    .message("Project not found. Please create a project with Project ID '" + exception.getProjectId() + "' and configure a GitHub repository URL.")
                    .build();
        }

        if (project.getRepoUrl() == null || project.getRepoUrl().isEmpty()) {
            return CodeFixResponse.builder()
                    .status("ERROR")
                    .message("Project '" + project.getName() + "' has no GitHub repository configured. Please add a repository URL.")
                    .build();
        }

        FixConversation conversation;
        if (request.getConversationId() != null) {
            conversation = conversationRepository.findByIdWithMessages(UUID.fromString(request.getConversationId()))
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));
        } else {
            conversation = FixConversation.builder()
                    .exceptionGroupId(request.getExceptionGroupId())
                    .projectId(project.getId().toString())
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

        // If no source files found, still proceed with analysis based on exception info only
        if (sourceFiles.isEmpty()) {
            log.info("No source files found in stack trace, proceeding with exception-only analysis");
        }

        String userMessage = request.getUserMessage();
        if (userMessage == null || userMessage.isEmpty()) {
            userMessage = "Please analyze this exception and provide a fix.";
        }

        conversation.addMessage(FixConversationMessage.builder()
                .role(FixConversationMessage.MessageRole.USER)
                .content(userMessage)
                .build());

        String aiResponse = callClaudeForFix(exception, sourceFiles, conversation.getMessages(), userMessage, repoFullName, defaultBranch);
        
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

        conversation.addMessage(FixConversationMessage.builder()
                .role(FixConversationMessage.MessageRole.USER)
                .content(userMessage)
                .build());

        String aiResponse = callClaudeForFix(exceptionOpt.get(), sourceFiles, conversation.getMessages(), userMessage, repoFullName, defaultBranch);

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

            // Skip common framework packages
            if (packageName.startsWith("java.") || packageName.startsWith("javax.") ||
                    packageName.startsWith("sun.") || packageName.startsWith("jdk.") ||
                    packageName.startsWith("org.springframework.") || 
                    packageName.startsWith("org.apache.catalina.") ||
                    packageName.startsWith("org.apache.tomcat.") ||
                    packageName.startsWith("org.apache.coyote.") ||
                    packageName.startsWith("jakarta.servlet.")) {
                continue;
            }

            // If packagePrefix is set, filter by it; otherwise accept all non-framework packages
            if (packagePrefix != null && !packagePrefix.isEmpty() && !packageName.startsWith(packagePrefix)) {
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
                                     List<FixConversationMessage> history, String userMessage,
                                     String repoFullName, String defaultBranch) {
        // Build initial prompt with exception info
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a code fixing assistant. Analyze the following exception and fix it.\n\n");

        prompt.append("## Exception Details\n");
        try {
            prompt.append(objectMapper.writeValueAsString(exception));
        } catch (Exception e) {
            prompt.append(exception.toString());
        }
        prompt.append("\n\n");

        // Add any source files we already have
        if (!sourceFiles.isEmpty()) {
            prompt.append("## Source Files Already Loaded\n");
            for (Map.Entry<String, String> entry : sourceFiles.entrySet()) {
                prompt.append("### ").append(entry.getKey()).append("\n```java\n");
                prompt.append(entry.getValue());
                prompt.append("\n```\n\n");
            }
        }

        prompt.append("## Instructions\n");
        prompt.append("You have access to tools to read files from the repository.\n");
        prompt.append("1. Use 'list_files' to discover what files exist in the project\n");
        prompt.append("2. Use 'read_file' to fetch relevant source files (controllers, services, etc.)\n");
        prompt.append("3. Then analyze the exception and provide a fix\n\n");
        prompt.append("IMPORTANT: Only read files from THIS repository. Do NOT attempt to read external library/framework files.\n");
        prompt.append("Ignore stack trace frames from packages like: org.apache, org.springframework, jakarta, javax, java., ");
        prompt.append("org.hibernate, com.fasterxml, io.netty, reactor, org.coyote, etc.\n");
        prompt.append("Focus ONLY on classes in the application's own package (usually com.reddiax, com.example, or similar).\n\n");
        prompt.append("When you have enough information, provide your response in this JSON format:\n");
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

        // Build message history
        List<Map<String, Object>> messages = new ArrayList<>();
        for (FixConversationMessage msg : history) {
            if (msg.getRole() == FixConversationMessage.MessageRole.USER) {
                messages.add(new HashMap<>(Map.of("role", "user", "content", msg.getContent())));
            } else if (msg.getRole() == FixConversationMessage.MessageRole.ASSISTANT) {
                messages.add(new HashMap<>(Map.of("role", "assistant", "content", msg.getContent())));
            }
        }

        if (messages.isEmpty() || !messages.get(messages.size() - 1).get("content").equals(userMessage)) {
            messages.add(new HashMap<>(Map.of("role", "user", "content", prompt.toString())));
        } else {
            messages.set(messages.size() - 1, new HashMap<>(Map.of("role", "user", "content", prompt.toString())));
        }

        // Define tools for Claude
        List<Map<String, Object>> tools = buildTools();

        return callClaudeWithTools(messages, tools, sourceFiles, repoFullName, defaultBranch, 5);
    }

    private List<Map<String, Object>> buildTools() {
        Map<String, Object> readFileTool = new HashMap<>();
        readFileTool.put("name", "read_file");
        readFileTool.put("description", "Read a source file from THIS repository only. Use for application code files (controllers, services, etc). Path format: src/main/java/com/package/ClassName.java. Do NOT use for external libraries like Spring, Apache, Jakarta - those are not in this repo.");
        
        Map<String, Object> readFileSchema = new HashMap<>();
        readFileSchema.put("type", "object");
        readFileSchema.put("properties", Map.of(
            "file_path", Map.of(
                "type", "string",
                "description", "The path to the file in the repository (e.g., src/main/java/com/example/MyController.java)"
            )
        ));
        readFileSchema.put("required", List.of("file_path"));
        readFileTool.put("input_schema", readFileSchema);

        Map<String, Object> listFilesTool = new HashMap<>();
        listFilesTool.put("name", "list_files");
        listFilesTool.put("description", "List files in a directory of the repository. Use this to discover what files exist. Start with 'src/main/java' to see the package structure.");
        
        Map<String, Object> listFilesSchema = new HashMap<>();
        listFilesSchema.put("type", "object");
        listFilesSchema.put("properties", Map.of(
            "directory_path", Map.of(
                "type", "string",
                "description", "The directory path to list (e.g., src/main/java or src/main/java/com/example)"
            )
        ));
        listFilesSchema.put("required", List.of("directory_path"));
        listFilesTool.put("input_schema", listFilesSchema);

        return List.of(readFileTool, listFilesTool);
    }

    private String callClaudeWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                        Map<String, String> loadedFiles, String repoFullName, String defaultBranch,
                                        int maxIterations) {
        for (int i = 0; i < maxIterations; i++) {
            try {
                String response = callClaudeApiWithRetry(messages, tools);

                JsonNode responseNode = objectMapper.readTree(response);
                String stopReason = responseNode.path("stop_reason").asText();
                JsonNode contentArray = responseNode.path("content");

                // Check if Claude wants to use a tool
                if ("tool_use".equals(stopReason)) {
                    // Process tool calls
                    List<Map<String, Object>> toolResults = new ArrayList<>();

                    for (JsonNode contentItem : contentArray) {
                        String type = contentItem.path("type").asText();
                        
                        if ("tool_use".equals(type)) {
                            String toolName = contentItem.path("name").asText();
                            String toolUseId = contentItem.path("id").asText();
                            JsonNode input = contentItem.path("input");

                            String toolResult = executeToolCall(toolName, input, loadedFiles, repoFullName, defaultBranch);
                            toolResults.add(Map.of(
                                "type", "tool_result",
                                "tool_use_id", toolUseId,
                                "content", toolResult
                            ));
                        }
                    }

                    // Add assistant's response with tool use
                    List<Object> assistantContentList = new ArrayList<>();
                    for (JsonNode contentItem : contentArray) {
                        assistantContentList.add(objectMapper.convertValue(contentItem, Map.class));
                    }
                    messages.add(Map.of("role", "assistant", "content", assistantContentList));

                    // Add tool results
                    messages.add(Map.of("role", "user", "content", toolResults));

                    log.info("Tool iteration {}: {} tool calls processed", i + 1, toolResults.size());

                } else {
                    // Claude finished - return the text response
                    for (JsonNode contentItem : contentArray) {
                        if ("text".equals(contentItem.path("type").asText())) {
                            return contentItem.path("text").asText();
                        }
                    }
                    return "No response text found";
                }

            } catch (Exception e) {
                log.error("Claude API call failed at iteration {}", i, e);
                throw new RuntimeException("AI analysis failed: " + e.getMessage());
            }
        }

        return "Analysis incomplete - reached maximum tool iterations. Please try again with a more specific request.";
    }

    private String callClaudeApiWithRetry(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        int maxRetries = 3;
        long baseDelayMs = 2000;

        for (int retry = 0; retry <= maxRetries; retry++) {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", claudeModel);
                requestBody.put("max_tokens", 4096);
                requestBody.put("messages", messages);
                requestBody.put("tools", tools);

                return claudeWebClient.post()
                        .uri("/messages")
                        .header("x-api-key", claudeApiKey)
                        .header("anthropic-version", "2023-06-01")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(120))
                        .block();

            } catch (org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests e) {
                if (retry == maxRetries) {
                    throw e;
                }
                long delayMs = baseDelayMs * (long) Math.pow(2, retry);
                log.warn("Rate limited (429), retrying in {}ms (attempt {}/{})", delayMs, retry + 1, maxRetries);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during rate limit backoff", ie);
                }
            }
        }
        throw new RuntimeException("Max retries exceeded for Claude API");
    }

    private String executeToolCall(String toolName, JsonNode input, Map<String, String> loadedFiles,
                                    String repoFullName, String defaultBranch) {
        try {
            switch (toolName) {
                case "read_file":
                    String filePath = input.path("file_path").asText();
                    log.info("Tool call: read_file({}) from repo {}", filePath, repoFullName);
                    
                    // Check if already loaded
                    if (loadedFiles.containsKey(filePath)) {
                        return loadedFiles.get(filePath);
                    }
                    
                    // Fetch from GitHub
                    try {
                        String content = gitHubService.getFileContent(repoFullName, filePath, defaultBranch);
                        if (content != null) {
                            loadedFiles.put(filePath, content);
                            return content;
                        }
                        return "File not found: " + filePath;
                    } catch (Exception e) {
                        log.warn("Could not fetch file {}: {}", filePath, e.getMessage());
                        return "Error reading file: " + e.getMessage();
                    }
                    
                case "list_files":
                    String dirPath = input.path("directory_path").asText();
                    log.info("Tool call: list_files({}) from repo {}", dirPath, repoFullName);
                    
                    try {
                        List<String> files = gitHubService.listDirectoryContents(repoFullName, dirPath, defaultBranch);
                        if (files.isEmpty()) {
                            return "Directory is empty or does not exist: " + dirPath;
                        }
                        return String.join("\n", files);
                    } catch (Exception e) {
                        log.warn("Could not list directory {}: {}", dirPath, e.getMessage());
                        return "Error listing directory: " + e.getMessage();
                    }
                    
                default:
                    return "Unknown tool: " + toolName;
            }
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return "Error executing tool: " + e.getMessage();
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
