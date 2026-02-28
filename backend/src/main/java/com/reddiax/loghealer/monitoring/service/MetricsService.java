package com.reddiax.loghealer.monitoring.service;

import com.reddiax.loghealer.monitoring.dto.*;
import com.reddiax.loghealer.monitoring.entity.HealthCheck;
import com.reddiax.loghealer.monitoring.entity.MonitoredService;
import com.reddiax.loghealer.monitoring.entity.ServiceStatus;
import com.reddiax.loghealer.monitoring.mapper.MonitoringMapper;
import com.reddiax.loghealer.monitoring.repository.AlertHistoryRepository;
import com.reddiax.loghealer.monitoring.repository.HealthCheckRepository;
import com.reddiax.loghealer.monitoring.repository.MonitoredServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MetricsService {

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final MonitoringMapper mapper;

    public DashboardSummaryDto getDashboardSummary() {
        List<MonitoredService> services = serviceRepository.findByIsActiveTrue();
        List<ServiceDto> serviceDtos = new ArrayList<>();

        int servicesUp = 0;
        int servicesDown = 0;
        int servicesDegraded = 0;

        for (MonitoredService service : services) {
            ServiceDto dto = enrichServiceDto(service);
            serviceDtos.add(dto);

            if (dto.getCurrentStatus() == ServiceStatus.UP) {
                servicesUp++;
            } else if (dto.getCurrentStatus() == ServiceStatus.DOWN) {
                servicesDown++;
            } else if (dto.getCurrentStatus() == ServiceStatus.DEGRADED) {
                servicesDegraded++;
            }
        }

        List<AlertHistoryDto> recentAlerts = mapper.toAlertHistoryDtoList(
                alertHistoryRepository.findAllUnresolved());

        Double overallUptime = calculateOverallUptime(services);

        return DashboardSummaryDto.builder()
                .totalServices(services.size())
                .servicesUp(servicesUp)
                .servicesDown(servicesDown)
                .servicesDegraded(servicesDegraded)
                .activeAlerts(recentAlerts.size())
                .overallUptime(overallUptime)
                .services(serviceDtos)
                .recentAlerts(recentAlerts)
                .build();
    }

    public ServiceDto enrichServiceDto(MonitoredService service) {
        ServiceDto dto = mapper.toServiceDto(service);
        
        Optional<HealthCheck> latestCheck = healthCheckRepository.findLatestByServiceId(service.getId());
        
        if (latestCheck.isPresent()) {
            HealthCheck check = latestCheck.get();
            dto.setCurrentStatus(check.getStatus());
            dto.setLastResponseTimeMs(check.getResponseTimeMs());
            dto.setLastCheckedAt(check.getCheckedAt());
        } else {
            dto.setCurrentStatus(ServiceStatus.UNKNOWN);
        }

        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);
        dto.setUptimePercentage(calculateUptimePercentage(service.getId(), since24h));
        dto.setAvgResponseTimeMs(healthCheckRepository.findAverageResponseTimeByServiceIdSince(service.getId(), since24h));

        return dto;
    }

    public MetricsDto getServiceMetrics(Long serviceId) {
        MonitoredService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service not found: " + serviceId));

        Instant now = Instant.now();
        Instant since24h = now.minus(24, ChronoUnit.HOURS);
        Instant since7d = now.minus(7, ChronoUnit.DAYS);
        Instant since30d = now.minus(30, ChronoUnit.DAYS);

        List<HealthCheck> checks24h = healthCheckRepository.findByServiceIdAndCheckedAtAfter(serviceId, since24h);
        List<HealthCheck> recentChecks = healthCheckRepository.findRecentByServiceId(serviceId, Pageable.ofSize(50));

        IntSummaryStatistics responseStats = checks24h.stream()
                .filter(c -> c.getResponseTimeMs() != null)
                .mapToInt(HealthCheck::getResponseTimeMs)
                .summaryStatistics();

        long failedChecks24h = checks24h.stream()
                .filter(c -> c.getStatus() == ServiceStatus.DOWN)
                .count();

        List<TimeSeriesDataPoint> responseTimeHistory = buildResponseTimeHistory(serviceId, since24h);
        List<TimeSeriesDataPoint> uptimeHistory = buildUptimeHistory(serviceId, since7d);

        return MetricsDto.builder()
                .serviceId(serviceId)
                .serviceName(service.getName())
                .uptimePercentage24h(calculateUptimePercentage(serviceId, since24h))
                .uptimePercentage7d(calculateUptimePercentage(serviceId, since7d))
                .uptimePercentage30d(calculateUptimePercentage(serviceId, since30d))
                .avgResponseTime24h(healthCheckRepository.findAverageResponseTimeByServiceIdSince(serviceId, since24h))
                .avgResponseTime7d(healthCheckRepository.findAverageResponseTimeByServiceIdSince(serviceId, since7d))
                .minResponseTime24h(responseStats.getCount() > 0 ? responseStats.getMin() : null)
                .maxResponseTime24h(responseStats.getCount() > 0 ? responseStats.getMax() : null)
                .totalChecks24h((long) checks24h.size())
                .failedChecks24h(failedChecks24h)
                .recentChecks(mapper.toHealthCheckDtoList(recentChecks))
                .responseTimeHistory(responseTimeHistory)
                .uptimeHistory(uptimeHistory)
                .build();
    }

    private Double calculateUptimePercentage(Long serviceId, Instant since) {
        List<HealthCheck> checks = healthCheckRepository.findByServiceIdAndCheckedAtAfter(serviceId, since);
        
        if (checks.isEmpty()) {
            return null;
        }

        long upChecks = checks.stream()
                .filter(c -> c.getStatus() == ServiceStatus.UP)
                .count();

        return (upChecks * 100.0) / checks.size();
    }

    private Double calculateOverallUptime(List<MonitoredService> services) {
        if (services.isEmpty()) {
            return null;
        }

        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);
        double totalUptime = 0;
        int serviceCount = 0;

        for (MonitoredService service : services) {
            Double uptime = calculateUptimePercentage(service.getId(), since24h);
            if (uptime != null) {
                totalUptime += uptime;
                serviceCount++;
            }
        }

        return serviceCount > 0 ? totalUptime / serviceCount : null;
    }

    private List<TimeSeriesDataPoint> buildResponseTimeHistory(Long serviceId, Instant since) {
        List<HealthCheck> checks = healthCheckRepository.findByServiceIdAndCheckedAtAfter(serviceId, since);
        
        return checks.stream()
                .filter(c -> c.getResponseTimeMs() != null)
                .map(c -> TimeSeriesDataPoint.builder()
                        .timestamp(c.getCheckedAt())
                        .value(c.getResponseTimeMs().doubleValue())
                        .build())
                .sorted(Comparator.comparing(TimeSeriesDataPoint::getTimestamp))
                .collect(Collectors.toList());
    }

    private List<TimeSeriesDataPoint> buildUptimeHistory(Long serviceId, Instant since) {
        List<HealthCheck> checks = healthCheckRepository.findByServiceIdAndCheckedAtAfter(serviceId, since);
        
        Map<Instant, List<HealthCheck>> groupedByHour = checks.stream()
                .collect(Collectors.groupingBy(c -> c.getCheckedAt().truncatedTo(ChronoUnit.HOURS)));

        return groupedByHour.entrySet().stream()
                .map(entry -> {
                    long upChecks = entry.getValue().stream()
                            .filter(c -> c.getStatus() == ServiceStatus.UP)
                            .count();
                    double uptime = (upChecks * 100.0) / entry.getValue().size();
                    return TimeSeriesDataPoint.builder()
                            .timestamp(entry.getKey())
                            .value(uptime)
                            .build();
                })
                .sorted(Comparator.comparing(TimeSeriesDataPoint::getTimestamp))
                .collect(Collectors.toList());
    }
}
