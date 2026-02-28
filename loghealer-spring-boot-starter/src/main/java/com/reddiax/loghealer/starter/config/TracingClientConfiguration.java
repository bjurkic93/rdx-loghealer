package com.reddiax.loghealer.starter.config;

import com.reddiax.loghealer.starter.LogHealerProperties;
import com.reddiax.loghealer.starter.interceptor.TracingClientHttpRequestInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration that automatically adds tracing interceptors to RestTemplate and WebClient beans.
 */
@Configuration
public class TracingClientConfiguration {

    private final LogHealerProperties properties;

    public TracingClientConfiguration(LogHealerProperties properties) {
        this.properties = properties;
    }

    @Bean
    public TracingClientHttpRequestInterceptor tracingClientHttpRequestInterceptor() {
        return new TracingClientHttpRequestInterceptor(properties);
    }

    /**
     * Bean post processor that automatically adds the tracing interceptor to all RestTemplate beans.
     */
    @Bean
    public BeanPostProcessor restTemplateTracingPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof RestTemplate restTemplate) {
                    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
                    
                    boolean hasTracingInterceptor = interceptors.stream()
                            .anyMatch(i -> i instanceof TracingClientHttpRequestInterceptor);
                    
                    if (!hasTracingInterceptor) {
                        interceptors.add(tracingClientHttpRequestInterceptor());
                        restTemplate.setInterceptors(interceptors);
                    }
                }
                return bean;
            }
        };
    }

    /**
     * Customizer for RestTemplateBuilder that adds tracing interceptor.
     */
    @Bean
    public RestTemplateBuilder tracingRestTemplateBuilder() {
        return new RestTemplateBuilder()
                .additionalInterceptors(tracingClientHttpRequestInterceptor());
    }
}
