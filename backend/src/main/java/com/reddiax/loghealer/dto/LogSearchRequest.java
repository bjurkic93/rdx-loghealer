package com.reddiax.loghealer.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogSearchRequest {

    private String query;
    
    private List<String> levels;
    
    private String projectId;
    
    private String logger;
    
    private String exceptionClass;
    
    private String environment;
    
    private String traceId;
    
    private Instant fromTimestamp;
    
    private Instant toTimestamp;
    
    @Builder.Default
    private int page = 0;
    
    @Builder.Default
    private int size = 50;
    
    @Builder.Default
    private String sortBy = "timestamp";
    
    @Builder.Default
    private String sortOrder = "desc";
}
