package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.dto.AiAnalysisResponse;
import com.reddiax.loghealer.dto.PullRequestResponse;
import com.reddiax.loghealer.entity.GitHubConnection;
import com.reddiax.loghealer.service.ai.AiAnalysisService;
import com.reddiax.loghealer.service.github.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
public class GitHubController {

    private final GitHubService gitHubService;
    private final AiAnalysisService aiAnalysisService;

    @GetMapping("/authorize/{projectId}")
    public ResponseEntity<Map<String, String>> getAuthorizationUrl(@PathVariable String projectId) {
        String authUrl = gitHubService.getAuthorizationUrl(projectId);
        return ResponseEntity.ok(Map.of("authorizationUrl", authUrl));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> handleOAuthCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state
    ) {
        try {
            String projectId = new String(Base64.getDecoder().decode(state));
            GitHubConnection connection = gitHubService.exchangeCodeForToken(code, projectId);
            
            log.info("GitHub OAuth successful for project {}, connection: {}", projectId, connection.getId());
            
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("https://loghealer.reddia-x.com/github/connect?connectionId=" + connection.getId()))
                    .build();
        } catch (Exception e) {
            log.error("GitHub OAuth callback failed", e);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("https://loghealer.reddia-x.com/github/error?message=" + e.getMessage()))
                    .build();
        }
    }

    @PostMapping("/connect/{connectionId}")
    public ResponseEntity<Map<String, Object>> connectRepository(
            @PathVariable String connectionId,
            @RequestBody Map<String, String> request
    ) {
        String repositoryFullName = request.get("repositoryFullName");
        if (repositoryFullName == null || repositoryFullName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "repositoryFullName is required"));
        }

        gitHubService.connectRepository(connectionId, repositoryFullName);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Repository connected successfully",
                "repository", repositoryFullName
        ));
    }

    @GetMapping("/repositories/{connectionId}")
    public ResponseEntity<List<Map<String, String>>> listRepositories(@PathVariable String connectionId) {
        return gitHubService.getConnectionById(connectionId)
                .map(connection -> {
                    List<Map<String, String>> repos = gitHubService.listUserRepositories(connection.getAccessToken());
                    return ResponseEntity.ok(repos);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/repositories")
    public ResponseEntity<List<Map<String, String>>> listAllRepositories() {
        return gitHubService.getAnyActiveConnection()
                .map(connection -> {
                    List<Map<String, String>> repos = gitHubService.listUserRepositories(connection.getAccessToken());
                    return ResponseEntity.ok(repos);
                })
                .orElse(ResponseEntity.ok(List.of()));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getGitHubStatus() {
        return gitHubService.getAnyActiveConnection()
                .map(conn -> ResponseEntity.ok(Map.<String, Object>of(
                        "connected", true,
                        "githubUsername", conn.getGithubUsername() != null ? conn.getGithubUsername() : "",
                        "connectionId", conn.getId()
                )))
                .orElse(ResponseEntity.ok(Map.of("connected", false)));
    }

    @GetMapping("/repo-info/{owner}/{repo}")
    public ResponseEntity<Map<String, Object>> getRepositoryProjectInfo(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(defaultValue = "main") String branch
    ) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching project info from pom.xml for {}", repoFullName);
        
        return gitHubService.getAnyActiveConnection()
                .map(connection -> {
                    try {
                        String pomContent = gitHubService.getFileContent(repoFullName, "pom.xml", branch);
                        if (pomContent == null || pomContent.isEmpty()) {
                            return ResponseEntity.ok(Map.<String, Object>of(
                                    "found", false,
                                    "error", "pom.xml not found"
                            ));
                        }
                        
                        Map<String, String> projectInfo = parsePomXml(pomContent);
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "found", true,
                                "artifactId", projectInfo.getOrDefault("artifactId", ""),
                                "groupId", projectInfo.getOrDefault("groupId", ""),
                                "name", projectInfo.getOrDefault("name", ""),
                                "description", projectInfo.getOrDefault("description", "")
                        ));
                    } catch (Exception e) {
                        log.error("Failed to fetch pom.xml from {}: {}", repoFullName, e.getMessage());
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "found", false,
                                "error", e.getMessage()
                        ));
                    }
                })
                .orElse(ResponseEntity.ok(Map.of("found", false, "error", "GitHub not connected")));
    }

    private Map<String, String> parsePomXml(String pomContent) {
        Map<String, String> result = new java.util.HashMap<>();
        
        // Simple XML parsing - extract top-level elements only (not from parent)
        result.put("artifactId", extractXmlElement(pomContent, "artifactId"));
        result.put("groupId", extractXmlElement(pomContent, "groupId"));
        result.put("name", extractXmlElement(pomContent, "name"));
        result.put("description", extractXmlElement(pomContent, "description"));
        
        return result;
    }

    private String extractXmlElement(String xml, String element) {
        // Find the element that's NOT inside <parent> block
        // First, remove the parent block
        String withoutParent = xml.replaceAll("(?s)<parent>.*?</parent>", "");
        
        // Now extract the element
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<" + element + ">([^<]*)</" + element + ">"
        );
        java.util.regex.Matcher matcher = pattern.matcher(withoutParent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    @GetMapping("/connection/{projectId}")
    public ResponseEntity<Map<String, Object>> getConnection(@PathVariable String projectId) {
        return gitHubService.getConnectionForProject(projectId)
                .map(conn -> ResponseEntity.ok(Map.<String, Object>of(
                        "connected", true,
                        "repositoryFullName", conn.getRepositoryFullName() != null ? conn.getRepositoryFullName() : "",
                        "githubUsername", conn.getGithubUsername() != null ? conn.getGithubUsername() : "",
                        "connectionId", conn.getId()
                )))
                .orElse(ResponseEntity.ok(Map.of("connected", false)));
    }

    @PostMapping("/create-pr/{exceptionGroupId}")
    public ResponseEntity<PullRequestResponse> createFixPullRequest(
            @PathVariable String exceptionGroupId,
            @RequestParam(required = false, defaultValue = "demo-project") String projectId,
            @RequestParam(required = false, defaultValue = "claude") String provider
    ) {
        log.info("Creating PR for exception {} in project {} using {}", exceptionGroupId, projectId, provider);

        AiAnalysisResponse analysis = aiAnalysisService.analyzeException(exceptionGroupId, provider, true);

        PullRequestResponse pr = gitHubService.createFixPullRequest(projectId, exceptionGroupId, analysis);

        log.info("Created PR #{} at {}", pr.getPrNumber(), pr.getHtmlUrl());
        return ResponseEntity.ok(pr);
    }

    @PostMapping("/analyze-and-pr/{exceptionGroupId}")
    public ResponseEntity<Map<String, Object>> analyzeAndCreatePr(
            @PathVariable String exceptionGroupId,
            @RequestParam(required = false, defaultValue = "demo-project") String projectId,
            @RequestParam(required = false, defaultValue = "claude") String provider
    ) {
        log.info("Full flow: Analyze and create PR for exception {}", exceptionGroupId);

        AiAnalysisResponse analysis = aiAnalysisService.analyzeException(exceptionGroupId, provider, true);

        PullRequestResponse pr = gitHubService.createFixPullRequest(projectId, exceptionGroupId, analysis);

        return ResponseEntity.ok(Map.of(
                "analysis", analysis,
                "pullRequest", pr
        ));
    }
}
