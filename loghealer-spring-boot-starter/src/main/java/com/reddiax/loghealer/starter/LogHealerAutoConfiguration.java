package com.reddiax.loghealer.starter;

import com.reddiax.loghealer.starter.aop.PerformanceMonitorAspect;
import com.reddiax.loghealer.starter.filter.RequestTracingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@Configuration
@EnableConfigurationProperties(LogHealerProperties.class)
@ConditionalOnProperty(prefix = "loghealer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogHealerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LogHealerAutoConfiguration.class);

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "loghealer.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RequestTracingFilter requestTracingFilter(LogHealerProperties properties) {
        log.info("LogHealer request tracing enabled for project: {}", properties.getProjectId());
        return new RequestTracingFilter(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "loghealer.performance", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PerformanceMonitorAspect performanceMonitorAspect(LogHealerProperties properties) {
        log.info("LogHealer performance monitoring enabled (slow query threshold: {}ms)",
                properties.getPerformance().getSlowQueryThresholdMs());
        return new PerformanceMonitorAspect(properties);
    }
}
