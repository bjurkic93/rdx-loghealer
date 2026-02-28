package com.reddiax.loghealer.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loghealer")
public class LogHealerProperties {

    private boolean enabled = true;
    private String projectId;
    private String environment = "production";

    private TracingConfig tracing = new TracingConfig();
    private PerformanceConfig performance = new PerformanceConfig();

    public static class TracingConfig {
        private boolean enabled = true;
        private String traceIdHeader = "X-Trace-Id";
        private boolean logRequests = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTraceIdHeader() { return traceIdHeader; }
        public void setTraceIdHeader(String traceIdHeader) { this.traceIdHeader = traceIdHeader; }
        public boolean isLogRequests() { return logRequests; }
        public void setLogRequests(boolean logRequests) { this.logRequests = logRequests; }
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
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public TracingConfig getTracing() { return tracing; }
    public void setTracing(TracingConfig tracing) { this.tracing = tracing; }
    public PerformanceConfig getPerformance() { return performance; }
    public void setPerformance(PerformanceConfig performance) { this.performance = performance; }
}
