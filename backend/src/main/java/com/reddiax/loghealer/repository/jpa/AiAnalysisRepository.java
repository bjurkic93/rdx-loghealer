package com.reddiax.loghealer.repository.jpa;

import com.reddiax.loghealer.entity.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, UUID> {

    List<AiAnalysis> findByExceptionGroupIdOrderByCreatedAtDesc(String exceptionGroupId);

    List<AiAnalysis> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
