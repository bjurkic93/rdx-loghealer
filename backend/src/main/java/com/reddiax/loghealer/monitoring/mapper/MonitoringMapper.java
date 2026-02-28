package com.reddiax.loghealer.monitoring.mapper;

import com.reddiax.loghealer.monitoring.dto.*;
import com.reddiax.loghealer.monitoring.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MonitoringMapper {

    ServiceDto toServiceDto(MonitoredService service);
    
    List<ServiceDto> toServiceDtoList(List<MonitoredService> services);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "healthChecks", ignore = true)
    @Mapping(target = "alertRules", ignore = true)
    MonitoredService toMonitoredService(ServiceCreateDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "healthChecks", ignore = true)
    @Mapping(target = "alertRules", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    void updateMonitoredService(ServiceCreateDto dto, @MappingTarget MonitoredService entity);

    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "serviceName", source = "service.name")
    HealthCheckDto toHealthCheckDto(HealthCheck healthCheck);
    
    List<HealthCheckDto> toHealthCheckDtoList(List<HealthCheck> healthChecks);

    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "serviceName", source = "service.name")
    @Mapping(target = "notifyEmails", expression = "java(mapEmailsToList(alertRule.getNotifyEmails()))")
    AlertRuleDto toAlertRuleDto(AlertRule alertRule);
    
    List<AlertRuleDto> toAlertRuleDtoList(List<AlertRule> alertRules);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "service", ignore = true)
    @Mapping(target = "notifyEmails", expression = "java(mapEmailsToString(dto.getNotifyEmails()))")
    AlertRule toAlertRule(AlertRuleCreateDto dto);

    @Mapping(target = "ruleId", source = "rule.id")
    @Mapping(target = "ruleName", source = "rule.name")
    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "serviceName", source = "service.name")
    @Mapping(target = "isResolved", expression = "java(alertHistory.getResolvedAt() != null)")
    AlertHistoryDto toAlertHistoryDto(AlertHistory alertHistory);
    
    List<AlertHistoryDto> toAlertHistoryDtoList(List<AlertHistory> alertHistories);

    default List<String> mapEmailsToList(String emails) {
        if (emails == null || emails.isBlank()) {
            return List.of();
        }
        return Arrays.asList(emails.split(","));
    }

    default String mapEmailsToString(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return "";
        }
        return String.join(",", emails);
    }
}
