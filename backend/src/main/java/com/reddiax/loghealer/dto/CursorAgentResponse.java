package com.reddiax.loghealer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursorAgentResponse {
    private String agentId;
    private String status;
    private String message;
    private String summary;
    private String prUrl;
    private String agentUrl;
    private String branchName;
    private List<ConversationMessage> conversation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMessage {
        private String id;
        private String type;
        private String text;
    }
}
