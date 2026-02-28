package com.reddiax.loghealer.controller;

import com.reddiax.loghealer.dto.ServiceGroupRequest;
import com.reddiax.loghealer.dto.ServiceGroupResponse;
import com.reddiax.loghealer.service.ServiceGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-groups")
@RequiredArgsConstructor
@Slf4j
public class ServiceGroupController {

    private final ServiceGroupService serviceGroupService;

    @GetMapping
    public ResponseEntity<List<ServiceGroupResponse>> getAllServiceGroups() {
        log.info("Getting all service groups");
        return ResponseEntity.ok(serviceGroupService.getAllServiceGroups());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceGroupResponse> getServiceGroup(@PathVariable UUID id) {
        log.info("Getting service group: {}", id);
        return ResponseEntity.ok(serviceGroupService.getServiceGroup(id));
    }

    @PostMapping
    public ResponseEntity<ServiceGroupResponse> createServiceGroup(
            @Valid @RequestBody ServiceGroupRequest request) {
        log.info("Creating service group: {}", request.getName());
        ServiceGroupResponse response = serviceGroupService.createServiceGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceGroupResponse> updateServiceGroup(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceGroupRequest request) {
        log.info("Updating service group: {}", id);
        return ResponseEntity.ok(serviceGroupService.updateServiceGroup(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServiceGroup(@PathVariable UUID id) {
        log.info("Deleting service group: {}", id);
        serviceGroupService.deleteServiceGroup(id);
        return ResponseEntity.noContent().build();
    }
}
