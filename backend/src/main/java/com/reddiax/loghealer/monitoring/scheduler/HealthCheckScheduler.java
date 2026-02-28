package com.reddiax.loghealer.monitoring.scheduler;

import com.reddiax.loghealer.monitoring.entity.HealthCheck;
import com.reddiax.loghealer.monitoring.entity.MonitoredService;
import com.reddiax.loghealer.monitoring.repository.MonitoredServiceRepository;
import com.reddiax.loghealer.monitoring.service.AlertService;
import com.reddiax.loghealer.monitoring.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "loghealer.monitoring.enabled", havingValue = "true", matchIfMissing = false)
public class HealthCheckScheduler {

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckService healthCheckService;
    private final AlertService alertService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Scheduled(fixedRateString = "${loghealer.monitoring.default-check-interval-seconds:30}000")
    public void performScheduledHealthChecks() {
        log.debug("Starting scheduled health checks...");

        List<MonitoredService> activeServices = serviceRepository.findByIsActiveTrue();

        if (activeServices.isEmpty()) {
            log.debug("No active services to check");
            return;
        }

        log.info("Performing health checks for {} active services", activeServices.size());

        List<CompletableFuture<Void>> futures = activeServices.stream()
                .map(service -> CompletableFuture.runAsync(() -> {
                    try {
                        HealthCheck result = healthCheckService.performHealthCheck(service);
                        alertService.evaluateAlerts(service, result);
                    } catch (Exception e) {
                        log.error("Error during health check for service {}: {}", service.getName(), e.getMessage());
                    }
                }, executorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.debug("Completed scheduled health checks");
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldHealthChecks() {
        log.info("Starting cleanup of old health check data...");
        alertService.cleanupOldData();
    }
}
