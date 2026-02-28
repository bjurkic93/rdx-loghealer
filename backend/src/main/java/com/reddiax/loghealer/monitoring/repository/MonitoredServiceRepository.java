package com.reddiax.loghealer.monitoring.repository;

import com.reddiax.loghealer.monitoring.entity.MonitoredService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {

    List<MonitoredService> findByIsActiveTrue();

    @Query("SELECT s FROM MonitoredService s LEFT JOIN FETCH s.alertRules WHERE s.isActive = true")
    List<MonitoredService> findActiveServicesWithAlertRules();

    boolean existsByUrlAndHealthEndpoint(String url, String healthEndpoint);
}
