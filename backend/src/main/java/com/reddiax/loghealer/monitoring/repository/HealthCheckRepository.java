package com.reddiax.loghealer.monitoring.repository;

import com.reddiax.loghealer.monitoring.entity.HealthCheck;
import com.reddiax.loghealer.monitoring.entity.ServiceStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface HealthCheckRepository extends JpaRepository<HealthCheck, Long> {

    @Query("SELECT h FROM HealthCheck h WHERE h.service.id = :serviceId ORDER BY h.checkedAt DESC")
    List<HealthCheck> findLatestByServiceId(@Param("serviceId") Long serviceId, Pageable pageable);

    default Optional<HealthCheck> findLatestByServiceId(Long serviceId) {
        List<HealthCheck> results = findLatestByServiceId(serviceId, Pageable.ofSize(1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Query("SELECT h FROM HealthCheck h WHERE h.service.id = :serviceId AND h.checkedAt >= :since ORDER BY h.checkedAt ASC")
    List<HealthCheck> findByServiceIdAndCheckedAtAfter(@Param("serviceId") Long serviceId, @Param("since") Instant since);

    @Query("SELECT h FROM HealthCheck h WHERE h.service.id = :serviceId ORDER BY h.checkedAt DESC")
    List<HealthCheck> findRecentByServiceId(@Param("serviceId") Long serviceId, Pageable pageable);

    @Query("SELECT COUNT(h) FROM HealthCheck h WHERE h.service.id = :serviceId AND h.status = :status AND h.checkedAt >= :since")
    long countByServiceIdAndStatusAndCheckedAtAfter(
            @Param("serviceId") Long serviceId,
            @Param("status") ServiceStatus status,
            @Param("since") Instant since);

    @Query("SELECT AVG(h.responseTimeMs) FROM HealthCheck h WHERE h.service.id = :serviceId AND h.checkedAt >= :since AND h.responseTimeMs IS NOT NULL")
    Double findAverageResponseTimeByServiceIdSince(@Param("serviceId") Long serviceId, @Param("since") Instant since);

    @Modifying
    @Query("DELETE FROM HealthCheck h WHERE h.checkedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
