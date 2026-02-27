package com.reddiax.loghealer.client;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class LogHealerAppender extends AppenderBase<ILoggingEvent> {

    private String endpoint = "https://loghealer.reddia-x.com/api/v1/logs/batch";
    private String apiKey;
    private String projectId;
    private String tenantId = "default";
    private String environment = "production";
    private String serviceName;
    private int batchSize = 50;
    private int flushIntervalMs = 5000;

    private final BlockingQueue<Map<String, Object>> buffer = new LinkedBlockingQueue<>(10000);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;
    private ScheduledExecutorService scheduler;
    private volatile boolean started = false;

    @Override
    public void start() {
        if (apiKey == null || apiKey.isEmpty()) {
            addWarn("LogHealer apiKey is not set, appender disabled");
            return;
        }
        if (projectId == null || projectId.isEmpty()) {
            addWarn("LogHealer projectId is not set, appender disabled");
            return;
        }

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "loghealer-flush");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        started = true;
        super.start();
        addInfo("LogHealer appender started for project: " + projectId);
    }

    @Override
    public void stop() {
        started = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        flush();
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!started) return;

        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
            logEntry.put("level", event.getLevel().toString());
            logEntry.put("logger", event.getLoggerName());
            logEntry.put("message", event.getFormattedMessage());
            logEntry.put("threadName", event.getThreadName());
            logEntry.put("projectId", projectId);
            logEntry.put("tenantId", tenantId);
            logEntry.put("environment", environment);
            logEntry.put("serviceName", serviceName != null ? serviceName : projectId);

            // MDC context
            if (event.getMDCPropertyMap() != null && !event.getMDCPropertyMap().isEmpty()) {
                logEntry.put("context", new HashMap<>(event.getMDCPropertyMap()));
            }

            // Exception handling
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy != null) {
                logEntry.put("exceptionClass", throwableProxy.getClassName());
                logEntry.put("exceptionMessage", throwableProxy.getMessage());
                logEntry.put("stackTrace", buildStackTrace(throwableProxy));
            }

            buffer.offer(logEntry);

            if (buffer.size() >= batchSize) {
                flush();
            }
        } catch (Exception e) {
            addError("Failed to append log to LogHealer buffer", e);
        }
    }

    private String buildStackTrace(IThrowableProxy throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClassName()).append(": ").append(throwable.getMessage()).append("\n");

        for (StackTraceElementProxy step : throwable.getStackTraceElementProxyArray()) {
            sb.append("\tat ").append(step.getSTEAsString()).append("\n");
        }

        if (throwable.getCause() != null) {
            sb.append("Caused by: ").append(buildStackTrace(throwable.getCause()));
        }

        return sb.toString();
    }

    private synchronized void flush() {
        if (buffer.isEmpty()) return;

        List<Map<String, Object>> batch = new ArrayList<>();
        buffer.drainTo(batch, batchSize * 2);

        if (batch.isEmpty()) return;

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("logs", batch);

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            addError("LogHealer API error: " + response.statusCode() + " - " + response.body());
                        }
                    })
                    .exceptionally(e -> {
                        addError("Failed to send logs to LogHealer", (Exception) e);
                        return null;
                    });

        } catch (Exception e) {
            addError("Failed to flush logs to LogHealer", e);
        }
    }

    // Setters for Logback XML configuration
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setFlushIntervalMs(int flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }
}
