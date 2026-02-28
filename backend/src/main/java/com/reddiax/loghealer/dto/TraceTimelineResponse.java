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
public class TraceTimelineResponse {

    private String traceId;
    private long startTime;
    private long endTime;
    private long durationMs;
    private int totalEvents;
    private List<TraceEvent> events;
    private List<String> servicesInvolved;
    private boolean hasError;
    private String rootCauseService;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TraceEvent {
        private String id;
        private long timestamp;
        private String serviceName;
        private String projectId;
        private String level;
        private String message;
        private String logger;
        private String spanId;
        private String parentSpanId;
        private long durationMs;
        private boolean isError;
        private String exceptionType;
        private String stackTrace;
    }
}
