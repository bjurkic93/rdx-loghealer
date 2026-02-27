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
        // For now, we need to get the access token from the connection
        // This is a simplified implementation
        return ResponseEntity.ok(List.of());
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
