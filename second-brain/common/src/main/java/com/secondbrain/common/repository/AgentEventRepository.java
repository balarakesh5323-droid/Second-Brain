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

    List<AgentEvent> findByEventType(EventType eventType);

    List<AgentEvent> findTop20ByOrderByCreatedAtDesc();
}
