package com.reddiax.loghealer.starter.aop;

import com.reddiax.loghealer.starter.LogHealerProperties;
import com.reddiax.loghealer.starter.filter.RequestTracingFilter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public class PerformanceMonitorAspect {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitorAspect.class);

    private final LogHealerProperties properties;

    public PerformanceMonitorAspect(LogHealerProperties properties) {
        this.properties = properties;
    }

    @Pointcut("@within(org.springframework.stereotype.Repository) || " +
              "@within(org.springframework.data.repository.Repository)")
    public void repositoryMethods() {}

    @Pointcut("execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..))")
    public void jpaRepositoryMethods() {}

    @Around("repositoryMethods() || jpaRepositoryMethods()")
    public Object monitorRepositoryMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.isEnabled() || !properties.getPerformance().isEnabled()) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();

        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            if (duration > properties.getPerformance().getSlowQueryThresholdMs()) {
                String traceId = RequestTracingFilter.getCurrentTraceId();
                log.warn("SLOW QUERY: {} took {}ms (threshold: {}ms) traceId={}",
                        methodName, duration,
                        properties.getPerformance().getSlowQueryThresholdMs(),
                        traceId);
            }
        }
    }
}
