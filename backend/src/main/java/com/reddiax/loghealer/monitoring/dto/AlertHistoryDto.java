package com.reddiax.loghealer.monitoring.dto;

import com.reddiax.loghealer.monitoring.entity.AlertRuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertHistoryDto {
    private Long id;
    private Long ruleId;
    private String ruleName;
    private Long serviceId;
    private String serviceName;
    private AlertRuleType alertType;
    private String message;
    private Instant triggeredAt;
    private Instant resolvedAt;
    private Boolean notificationSent;
    private Instant notificationSentAt;
    private Boolean isResolved;
}
