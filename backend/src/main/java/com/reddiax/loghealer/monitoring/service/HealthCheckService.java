package com.reddiax.loghealer.monitoring.service;

import com.reddiax.loghealer.monitoring.entity.HealthCheck;
import com.reddiax.loghealer.monitoring.entity.MonitoredService;
import com.reddiax.loghealer.monitoring.entity.ServiceStatus;
import com.reddiax.loghealer.monitoring.repository.HealthCheckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthCheckService {

    private final WebClient webClient;
    private final HealthCheckRepository healthCheckRepository;

    @Transactional
    public HealthCheck performHealthCheck(MonitoredService service) {
        String url = service.getFullHealthUrl();
        log.debug("Performing health check for service: {} at {}", service.getName(), url);

        long startTime = System.currentTimeMillis();

        try {
            Integer statusCode = webClient.get()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .map(response -> response.getStatusCode().value())
                    .timeout(Duration.ofMillis(service.getTimeoutMs()))
                    .block();

            long responseTime = System.currentTimeMillis() - startTime;

            ServiceStatus status = determineStatus(statusCode, (int) responseTime, service.getTimeoutMs());

            HealthCheck healthCheck = HealthCheck.builder()
                    .service(service)
                    .status(status)
                    .responseTimeMs((int) responseTime)
                    .statusCode(statusCode)
                    .checkedAt(Instant.now())
                    .build();

            log.info("Health check completed for {}: status={}, responseTime={}ms, httpStatus={}",
                    service.getName(), status, responseTime, statusCode);

            return healthCheckRepository.save(healthCheck);

        } catch (WebClientResponseException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            
            HealthCheck healthCheck = HealthCheck.builder()
                    .service(service)
                    .status(ServiceStatus.DOWN)
                    .responseTimeMs((int) responseTime)
                    .statusCode(e.getStatusCode().value())
                    .errorMessage(e.getMessage())
                    .checkedAt(Instant.now())
                    .build();

            log.warn("Health check failed for {}: httpStatus={}, error={}",
                    service.getName(), e.getStatusCode().value(), e.getMessage());

            return healthCheckRepository.save(healthCheck);

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;

            HealthCheck healthCheck = HealthCheck.builder()
                    .service(service)
                    .status(ServiceStatus.DOWN)
                    .responseTimeMs((int) responseTime)
                    .errorMessage(e.getMessage())
                    .checkedAt(Instant.now())
                    .build();

            log.error("Health check error for {}: {}", service.getName(), e.getMessage());

            return healthCheckRepository.save(healthCheck);
        }
    }

    private ServiceStatus determineStatus(Integer statusCode, int responseTimeMs, int timeoutMs) {
        if (statusCode == null) {
            return ServiceStatus.DOWN;
        }

        if (statusCode >= 200 && statusCode < 300) {
            if (responseTimeMs > timeoutMs * 0.8) {
                return ServiceStatus.DEGRADED;
            }
            return ServiceStatus.UP;
        }

        if (statusCode >= 500) {
            return ServiceStatus.DOWN;
        }

        if (statusCode >= 400) {
            return ServiceStatus.DEGRADED;
        }

        return ServiceStatus.UNKNOWN;
    }
}
