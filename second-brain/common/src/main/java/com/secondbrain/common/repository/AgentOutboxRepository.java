package com.secondbrain.common.repository;

import com.secondbrain.common.entity.AgentOutbox;
import com.secondbrain.common.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentOutboxRepository extends JpaRepository<AgentOutbox, UUID> {

    @Query(value = "SELECT * FROM agent_outbox WHERE (status = 'PENDING' OR (status = 'FAILED' AND retry_count < max_retries)) AND (next_retry_at IS NULL OR next_retry_at <= :now) ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<AgentOutbox> claimReadyForProcessing(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Query("SELECT o FROM AgentOutbox o WHERE o.status = :processing AND o.processingStartedAt <= :stuckThreshold")
    List<AgentOutbox> findStuckProcessing(@Param("processing") OutboxStatus processing, @Param("stuckThreshold") LocalDateTime stuckThreshold);

    boolean existsByIdempotencyKey(String idempotencyKey);

    java.util.Optional<AgentOutbox> findByIdempotencyKey(String idempotencyKey);

    List<AgentOutbox> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<AgentOutbox> findByStatus(OutboxStatus status);

    long countByStatus(OutboxStatus status);
}
