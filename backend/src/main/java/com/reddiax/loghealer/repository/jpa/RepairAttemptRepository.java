package com.reddiax.loghealer.repository.jpa;

import com.reddiax.loghealer.entity.RepairAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RepairAttemptRepository extends JpaRepository<RepairAttempt, UUID> {

    @Query("SELECT r FROM RepairAttempt r WHERE r.aiAnalysis.projectId = :projectId ORDER BY r.createdAt DESC")
    List<RepairAttempt> findByProjectId(@Param("projectId") UUID projectId);

    List<RepairAttempt> findByStatus(RepairAttempt.RepairStatus status);
}
