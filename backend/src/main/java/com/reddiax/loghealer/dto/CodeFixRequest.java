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
public class CodeFixRequest {
    private String exceptionGroupId;
    private String projectId;
    private String provider;
    private String conversationId;
    private String userMessage;
}
