package com.reddiax.loghealer.monitoring.controller;

import com.reddiax.loghealer.monitoring.dto.AlertHistoryDto;
import com.reddiax.loghealer.monitoring.dto.AlertRuleCreateDto;
import com.reddiax.loghealer.monitoring.dto.AlertRuleDto;
import com.reddiax.loghealer.monitoring.entity.AlertRule;
import com.reddiax.loghealer.monitoring.entity.MonitoredService;
import com.reddiax.loghealer.monitoring.mapper.MonitoringMapper;
import com.reddiax.loghealer.monitoring.repository.AlertHistoryRepository;
import com.reddiax.loghealer.monitoring.repository.AlertRuleRepository;
import com.reddiax.loghealer.monitoring.repository.MonitoredServiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/monitoring/alerts")
@RequiredArgsConstructor
@Tag(name = "Monitoring Alerts", description = "Alert rules and history management")
public class MonitoringAlertController {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final MonitoringMapper mapper;

    @GetMapping("/rules")
    @Operation(summary = "List all alert rules", description = "Returns all configured alert rules")
    public ResponseEntity<List<AlertRuleDto>> getAllRules() {
        List<AlertRule> rules = alertRuleRepository.findAllActiveWithService();
        return ResponseEntity.ok(mapper.toAlertRuleDtoList(rules));
    }

    @GetMapping("/rules/service/{serviceId}")
    @Operation(summary = "Get rules for service", description = "Returns alert rules for a specific service")
    public ResponseEntity<List<AlertRuleDto>> getRulesForService(@PathVariable Long serviceId) {
        if (!serviceRepository.existsById(serviceId)) {
            return ResponseEntity.notFound().build();
        }
        List<AlertRule> rules = alertRuleRepository.findByServiceIdAndIsActiveTrue(serviceId);
        return ResponseEntity.ok(mapper.toAlertRuleDtoList(rules));
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "Get alert rule by ID", description = "Returns a specific alert rule")
    public ResponseEntity<AlertRuleDto> getRule(@PathVariable Long id) {
        return alertRuleRepository.findById(id)
                .map(mapper::toAlertRuleDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/rules")
    @Operation(summary = "Create alert rule", description = "Creates a new alert rule")
    public ResponseEntity<AlertRuleDto> createRule(@Valid @RequestBody AlertRuleCreateDto dto) {
        MonitoredService service = serviceRepository.findById(dto.getServiceId())
                .orElse(null);
        
        if (service == null) {
            return ResponseEntity.badRequest().build();
        }

        AlertRule rule = mapper.toAlertRule(dto);
        rule.setService(service);
        rule = alertRuleRepository.save(rule);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toAlertRuleDto(rule));
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "Update alert rule", description = "Updates an existing alert rule")
    public ResponseEntity<AlertRuleDto> updateRule(@PathVariable Long id, @Valid @RequestBody AlertRuleCreateDto dto) {
        return alertRuleRepository.findById(id)
                .map(rule -> {
                    rule.setName(dto.getName());
                    rule.setRuleType(dto.getRuleType());
                    rule.setThresholdValue(dto.getThresholdValue());
                    rule.setConsecutiveFailures(dto.getConsecutiveFailures());
                    rule.setEmailList(dto.getNotifyEmails());
                    rule.setCooldownMinutes(dto.getCooldownMinutes());
                    rule = alertRuleRepository.save(rule);
                    return ResponseEntity.ok(mapper.toAlertRuleDto(rule));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "Delete alert rule", description = "Deletes an alert rule")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        if (!alertRuleRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        alertRuleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/rules/{id}/toggle")
    @Operation(summary = "Toggle alert rule", description = "Enables or disables an alert rule")
    public ResponseEntity<AlertRuleDto> toggleRule(@PathVariable Long id) {
        return alertRuleRepository.findById(id)
                .map(rule -> {
                    rule.setIsActive(!rule.getIsActive());
                    rule = alertRuleRepository.save(rule);
                    return ResponseEntity.ok(mapper.toAlertRuleDto(rule));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    @Operation(summary = "Get alert history", description = "Returns paginated alert history")
    public ResponseEntity<Page<AlertHistoryDto>> getAlertHistory(Pageable pageable) {
        Page<AlertHistoryDto> history = alertHistoryRepository.findAllOrderByTriggeredAtDesc(pageable)
                .map(mapper::toAlertHistoryDto);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/history/service/{serviceId}")
    @Operation(summary = "Get alert history for service", description = "Returns alert history for a specific service")
    public ResponseEntity<Page<AlertHistoryDto>> getAlertHistoryForService(
            @PathVariable Long serviceId,
            Pageable pageable) {
        
        if (!serviceRepository.existsById(serviceId)) {
            return ResponseEntity.notFound().build();
        }

        Page<AlertHistoryDto> history = alertHistoryRepository.findByServiceId(serviceId, pageable)
                .map(mapper::toAlertHistoryDto);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active alerts", description = "Returns all currently unresolved alerts")
    public ResponseEntity<List<AlertHistoryDto>> getActiveAlerts() {
        List<AlertHistoryDto> alerts = mapper.toAlertHistoryDtoList(
                alertHistoryRepository.findAllUnresolved());
        return ResponseEntity.ok(alerts);
    }
}
