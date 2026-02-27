package com.reddiax.loghealer.service.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reddiax.loghealer.config.GitHubConfig;
import com.reddiax.loghealer.dto.AiAnalysisResponse;
import com.reddiax.loghealer.dto.PullRequestResponse;
import com.reddiax.loghealer.entity.GitHubConnection;
import com.reddiax.loghealer.repository.jpa.GitHubConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private final WebClient githubWebClient;
    private final WebClient githubOAuthClient;
    private final GitHubConfig gitHubConfig;
    private final GitHubConnectionRepository connectionRepository;
    private final ObjectMapper objectMapper;

    public String getAuthorizationUrl(String projectId) {
        String state = Base64.getEncoder().encodeToString(projectId.getBytes());
        return String.format(
                "https://github.com/login/oauth/authorize?client_id=%s&scope=repo&state=%s&redirect_uri=%s",
                gitHubConfig.getClientId(),
                state,
                "https://loghealer.reddia-x.com/api/v1/github/callback"
        );
    }

    public GitHubConnection exchangeCodeForToken(String code, String projectId) {
        try {
            String response = githubOAuthClient.post()
                    .uri("/login/oauth/access_token")
                    .bodyValue(Map.of(
                            "client_id", gitHubConfig.getClientId(),
                            "client_secret", gitHubConfig.getClientSecret(),
                            "code", code
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode tokenResponse = objectMapper.readTree(response);
            String accessToken = tokenResponse.path("access_token").asText();

            if (accessToken == null || accessToken.isEmpty()) {
                throw new RuntimeException("Failed to get access token: " + response);
            }

            JsonNode userInfo = getUserInfo(accessToken);
            String username = userInfo.path("login").asText();

            GitHubConnection connection = GitHubConnection.builder()
                    .projectId(projectId)
                    .accessToken(accessToken)
                    .githubUsername(username)
                    .isActive(true)
                    .build();

            return connectionRepository.save(connection);
        } catch (Exception e) {
            log.error("Failed to exchange code for token", e);
            throw new RuntimeException("GitHub OAuth failed: " + e.getMessage(), e);
        }
    }

    public void connectRepository(String connectionId, String repositoryFullName) {
        GitHubConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));

        JsonNode repoInfo = getRepositoryInfo(connection.getAccessToken(), repositoryFullName);
        String defaultBranch = repoInfo.path("default_branch").asText("main");

        connection.setRepositoryFullName(repositoryFullName);
        connection.setRepositoryDefaultBranch(defaultBranch);
        connectionRepository.save(connection);

        log.info("Connected repository {} to project {}", repositoryFullName, connection.getProjectId());
    }

    public PullRequestResponse createFixPullRequest(
            String projectId,
            String exceptionFingerprint,
            AiAnalysisResponse aiAnalysis
    ) {
        GitHubConnection connection = connectionRepository.findByProjectIdAndIsActiveTrue(projectId)
                .orElseThrow(() -> new IllegalArgumentException("No GitHub connection found for project"));

        if (aiAnalysis.getSuggestedFix() == null || aiAnalysis.getSuggestedFix().getCodeSnippet() == null) {
            throw new IllegalArgumentException("No code fix available in AI analysis");
        }

        String accessToken = connection.getAccessToken();
        String repoFullName = connection.getRepositoryFullName();
        String defaultBranch = connection.getRepositoryDefaultBranch();
        String branchName = "fix/loghealer-" + exceptionFingerprint.substring(0, 8);

        try {
            String baseSha = getLatestCommitSha(accessToken, repoFullName, defaultBranch);
            createBranch(accessToken, repoFullName, branchName, baseSha);

            String fileName = aiAnalysis.getSuggestedFix().getFileName();
            if (fileName == null || fileName.isEmpty()) {
                fileName = "LOGHEALER_FIX.md";
            }

            String fileContent = buildFixFileContent(aiAnalysis);
            createOrUpdateFile(accessToken, repoFullName, branchName, fileName, fileContent,
                    "fix: " + aiAnalysis.getSuggestedFix().getDescription());

            String prBody = buildPullRequestBody(aiAnalysis);
            JsonNode prResponse = createPullRequest(
                    accessToken,
                    repoFullName,
                    branchName,
                    defaultBranch,
                    "fix: " + truncate(aiAnalysis.getRootCause(), 50),
                    prBody
            );

            return PullRequestResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .prNumber(prResponse.path("number").asInt())
                    .title(prResponse.path("title").asText())
                    .description(prBody)
                    .htmlUrl(prResponse.path("html_url").asText())
                    .branchName(branchName)
                    .status("open")
                    .exceptionGroupId(exceptionFingerprint)
                    .repositoryFullName(repoFullName)
                    .createdAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to create PR for exception {}", exceptionFingerprint, e);
            throw new RuntimeException("Failed to create Pull Request: " + e.getMessage(), e);
        }
    }

    private JsonNode getUserInfo(String accessToken) {
        String response = githubWebClient.get()
                .uri("/user")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse user info", e);
        }
    }

    private JsonNode getRepositoryInfo(String accessToken, String repoFullName) {
        String response = githubWebClient.get()
                .uri("/repos/" + repoFullName)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get repository info", e);
        }
    }

    private String getLatestCommitSha(String accessToken, String repoFullName, String branch) {
        String response = githubWebClient.get()
                .uri("/repos/" + repoFullName + "/git/ref/heads/" + branch)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode ref = objectMapper.readTree(response);
            return ref.path("object").path("sha").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get latest commit SHA", e);
        }
    }

    private void createBranch(String accessToken, String repoFullName, String branchName, String baseSha) {
        githubWebClient.post()
                .uri("/repos/" + repoFullName + "/git/refs")
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(Map.of(
                        "ref", "refs/heads/" + branchName,
                        "sha", baseSha
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("Created branch {} in {}", branchName, repoFullName);
    }

    private void createOrUpdateFile(String accessToken, String repoFullName, String branch,
                                    String filePath, String content, String commitMessage) {
        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes());

        String existingSha = null;
        try {
            String existing = githubWebClient.get()
                    .uri("/repos/" + repoFullName + "/contents/" + filePath + "?ref=" + branch)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode existingFile = objectMapper.readTree(existing);
            existingSha = existingFile.path("sha").asText(null);
        } catch (Exception e) {
            // File doesn't exist, that's fine
        }

        Map<String, Object> body = new HashMap<>();
        body.put("message", commitMessage);
        body.put("content", encodedContent);
        body.put("branch", branch);
        if (existingSha != null) {
            body.put("sha", existingSha);
        }

        githubWebClient.put()
                .uri("/repos/" + repoFullName + "/contents/" + filePath)
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("Created/updated file {} in branch {}", filePath, branch);
    }

    private JsonNode createPullRequest(String accessToken, String repoFullName, String headBranch,
                                       String baseBranch, String title, String body) {
        String response = githubWebClient.post()
                .uri("/repos/" + repoFullName + "/pulls")
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(Map.of(
                        "title", title,
                        "body", body,
                        "head", headBranch,
                        "base", baseBranch
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse PR response", e);
        }
    }

    private String buildFixFileContent(AiAnalysisResponse analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("# LogHealer Auto-Fix\n\n");
        sb.append("## Root Cause\n");
        sb.append(analysis.getRootCause()).append("\n\n");
        sb.append("## Severity\n");
        sb.append("**").append(analysis.getSeverity()).append("**\n\n");
        sb.append("## Suggested Fix\n");
        sb.append(analysis.getSuggestedFix().getDescription()).append("\n\n");
        sb.append("### Code\n");
        sb.append("```").append(analysis.getSuggestedFix().getLanguage()).append("\n");
        sb.append(analysis.getSuggestedFix().getCodeSnippet()).append("\n");
        sb.append("```\n\n");

        if (analysis.getPreventionTips() != null && !analysis.getPreventionTips().isEmpty()) {
            sb.append("## Prevention Tips\n");
            for (String tip : analysis.getPreventionTips()) {
                sb.append("- ").append(tip).append("\n");
            }
        }

        sb.append("\n---\n");
        sb.append("*Generated by LogHealer AI (").append(analysis.getProvider())
                .append(" / ").append(analysis.getModel()).append(")*\n");

        return sb.toString();
    }

    private String buildPullRequestBody(AiAnalysisResponse analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🔧 LogHealer Auto-Fix\n\n");
        sb.append("This PR was automatically generated by LogHealer AI to fix a detected exception.\n\n");
        sb.append("### Root Cause\n");
        sb.append(analysis.getRootCause()).append("\n\n");
        sb.append("### Severity: ").append(getSeverityEmoji(analysis.getSeverity()))
                .append(" ").append(analysis.getSeverity()).append("\n\n");

        if (analysis.getSuggestedFix() != null) {
            sb.append("### Suggested Fix\n");
            sb.append(analysis.getSuggestedFix().getDescription()).append("\n\n");
            sb.append("**Confidence:** ").append(Math.round(analysis.getSuggestedFix().getConfidence() * 100))
                    .append("%\n\n");
        }

        if (analysis.getPreventionTips() != null && !analysis.getPreventionTips().isEmpty()) {
            sb.append("### Prevention Tips\n");
            for (String tip : analysis.getPreventionTips()) {
                sb.append("- ").append(tip).append("\n");
            }
            sb.append("\n");
        }

        sb.append("---\n");
        sb.append("*🤖 Generated by [LogHealer](https://loghealer.reddia-x.com) using ")
                .append(analysis.getProvider()).append(" / ").append(analysis.getModel()).append("*");

        return sb.toString();
    }

    private String getSeverityEmoji(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "🔴";
            case "HIGH" -> "🟠";
            case "MEDIUM" -> "🟡";
            case "LOW" -> "🟢";
            default -> "⚪";
        };
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    public List<Map<String, String>> listUserRepositories(String accessToken) {
        String response = githubWebClient.get()
                .uri("/user/repos?per_page=100&sort=updated")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode repos = objectMapper.readTree(response);
            List<Map<String, String>> result = new ArrayList<>();
            for (JsonNode repo : repos) {
                result.add(Map.of(
                        "fullName", repo.path("full_name").asText(),
                        "name", repo.path("name").asText(),
                        "private", String.valueOf(repo.path("private").asBoolean()),
                        "defaultBranch", repo.path("default_branch").asText("main")
                ));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list repositories", e);
        }
    }

    public Optional<GitHubConnection> getConnectionForProject(String projectId) {
        return connectionRepository.findByProjectIdAndIsActiveTrue(projectId);
    }
}
