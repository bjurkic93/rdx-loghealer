package com.reddiax.loghealer.starter.filter;

import com.reddiax.loghealer.starter.LogHealerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTracingFilter.class);

    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    public static final String ENDPOINT = "endpoint";
    public static final String METHOD = "method";
    public static final String START_TIME = "startTime";
    public static final String PROJECT_ID = "projectId";
    public static final String ENVIRONMENT = "environment";

    private final LogHealerProperties properties;

    public RequestTracingFilter(LogHealerProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String traceId = extractOrGenerateTraceId(request);
        long startTime = System.currentTimeMillis();

        MDC.put(TRACE_ID, traceId);
        MDC.put(ENDPOINT, request.getRequestURI());
        MDC.put(METHOD, request.getMethod());
        MDC.put(START_TIME, String.valueOf(startTime));
        MDC.put(PROJECT_ID, properties.getProjectId());
        MDC.put(ENVIRONMENT, properties.getEnvironment());

        String userId = extractUserId(request);
        if (userId != null) {
            MDC.put(USER_ID, userId);
        }

        response.setHeader(properties.getTracing().getTraceIdHeader(), traceId);

        try {
            filterChain.doFilter(request, response);

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            if (duration > properties.getPerformance().getSlowRequestThresholdMs()) {
                log.warn("SLOW_REQUEST: {} {} status={} duration={}ms threshold={}ms",
                        request.getMethod(), request.getRequestURI(), status, duration,
                        properties.getPerformance().getSlowRequestThresholdMs());
            } else if (properties.getTracing().isLogRequests()) {
                log.info("REQUEST: {} {} status={} duration={}ms",
                        request.getMethod(), request.getRequestURI(), status, duration);
            }

            MDC.clear();
        }
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") ||
               uri.startsWith("/health") ||
               uri.equals("/favicon.ico") ||
               uri.endsWith(".css") ||
               uri.endsWith(".js") ||
               uri.endsWith(".png") ||
               uri.endsWith(".jpg");
    }

    private String extractOrGenerateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(properties.getTracing().getTraceIdHeader());
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        return traceId;
    }

    private String extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                String[] parts = token.split("\\.");
                if (parts.length == 3) {
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    if (payload.contains("\"sub\"")) {
                        int start = payload.indexOf("\"sub\"") + 7;
                        int end = payload.indexOf("\"", start);
                        return payload.substring(start, end);
                    }
                }
            } catch (Exception ignored) {}
        }
        return request.getHeader("X-User-Id");
    }

    public static String getCurrentTraceId() {
        return MDC.get(TRACE_ID);
    }

    public static String getCurrentUserId() {
        return MDC.get(USER_ID);
    }

    public static Long getRequestStartTime() {
        String startTime = MDC.get(START_TIME);
        return startTime != null ? Long.parseLong(startTime) : null;
    }
}
