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
public class CodeFixResponse {
    private String conversationId;
    private String status;
    private String message;
    
    private Analysis analysis;
    private List<FileChange> changes;
    private PullRequestInfo pullRequest;
    private List<ThinkingStep> thinkingSteps;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Analysis {
        private String rootCause;
        private String severity;
        private String explanation;
        private List<String> affectedFiles;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileChange {
        private String filePath;
        private String language;
        private String oldCode;
        private String newCode;
        private int startLine;
        private int endLine;
        private String changeDescription;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PullRequestInfo {
        private int prNumber;
        private String title;
        private String htmlUrl;
        private String branchName;
        private Instant createdAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThinkingStep {
        private String type; // "thinking", "tool_call", "tool_result", "analysis"
        private String content;
        private String toolName;
        private String toolInput;
        private String toolOutput;
        private Instant timestamp;
    }
}
