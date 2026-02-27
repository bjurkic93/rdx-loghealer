package com.reddiax.loghealer.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsResponse {

    private long totalLogs;
    private long totalErrors;
    private long totalWarnings;
    private long totalExceptionGroups;
    private long newExceptions;
    private long resolvedExceptions;
    
    private List<LogLevelCount> logsByLevel;
    private List<TimeSeriesPoint> logsOverTime;
    private List<TopException> topExceptions;
    private List<ProjectStats> projectStats;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LogLevelCount {
        private String level;
        private long count;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TimeSeriesPoint {
        private String timestamp;
        private long count;
        private long errors;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopException {
        private String exceptionClass;
        private String message;
        private long count;
        private String lastSeen;
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProjectStats {
        private String projectId;
        private String projectName;
        private long logCount;
        private long errorCount;
    }
}
