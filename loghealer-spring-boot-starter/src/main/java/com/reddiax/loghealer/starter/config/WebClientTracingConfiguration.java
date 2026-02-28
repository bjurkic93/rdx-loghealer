package com.reddiax.loghealer.starter.config;

import com.reddiax.loghealer.starter.LogHealerProperties;
import com.reddiax.loghealer.starter.interceptor.TracingExchangeFilterFunction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient tracing support.
 * Only loaded if WebClient is on the classpath.
 */
@Configuration
@ConditionalOnClass(WebClient.class)
public class WebClientTracingConfiguration {

    private final LogHealerProperties properties;

    public WebClientTracingConfiguration(LogHealerProperties properties) {
        this.properties = properties;
    }

    @Bean
    public TracingExchangeFilterFunction tracingExchangeFilterFunction() {
        return new TracingExchangeFilterFunction(properties);
    }

    /**
     * WebClient.Builder with tracing filter pre-configured.
     * Applications can inject this builder to create traced WebClient instances.
     */
    @Bean
    public WebClient.Builder tracingWebClientBuilder() {
        return WebClient.builder()
                .filter(tracingExchangeFilterFunction());
    }
}
