package com.reddiax.loghealer.starter.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reddiax.loghealer.starter.LogHealerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class LogHealerClient {

    private static final Logger log = LoggerFactory.getLogger(LogHealerClient.class);

    private final LogHealerProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final BlockingQueue<Map<String, Object>> eventQueue;
    private final ScheduledExecutorService scheduler;

    public LogHealerClient(LogHealerProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.eventQueue = new LinkedBlockingQueue<>(10000);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "loghealer-sender");
            t.setDaemon(true);
            return t;
        });

        this.scheduler.scheduleAtFixedRate(this::flush, 5, 5, TimeUnit.SECONDS);
        log.info("LogHealer client initialized for project: {}", properties.getProjectId());
    }

    public void reportException(ExceptionEvent event) {
        if (!properties.isEnabled() || !properties.getException().isEnabled()) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "exception");
        payload.put("timestamp", Instant.now().toString());
        payload.put("projectId", properties.getProjectId());
        payload.put("environment", properties.getEnvironment());
        payload.put("traceId", event.getTraceId());
        payload.put("exceptionClass", event.getExceptionClass());
        payload.put("exceptionMessage", event.getExceptionMessage());
        payload.put("stackTrace", event.getStackTrace());
        payload.put("endpoint", event.getEndpoint());
        payload.put("method", event.getMethod());
        payload.put("statusCode", event.getStatusCode());
        payload.put("userId", event.getUserId());
        payload.put("requestHeaders", event.getRequestHeaders());
        payload.put("requestBody", event.getRequestBody());
        payload.put("requestParams", event.getRequestParams());
        payload.put("duration", event.getDurationMs());

        eventQueue.offer(payload);
    }

    public void reportSlowRequest(SlowRequestEvent event) {
        if (!properties.isEnabled() || !properties.getPerformance().isEnabled()) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "slow_request");
        payload.put("timestamp", Instant.now().toString());
        payload.put("projectId", properties.getProjectId());
        payload.put("environment", properties.getEnvironment());
        payload.put("traceId", event.getTraceId());
        payload.put("endpoint", event.getEndpoint());
        payload.put("method", event.getMethod());
        payload.put("durationMs", event.getDurationMs());
        payload.put("threshold", properties.getPerformance().getSlowRequestThresholdMs());
        payload.put("userId", event.getUserId());

        eventQueue.offer(payload);
    }

    public void reportEvent(String type, Map<String, Object> data) {
        if (!properties.isEnabled()) return;

        Map<String, Object> payload = new HashMap<>(data);
        payload.put("type", type);
        payload.put("timestamp", Instant.now().toString());
        payload.put("projectId", properties.getProjectId());
        payload.put("environment", properties.getEnvironment());

        eventQueue.offer(payload);
    }

    private void flush() {
        if (eventQueue.isEmpty()) return;

        List<Map<String, Object>> batch = new ArrayList<>();
        eventQueue.drainTo(batch, 100);

        if (batch.isEmpty()) return;

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("events", batch);

            String json = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getEndpoint() + "/events/batch"))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            log.warn("LogHealer API error: {} - {}", response.statusCode(), response.body());
                        }
                    })
                    .exceptionally(e -> {
                        log.warn("Failed to send events to LogHealer: {}", e.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            log.error("Failed to flush events to LogHealer", e);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        flush();
    }

    public static class ExceptionEvent {
        private String traceId;
        private String exceptionClass;
        private String exceptionMessage;
        private String stackTrace;
        private String endpoint;
        private String method;
        private int statusCode;
        private String userId;
        private Map<String, String> requestHeaders;
        private String requestBody;
        private Map<String, String> requestParams;
        private long durationMs;

        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getExceptionClass() { return exceptionClass; }
        public void setExceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; }
        public String getExceptionMessage() { return exceptionMessage; }
        public void setExceptionMessage(String exceptionMessage) { this.exceptionMessage = exceptionMessage; }
        public String getStackTrace() { return stackTrace; }
        public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public Map<String, String> getRequestHeaders() { return requestHeaders; }
        public void setRequestHeaders(Map<String, String> requestHeaders) { this.requestHeaders = requestHeaders; }
        public String getRequestBody() { return requestBody; }
        public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
        public Map<String, String> getRequestParams() { return requestParams; }
        public void setRequestParams(Map<String, String> requestParams) { this.requestParams = requestParams; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }

    public static class SlowRequestEvent {
        private String traceId;
        private String endpoint;
        private String method;
        private long durationMs;
        private String userId;

        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }
}
