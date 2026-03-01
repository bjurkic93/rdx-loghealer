package com.reddiax.loghealer.repository.jpa;

import com.reddiax.loghealer.entity.FixConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FixConversationRepository extends JpaRepository<FixConversation, UUID> {

    List<FixConversation> findByExceptionGroupIdOrderByCreatedAtDesc(String exceptionGroupId);

    Optional<FixConversation> findByExceptionGroupIdAndStatus(String exceptionGroupId, FixConversation.ConversationStatus status);

    @Query("SELECT c FROM FixConversation c LEFT JOIN FETCH c.messages WHERE c.id = :id")
    Optional<FixConversation> findByIdWithMessages(@Param("id") UUID id);

    List<FixConversation> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
