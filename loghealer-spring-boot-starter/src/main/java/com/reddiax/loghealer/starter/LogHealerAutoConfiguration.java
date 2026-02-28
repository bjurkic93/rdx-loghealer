package com.reddiax.loghealer.starter;

import com.reddiax.loghealer.starter.aop.PerformanceMonitorAspect;
import com.reddiax.loghealer.starter.client.LogHealerClient;
import com.reddiax.loghealer.starter.exception.LogHealerExceptionHandler;
import com.reddiax.loghealer.starter.filter.RequestTracingFilter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

    private LogHealerClient client;

    @Bean
    @ConditionalOnMissingBean
    public LogHealerClient logHealerClient(LogHealerProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("LogHealer API key not configured. Exception tracking disabled.");
            properties.setEnabled(false);
        }
        this.client = new LogHealerClient(properties);
        return client;
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "loghealer.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RequestTracingFilter requestTracingFilter(LogHealerProperties properties, LogHealerClient client) {
        log.info("LogHealer request tracing enabled for project: {}", properties.getProjectId());
        return new RequestTracingFilter(properties, client);
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "loghealer.exception", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LogHealerExceptionHandler logHealerExceptionHandler(LogHealerProperties properties, LogHealerClient client) {
        log.info("LogHealer exception tracking enabled");
        return new LogHealerExceptionHandler(properties, client);
    }

    @Bean
    @ConditionalOnProperty(prefix = "loghealer.performance", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PerformanceMonitorAspect performanceMonitorAspect(LogHealerProperties properties) {
        log.info("LogHealer performance monitoring enabled (slow query threshold: {}ms)",
                properties.getPerformance().getSlowQueryThresholdMs());
        return new PerformanceMonitorAspect(properties);
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }
}
