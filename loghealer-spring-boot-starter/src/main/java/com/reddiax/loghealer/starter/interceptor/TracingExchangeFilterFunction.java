package com.reddiax.loghealer.starter.interceptor;

import com.reddiax.loghealer.starter.LogHealerProperties;
import com.reddiax.loghealer.starter.filter.RequestTracingFilter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * ExchangeFilterFunction for WebClient that propagates traceId to downstream services.
 */
public class TracingExchangeFilterFunction implements ExchangeFilterFunction {

    private final LogHealerProperties properties;

    public TracingExchangeFilterFunction(LogHealerProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String traceId = RequestTracingFilter.getCurrentTraceId();
        String userId = RequestTracingFilter.getCurrentUserId();
        
        ClientRequest.Builder builder = ClientRequest.from(request);
        
        if (traceId != null) {
            builder.header(properties.getTracing().getTraceIdHeader(), traceId);
        }
        
        if (userId != null) {
            builder.header("X-User-Id", userId);
        }
        
        return next.exchange(builder.build());
    }
    
    /**
     * Create a filter function that can be used with WebClient.Builder.filter()
     */
    public static ExchangeFilterFunction create(LogHealerProperties properties) {
        return new TracingExchangeFilterFunction(properties);
    }
}
