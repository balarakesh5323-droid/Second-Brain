package com.secondbrain.common.repository;

import com.secondbrain.common.entity.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentSessionRepository extends JpaRepository<AgentSession, UUID> {

    List<AgentSession> findByAgentId(UUID agentId);

    List<AgentSession> findByAgentIdOrderByStartedAtDesc(UUID agentId);

    List<AgentSession> findByAgentIdAndProjectIdAndRepositoryIdOrderByStartedAtDesc(UUID agentId, UUID projectId, UUID repositoryId);

    List<AgentSession> findByAgentIdAndProjectIdOrderByStartedAtDesc(UUID agentId, UUID projectId);

    List<AgentSession> findByRepositoryId(UUID repositoryId);

    List<AgentSession> findByProjectId(UUID projectId);

    List<AgentSession> findTop10ByOrderByStartedAtDesc();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM AgentSession s WHERE s.id = :id")
    java.util.Optional<AgentSession> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM AgentSession s WHERE s.status = :status AND (:after IS NULL OR s.createdAt > :after OR (s.createdAt = :after AND s.id > :lastId)) ORDER BY s.createdAt ASC, s.id ASC")
    List<AgentSession> findIncrementalSessions(@org.springframework.data.repository.query.Param("status") String status,
                                               @org.springframework.data.repository.query.Param("after") java.time.LocalDateTime after,
                                               @org.springframework.data.repository.query.Param("lastId") UUID lastId,
                                               org.springframework.data.domain.Pageable pageable);
}
