package com.reddiax.loghealer.monitoring.repository;

import com.reddiax.loghealer.monitoring.entity.AlertRule;
import com.reddiax.loghealer.monitoring.entity.AlertRuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByServiceIdAndIsActiveTrue(Long serviceId);

    @Query("SELECT r FROM AlertRule r WHERE r.service.id = :serviceId AND r.ruleType = :ruleType AND r.isActive = true")
    List<AlertRule> findActiveRulesByServiceIdAndType(
            @Param("serviceId") Long serviceId,
            @Param("ruleType") AlertRuleType ruleType);

    @Query("SELECT r FROM AlertRule r JOIN FETCH r.service WHERE r.isActive = true")
    List<AlertRule> findAllActiveWithService();
}
