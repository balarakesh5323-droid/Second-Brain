package com.secondbrain.common.repository;

import com.secondbrain.common.entity.AgentSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentSessionRepository extends JpaRepository<AgentSession, UUID> {

    List<AgentSession> findByAgentId(UUID agentId);

    List<AgentSession> findByAgentIdOrderByStartedAtDesc(UUID agentId);

    List<AgentSession> findByAgentIdAndProjectIdAndRepositoryIdOrderByStartedAtDesc(UUID agentId, UUID projectId, UUID repositoryId);

    List<AgentSession> findByAgentIdAndProjectIdOrderByStartedAtDesc(UUID agentId, UUID projectId);

    List<AgentSession> findByRepositoryId(UUID repositoryId);

    List<AgentSession> findByRepositoryIdOrderByStartedAtDesc(UUID repositoryId, Pageable pageable);

    List<AgentSession> findByProjectId(UUID projectId);

    List<AgentSession> findByProjectIdOrderByStartedAtDesc(UUID projectId, Pageable pageable);

    List<AgentSession> findTop10ByOrderByStartedAtDesc();

    // Bounded task-aware cross-agent predecessor queries
    @Query("SELECT s FROM AgentSession s LEFT JOIN FETCH s.agent WHERE s.repository.id = :repoId AND s.agent.id != :agentId AND LOWER(s.task) LIKE LOWER(CONCAT('%', :taskKeyword, '%')) ORDER BY s.startedAt DESC")
    List<AgentSession> findMatchingPredecessorsByTask(@Param("repoId") UUID repoId,
                                                      @Param("agentId") UUID agentId,
                                                      @Param("taskKeyword") String taskKeyword,
                                                      Pageable pageable);

    @Query("SELECT s FROM AgentSession s LEFT JOIN FETCH s.agent WHERE s.repository.id = :repoId AND s.agent.id != :agentId ORDER BY s.startedAt DESC")
    List<AgentSession> findRecentPredecessorsByRepo(@Param("repoId") UUID repoId,
                                                   @Param("agentId") UUID agentId,
                                                   Pageable pageable);

    @Query("SELECT s FROM AgentSession s LEFT JOIN FETCH s.agent WHERE s.project.id = :projId AND s.agent.id != :agentId ORDER BY s.startedAt DESC")
    List<AgentSession> findRecentPredecessorsByProject(@Param("projId") UUID projId,
                                                      @Param("agentId") UUID agentId,
                                                      Pageable pageable);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AgentSession s WHERE s.id = :id")
    Optional<AgentSession> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT s FROM AgentSession s LEFT JOIN FETCH s.agent LEFT JOIN FETCH s.repository LEFT JOIN FETCH s.project WHERE s.status = :status AND (:after IS NULL OR s.createdAt > :after OR (s.createdAt = :after AND s.id > :lastId)) ORDER BY s.createdAt ASC, s.id ASC")
    List<AgentSession> findIncrementalSessions(@Param("status") String status,
                                                @Param("after") LocalDateTime after,
                                                @Param("lastId") UUID lastId,
                                                Pageable pageable);
}
