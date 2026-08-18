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

    @Query("SELECT o FROM AgentOutbox o WHERE (o.status = :pending OR o.status = :failed) AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now) ORDER BY o.createdAt ASC")
    List<AgentOutbox> findReadyToProcess(
            @Param("pending") OutboxStatus pending,
            @Param("failed") OutboxStatus failed,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    List<AgentOutbox> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<AgentOutbox> findByStatus(OutboxStatus status);

    long countByStatus(OutboxStatus status);
}
