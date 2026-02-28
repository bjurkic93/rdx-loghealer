package com.reddiax.loghealer.monitoring.dto;

import com.reddiax.loghealer.monitoring.entity.AlertRuleType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleCreateDto {
    
    @NotNull(message = "Service ID is required")
    private Long serviceId;
    
    @NotBlank(message = "Name is required")
    private String name;
    
    @NotNull(message = "Rule type is required")
    private AlertRuleType ruleType;
    
    @NotNull(message = "Threshold value is required")
    @Min(value = 1, message = "Threshold must be at least 1")
    private Integer thresholdValue;
    
    @Min(value = 1, message = "Consecutive failures must be at least 1")
    private Integer consecutiveFailures = 1;
    
    @NotEmpty(message = "At least one email is required")
    private List<String> notifyEmails;
    
    @Min(value = 1, message = "Cooldown must be at least 1 minute")
    private Integer cooldownMinutes = 15;
}
