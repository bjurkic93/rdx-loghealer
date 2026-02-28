package com.reddiax.loghealer.monitoring.dto;

import com.reddiax.loghealer.monitoring.entity.AlertRuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleDto {
    private Long id;
    private Long serviceId;
    private String serviceName;
    private String name;
    private AlertRuleType ruleType;
    private Integer thresholdValue;
    private Integer consecutiveFailures;
    private List<String> notifyEmails;
    private Integer cooldownMinutes;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
