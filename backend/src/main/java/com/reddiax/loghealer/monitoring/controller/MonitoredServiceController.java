package com.reddiax.loghealer.monitoring.controller;

import com.reddiax.loghealer.monitoring.dto.HealthCheckDto;
import com.reddiax.loghealer.monitoring.dto.ServiceCreateDto;
import com.reddiax.loghealer.monitoring.dto.ServiceDto;
import com.reddiax.loghealer.monitoring.entity.HealthCheck;
import com.reddiax.loghealer.monitoring.entity.MonitoredService;
import com.reddiax.loghealer.monitoring.mapper.MonitoringMapper;
import com.reddiax.loghealer.monitoring.repository.HealthCheckRepository;
import com.reddiax.loghealer.monitoring.repository.MonitoredServiceRepository;
import com.reddiax.loghealer.monitoring.service.HealthCheckService;
import com.reddiax.loghealer.monitoring.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/monitoring/services")
@RequiredArgsConstructor
@Tag(name = "Monitored Services", description = "Monitored services management")
public class MonitoredServiceController {

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final HealthCheckService healthCheckService;
    private final MetricsService metricsService;
    private final MonitoringMapper mapper;

    @GetMapping
    @Operation(summary = "List all services", description = "Returns all monitored services with their current status")
    public ResponseEntity<List<ServiceDto>> getAllServices() {
        List<MonitoredService> services = serviceRepository.findAll();
        List<ServiceDto> dtos = services.stream()
                .map(metricsService::enrichServiceDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get service by ID", description = "Returns a specific service with its current status")
    public ResponseEntity<ServiceDto> getService(@PathVariable Long id) {
        return serviceRepository.findById(id)
                .map(metricsService::enrichServiceDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create new service", description = "Adds a new service to monitoring")
    public ResponseEntity<ServiceDto> createService(@Valid @RequestBody ServiceCreateDto dto) {
        if (serviceRepository.existsByUrlAndHealthEndpoint(dto.getUrl(), dto.getHealthEndpoint())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        MonitoredService service = mapper.toMonitoredService(dto);
        service = serviceRepository.save(service);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(metricsService.enrichServiceDto(service));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update service", description = "Updates an existing monitored service")
    public ResponseEntity<ServiceDto> updateService(@PathVariable Long id, @Valid @RequestBody ServiceCreateDto dto) {
        return serviceRepository.findById(id)
                .map(service -> {
                    mapper.updateMonitoredService(dto, service);
                    service = serviceRepository.save(service);
                    return ResponseEntity.ok(metricsService.enrichServiceDto(service));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete service", description = "Removes a service from monitoring")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        if (!serviceRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        serviceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/check")
    @Operation(summary = "Trigger health check", description = "Manually triggers a health check for a service")
    public ResponseEntity<HealthCheckDto> triggerHealthCheck(@PathVariable Long id) {
        return serviceRepository.findById(id)
                .map(service -> {
                    HealthCheck result = healthCheckService.performHealthCheck(service);
                    return ResponseEntity.ok(mapper.toHealthCheckDto(result));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get health check history", description = "Returns recent health checks for a service")
    public ResponseEntity<List<HealthCheckDto>> getHealthCheckHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "100") int limit) {
        
        if (!serviceRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        List<HealthCheck> checks = healthCheckRepository.findRecentByServiceId(id, Pageable.ofSize(limit));
        return ResponseEntity.ok(mapper.toHealthCheckDtoList(checks));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle service active status", description = "Enables or disables monitoring for a service")
    public ResponseEntity<ServiceDto> toggleService(@PathVariable Long id) {
        return serviceRepository.findById(id)
                .map(service -> {
                    service.setIsActive(!service.getIsActive());
                    service = serviceRepository.save(service);
                    return ResponseEntity.ok(metricsService.enrichServiceDto(service));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
