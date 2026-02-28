package com.reddiax.loghealer.starter.interceptor;

import com.reddiax.loghealer.starter.LogHealerProperties;
import com.reddiax.loghealer.starter.filter.RequestTracingFilter;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Interceptor for RestTemplate that propagates traceId to downstream services.
 */
public class TracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private final LogHealerProperties properties;

    public TracingClientHttpRequestInterceptor(LogHealerProperties properties) {
        this.properties = properties;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) 
            throws IOException {
        
        String traceId = RequestTracingFilter.getCurrentTraceId();
        if (traceId != null) {
            request.getHeaders().add(properties.getTracing().getTraceIdHeader(), traceId);
        }
        
        String userId = RequestTracingFilter.getCurrentUserId();
        if (userId != null) {
            request.getHeaders().add("X-User-Id", userId);
        }
        
        return execution.execute(request, body);
    }
}
