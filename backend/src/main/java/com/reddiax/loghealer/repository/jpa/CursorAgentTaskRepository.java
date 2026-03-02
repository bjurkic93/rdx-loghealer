package com.reddiax.loghealer.repository.jpa;

import com.reddiax.loghealer.entity.CursorAgentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CursorAgentTaskRepository extends JpaRepository<CursorAgentTask, UUID> {
    
    Optional<CursorAgentTask> findByCursorAgentId(String cursorAgentId);
    
    List<CursorAgentTask> findByExceptionGroupId(String exceptionGroupId);
    
    List<CursorAgentTask> findByProjectId(UUID projectId);
    
    List<CursorAgentTask> findByStatus(String status);
}
