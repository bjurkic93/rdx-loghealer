package com.reddiax.loghealer.starter.filter;

import com.reddiax.loghealer.starter.LogHealerProperties;
import com.reddiax.loghealer.starter.client.LogHealerClient;
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
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

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

    private final LogHealerProperties properties;
    private final LogHealerClient client;

    public RequestTracingFilter(LogHealerProperties properties, LogHealerClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        String traceId = extractOrGenerateTraceId(request);
        long startTime = System.currentTimeMillis();

        MDC.put(TRACE_ID, traceId);
        MDC.put(ENDPOINT, request.getRequestURI());
        MDC.put(METHOD, request.getMethod());
        MDC.put(START_TIME, String.valueOf(startTime));

        String userId = extractUserId(request);
        if (userId != null) {
            MDC.put(USER_ID, userId);
        }

        response.setHeader(properties.getTracing().getTraceIdHeader(), traceId);

        try {
            if (properties.getTracing().isLogRequests()) {
                log.info(">>> {} {} traceId={}", request.getMethod(), request.getRequestURI(), traceId);
            }

            filterChain.doFilter(wrappedRequest, wrappedResponse);

        } finally {
            long duration = System.currentTimeMillis() - startTime;

            if (properties.getTracing().isLogResponses()) {
                log.info("<<< {} {} status={} duration={}ms traceId={}",
                        request.getMethod(), request.getRequestURI(),
                        wrappedResponse.getStatus(), duration, traceId);
            }

            if (duration > properties.getPerformance().getSlowRequestThresholdMs()) {
                LogHealerClient.SlowRequestEvent event = new LogHealerClient.SlowRequestEvent();
                event.setTraceId(traceId);
                event.setEndpoint(request.getRequestURI());
                event.setMethod(request.getMethod());
                event.setDurationMs(duration);
                event.setUserId(userId);
                client.reportSlowRequest(event);

                log.warn("SLOW REQUEST: {} {} took {}ms (threshold: {}ms) traceId={}",
                        request.getMethod(), request.getRequestURI(), duration,
                        properties.getPerformance().getSlowRequestThresholdMs(), traceId);
            }

            wrappedResponse.copyBodyToResponse();
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
