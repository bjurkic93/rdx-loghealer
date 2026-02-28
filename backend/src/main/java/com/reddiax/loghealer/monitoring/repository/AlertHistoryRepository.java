package com.reddiax.loghealer.monitoring.repository;

import com.reddiax.loghealer.monitoring.entity.AlertHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {

    @Query("SELECT a FROM AlertHistory a WHERE a.rule.id = :ruleId AND a.resolvedAt IS NULL ORDER BY a.triggeredAt DESC")
    Optional<AlertHistory> findActiveAlertByRuleId(@Param("ruleId") Long ruleId);

    @Query("SELECT a FROM AlertHistory a WHERE a.service.id = :serviceId ORDER BY a.triggeredAt DESC")
    Page<AlertHistory> findByServiceId(@Param("serviceId") Long serviceId, Pageable pageable);

    @Query("SELECT a FROM AlertHistory a ORDER BY a.triggeredAt DESC")
    Page<AlertHistory> findAllOrderByTriggeredAtDesc(Pageable pageable);

    @Query("SELECT a FROM AlertHistory a WHERE a.resolvedAt IS NULL ORDER BY a.triggeredAt DESC")
    List<AlertHistory> findAllUnresolved();

    @Query("SELECT a FROM AlertHistory a WHERE a.rule.id = :ruleId AND a.triggeredAt >= :since ORDER BY a.triggeredAt DESC")
    List<AlertHistory> findByRuleIdAndTriggeredAtAfter(@Param("ruleId") Long ruleId, @Param("since") Instant since);

    @Query("SELECT COUNT(a) FROM AlertHistory a WHERE a.service.id = :serviceId AND a.triggeredAt >= :since")
    long countByServiceIdAndTriggeredAtAfter(@Param("serviceId") Long serviceId, @Param("since") Instant since);
}
