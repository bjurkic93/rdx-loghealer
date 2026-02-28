package com.reddiax.loghealer.starter.exception;

import com.reddiax.loghealer.starter.LogHealerProperties;
import com.reddiax.loghealer.starter.client.LogHealerClient;
import com.reddiax.loghealer.starter.filter.RequestTracingFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LogHealerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LogHealerExceptionHandler.class);
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "x-api-key", "x-auth-token", "password"
    );

    private final LogHealerProperties properties;
    private final LogHealerClient client;

    public LogHealerExceptionHandler(LogHealerProperties properties, LogHealerClient client) {
        this.properties = properties;
        this.client = client;
    }

    @ExceptionHandler(Exception.class)
    public void handleException(Exception ex, HttpServletRequest request) throws Exception {
        reportException(ex, request);
        throw ex;
    }

    public void reportException(Throwable ex, HttpServletRequest request) {
        if (!properties.isEnabled() || !properties.getException().isEnabled()) return;

        try {
            LogHealerClient.ExceptionEvent event = new LogHealerClient.ExceptionEvent();

            event.setTraceId(RequestTracingFilter.getCurrentTraceId());
            event.setUserId(RequestTracingFilter.getCurrentUserId());

            event.setExceptionClass(ex.getClass().getName());
            event.setExceptionMessage(ex.getMessage());

            if (properties.getException().isIncludeStackTrace()) {
                event.setStackTrace(getStackTraceAsString(ex));
            }

            event.setEndpoint(request.getRequestURI());
            event.setMethod(request.getMethod());

            Long startTime = RequestTracingFilter.getRequestStartTime();
            if (startTime != null) {
                event.setDurationMs(System.currentTimeMillis() - startTime);
            }

            if (properties.getException().isIncludeHeaders()) {
                event.setRequestHeaders(extractHeaders(request));
            }

            if (properties.getException().isIncludeRequestBody()) {
                event.setRequestBody(extractRequestBody(request));
            }

            event.setRequestParams(extractParams(request));

            client.reportException(event);

            log.debug("Exception reported to LogHealer: {} - {} traceId={}",
                    ex.getClass().getSimpleName(), ex.getMessage(),
                    RequestTracingFilter.getCurrentTraceId());

        } catch (Exception e) {
            log.warn("Failed to report exception to LogHealer: {}", e.getMessage());
        }
    }

    private String getStackTraceAsString(Throwable ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (SENSITIVE_HEADERS.contains(name.toLowerCase())) {
                headers.put(name, "[REDACTED]");
            } else {
                headers.put(name, request.getHeader(name));
            }
        }
        return headers;
    }

    private String extractRequestBody(HttpServletRequest request) {
        try {
            if (request instanceof ContentCachingRequestWrapper wrapper) {
                byte[] content = wrapper.getContentAsByteArray();
                if (content.length > 0) {
                    String body = new String(content, StandardCharsets.UTF_8);
                    int maxLength = properties.getException().getMaxBodyLength();
                    if (body.length() > maxLength) {
                        return body.substring(0, maxLength) + "... [truncated]";
                    }
                    return maskSensitiveData(body);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        return request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.join(",", e.getValue())
                ));
    }

    private String maskSensitiveData(String body) {
        return body
                .replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"[REDACTED]\"")
                .replaceAll("\"secret\"\\s*:\\s*\"[^\"]*\"", "\"secret\":\"[REDACTED]\"")
                .replaceAll("\"apiKey\"\\s*:\\s*\"[^\"]*\"", "\"apiKey\":\"[REDACTED]\"")
                .replaceAll("\"token\"\\s*:\\s*\"[^\"]*\"", "\"token\":\"[REDACTED]\"");
    }
}
