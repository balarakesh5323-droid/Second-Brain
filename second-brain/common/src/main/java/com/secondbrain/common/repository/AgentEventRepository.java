package com.secondbrain.common.repository;

import com.secondbrain.common.entity.AgentEvent;
import com.secondbrain.common.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentEventRepository extends JpaRepository<AgentEvent, UUID> {

    List<AgentEvent> findBySessionId(UUID sessionId);

    List<AgentEvent> findBySessionIdOrderBySequenceNumberAsc(UUID sessionId);

    java.util.Optional<AgentEvent> findTopBySessionIdOrderBySequenceNumberDesc(UUID sessionId);

    List<AgentEvent> findByEventType(EventType eventType);

    List<AgentEvent> findTop20ByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.Query("SELECT e FROM AgentEvent e WHERE e.session.repository.id = :repositoryId ORDER BY e.createdAt DESC LIMIT 20")
    List<AgentEvent> findTop20ByRepositoryIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("repositoryId") UUID repositoryId);

    @org.springframework.data.jpa.repository.Query("SELECT e FROM AgentEvent e WHERE e.session.project.id = :projectId ORDER BY e.createdAt DESC LIMIT 20")
    List<AgentEvent> findTop20ByProjectIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("projectId") UUID projectId);

    @org.springframework.data.jpa.repository.Query("SELECT e FROM AgentEvent e WHERE (:after IS NULL OR e.createdAt > :after OR (e.createdAt = :after AND e.id > :lastId)) ORDER BY e.createdAt ASC, e.id ASC")
    List<AgentEvent> findIncrementalEvents(@org.springframework.data.repository.query.Param("after") java.time.LocalDateTime after,
                                          @org.springframework.data.repository.query.Param("lastId") UUID lastId,
                                          org.springframework.data.domain.Pageable pageable);
}
