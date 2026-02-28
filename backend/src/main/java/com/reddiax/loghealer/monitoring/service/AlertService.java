package com.reddiax.loghealer.monitoring.service;

import com.reddiax.loghealer.monitoring.entity.*;
import com.reddiax.loghealer.monitoring.repository.AlertHistoryRepository;
import com.reddiax.loghealer.monitoring.repository.AlertRuleRepository;
import com.reddiax.loghealer.monitoring.repository.HealthCheckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final MonitoringEmailService emailService;

    @Transactional
    public void evaluateAlerts(MonitoredService service, HealthCheck healthCheck) {
        List<AlertRule> rules = alertRuleRepository.findByServiceIdAndIsActiveTrue(service.getId());

        for (AlertRule rule : rules) {
            evaluateRule(service, healthCheck, rule);
        }
    }

    private void evaluateRule(MonitoredService service, HealthCheck healthCheck, AlertRule rule) {
        boolean shouldTrigger = switch (rule.getRuleType()) {
            case DOWNTIME -> evaluateDowntimeRule(service, healthCheck, rule);
            case SLOW_RESPONSE -> evaluateSlowResponseRule(service, healthCheck, rule);
            case ERROR_RATE -> evaluateErrorRateRule(service, rule);
        };

        if (shouldTrigger) {
            triggerAlert(service, healthCheck, rule);
        } else if (healthCheck.getStatus() == ServiceStatus.UP) {
            resolveAlert(rule);
        }
    }

    private boolean evaluateDowntimeRule(MonitoredService service, HealthCheck healthCheck, AlertRule rule) {
        if (healthCheck.getStatus() == ServiceStatus.UP) {
            return false;
        }

        List<HealthCheck> recentChecks = healthCheckRepository.findRecentByServiceId(
                service.getId(), Pageable.ofSize(rule.getConsecutiveFailures()));

        if (recentChecks.size() < rule.getConsecutiveFailures()) {
            return false;
        }

        return recentChecks.stream()
                .allMatch(check -> check.getStatus() == ServiceStatus.DOWN || check.getStatus() == ServiceStatus.DEGRADED);
    }

    private boolean evaluateSlowResponseRule(MonitoredService service, HealthCheck healthCheck, AlertRule rule) {
        if (healthCheck.getResponseTimeMs() == null || healthCheck.getResponseTimeMs() < rule.getThresholdValue()) {
            return false;
        }

        List<HealthCheck> recentChecks = healthCheckRepository.findRecentByServiceId(
                service.getId(), Pageable.ofSize(rule.getConsecutiveFailures()));

        if (recentChecks.size() < rule.getConsecutiveFailures()) {
            return false;
        }

        return recentChecks.stream()
                .allMatch(check -> check.getResponseTimeMs() != null && check.getResponseTimeMs() >= rule.getThresholdValue());
    }

    private boolean evaluateErrorRateRule(MonitoredService service, AlertRule rule) {
        Instant since = Instant.now().minus(5, ChronoUnit.MINUTES);
        
        long totalChecks = healthCheckRepository.findByServiceIdAndCheckedAtAfter(service.getId(), since).size();
        if (totalChecks == 0) {
            return false;
        }

        long errorChecks = healthCheckRepository.countByServiceIdAndStatusAndCheckedAtAfter(
                service.getId(), ServiceStatus.DOWN, since);
        
        int errorRate = (int) ((errorChecks * 100) / totalChecks);
        return errorRate >= rule.getThresholdValue();
    }

    @Transactional
    public void triggerAlert(MonitoredService service, HealthCheck healthCheck, AlertRule rule) {
        Optional<AlertHistory> existingAlert = alertHistoryRepository.findActiveAlertByRuleId(rule.getId());

        if (existingAlert.isPresent()) {
            AlertHistory alert = existingAlert.get();
            Instant cooldownThreshold = alert.getTriggeredAt().plus(rule.getCooldownMinutes(), ChronoUnit.MINUTES);
            
            if (Instant.now().isBefore(cooldownThreshold)) {
                log.debug("Alert for rule {} is in cooldown period", rule.getName());
                return;
            }
        }

        String message = buildAlertMessage(service, healthCheck, rule);

        AlertHistory alert = AlertHistory.builder()
                .rule(rule)
                .service(service)
                .alertType(rule.getRuleType())
                .message(message)
                .triggeredAt(Instant.now())
                .build();

        alert = alertHistoryRepository.save(alert);

        log.warn("Alert triggered: {} - {}", rule.getName(), message);

        sendAlertNotification(alert, rule);
    }

    @Transactional
    public void resolveAlert(AlertRule rule) {
        Optional<AlertHistory> activeAlert = alertHistoryRepository.findActiveAlertByRuleId(rule.getId());
        
        if (activeAlert.isPresent()) {
            AlertHistory alert = activeAlert.get();
            alert.setResolvedAt(Instant.now());
            alertHistoryRepository.save(alert);
            
            log.info("Alert resolved: {}", rule.getName());

            sendResolutionNotification(alert, rule);
        }
    }

    private String buildAlertMessage(MonitoredService service, HealthCheck healthCheck, AlertRule rule) {
        return switch (rule.getRuleType()) {
            case DOWNTIME -> String.format(
                    "Service '%s' is DOWN. Status code: %s, Error: %s",
                    service.getName(),
                    healthCheck.getStatusCode() != null ? healthCheck.getStatusCode() : "N/A",
                    healthCheck.getErrorMessage() != null ? healthCheck.getErrorMessage() : "No response"
            );
            case SLOW_RESPONSE -> String.format(
                    "Service '%s' is responding slowly. Response time: %dms (threshold: %dms)",
                    service.getName(),
                    healthCheck.getResponseTimeMs(),
                    rule.getThresholdValue()
            );
            case ERROR_RATE -> String.format(
                    "Service '%s' has high error rate exceeding %d%%",
                    service.getName(),
                    rule.getThresholdValue()
            );
        };
    }

    private void sendAlertNotification(AlertHistory alert, AlertRule rule) {
        try {
            emailService.sendAlertEmail(alert, rule.getEmailList());
            alert.setNotificationSent(true);
            alert.setNotificationSentAt(Instant.now());
            alertHistoryRepository.save(alert);
        } catch (Exception e) {
            log.error("Failed to send alert notification: {}", e.getMessage());
        }
    }

    private void sendResolutionNotification(AlertHistory alert, AlertRule rule) {
        try {
            emailService.sendResolutionEmail(alert, rule.getEmailList());
        } catch (Exception e) {
            log.error("Failed to send resolution notification: {}", e.getMessage());
        }
    }

    @Transactional
    public void cleanupOldData() {
        Instant threshold = Instant.now().minus(30, ChronoUnit.DAYS);
        int deleted = healthCheckRepository.deleteOlderThan(threshold);
        log.info("Cleaned up {} old health check records", deleted);
    }
}
