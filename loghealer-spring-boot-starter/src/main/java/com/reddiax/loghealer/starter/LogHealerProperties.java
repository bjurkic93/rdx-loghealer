package com.reddiax.loghealer.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loghealer")
public class LogHealerProperties {

    private boolean enabled = true;
    private String endpoint = "https://loghealer.reddia-x.com/api/v1";
    private String apiKey;
    private String projectId;
    private String environment = "production";

    private ExceptionConfig exception = new ExceptionConfig();
    private TracingConfig tracing = new TracingConfig();
    private PerformanceConfig performance = new PerformanceConfig();

    public static class ExceptionConfig {
        private boolean enabled = true;
        private boolean includeStackTrace = true;
        private boolean includeRequestBody = true;
        private boolean includeHeaders = true;
        private int maxBodyLength = 10000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isIncludeStackTrace() { return includeStackTrace; }
        public void setIncludeStackTrace(boolean includeStackTrace) { this.includeStackTrace = includeStackTrace; }
        public boolean isIncludeRequestBody() { return includeRequestBody; }
        public void setIncludeRequestBody(boolean includeRequestBody) { this.includeRequestBody = includeRequestBody; }
        public boolean isIncludeHeaders() { return includeHeaders; }
        public void setIncludeHeaders(boolean includeHeaders) { this.includeHeaders = includeHeaders; }
        public int getMaxBodyLength() { return maxBodyLength; }
        public void setMaxBodyLength(int maxBodyLength) { this.maxBodyLength = maxBodyLength; }
    }

    public static class TracingConfig {
        private boolean enabled = true;
        private String traceIdHeader = "X-Trace-Id";
        private boolean logRequests = true;
        private boolean logResponses = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTraceIdHeader() { return traceIdHeader; }
        public void setTraceIdHeader(String traceIdHeader) { this.traceIdHeader = traceIdHeader; }
        public boolean isLogRequests() { return logRequests; }
        public void setLogRequests(boolean logRequests) { this.logRequests = logRequests; }
        public boolean isLogResponses() { return logResponses; }
        public void setLogResponses(boolean logResponses) { this.logResponses = logResponses; }
    }

    public static class PerformanceConfig {
        private boolean enabled = true;
        private long slowRequestThresholdMs = 1000;
        private long slowQueryThresholdMs = 500;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getSlowRequestThresholdMs() { return slowRequestThresholdMs; }
        public void setSlowRequestThresholdMs(long slowRequestThresholdMs) { this.slowRequestThresholdMs = slowRequestThresholdMs; }
        public long getSlowQueryThresholdMs() { return slowQueryThresholdMs; }
        public void setSlowQueryThresholdMs(long slowQueryThresholdMs) { this.slowQueryThresholdMs = slowQueryThresholdMs; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public ExceptionConfig getException() { return exception; }
    public void setException(ExceptionConfig exception) { this.exception = exception; }
    public TracingConfig getTracing() { return tracing; }
    public void setTracing(TracingConfig tracing) { this.tracing = tracing; }
    public PerformanceConfig getPerformance() { return performance; }
    public void setPerformance(PerformanceConfig performance) { this.performance = performance; }
}
