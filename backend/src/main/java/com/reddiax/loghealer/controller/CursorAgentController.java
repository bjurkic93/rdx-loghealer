package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.dto.CursorAgentResponse;
import com.reddiax.loghealer.entity.Project;
import com.reddiax.loghealer.repository.elasticsearch.ExceptionGroupRepository;
import com.reddiax.loghealer.repository.jpa.ProjectRepository;
import com.reddiax.loghealer.service.cursor.CursorAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cursor-agent")
@RequiredArgsConstructor
@Slf4j
public class CursorAgentController {

    private final CursorAgentService cursorAgentService;
    private final ExceptionGroupRepository exceptionGroupRepository;
    private final ProjectRepository projectRepository;

    @PostMapping("/launch")
    public ResponseEntity<CursorAgentResponse> launchAgent(
            @RequestBody Map<String, String> request) {
        
        String exceptionId = request.get("exceptionId");
        String projectId = request.get("projectId");

        log.info("Launching Cursor agent for exception: {}, project: {}", exceptionId, projectId);

        var exceptionOpt = exceptionGroupRepository.findById(exceptionId);
        if (exceptionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    CursorAgentResponse.builder()
                            .status("ERROR")
                            .message("Exception not found: " + exceptionId)
                            .build()
            );
        }

        Project project = null;
        if (projectId != null && !projectId.isEmpty()) {
            try {
                project = projectRepository.findById(UUID.fromString(projectId)).orElse(null);
            } catch (IllegalArgumentException e) {
                project = projectRepository.findByProjectKey(projectId).orElse(null);
            }
        }

        if (project == null) {
            String projectKey = exceptionOpt.get().getProjectId();
            if (projectKey != null) {
                project = projectRepository.findByProjectKey(projectKey).orElse(null);
            }
        }

        if (project == null) {
            return ResponseEntity.badRequest().body(
                    CursorAgentResponse.builder()
                            .status("ERROR")
                            .message("Project not found")
                            .build()
            );
        }

        CursorAgentResponse response = cursorAgentService.launchFixAgent(exceptionOpt.get(), project);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{agentId}/status")
    public ResponseEntity<CursorAgentResponse> getAgentStatus(@PathVariable String agentId) {
        CursorAgentResponse response = cursorAgentService.getAgentStatus(agentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{agentId}/conversation")
    public ResponseEntity<CursorAgentResponse> getAgentConversation(@PathVariable String agentId) {
        CursorAgentResponse response = cursorAgentService.getAgentConversation(agentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{agentId}/followup")
    public ResponseEntity<CursorAgentResponse> sendFollowUp(
            @PathVariable String agentId,
            @RequestBody Map<String, String> request) {
        
        String message = request.get("message");
        if (message == null || message.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    CursorAgentResponse.builder()
                            .status("ERROR")
                            .message("Message is required")
                            .build()
            );
        }

        CursorAgentResponse response = cursorAgentService.sendFollowUp(agentId, message);
        return ResponseEntity.ok(response);
    }
}
