package com.secondbrain.service;

import com.secondbrain.common.dto.EventDto;
import com.secondbrain.common.entity.AgentEvent;
import com.secondbrain.common.entity.AgentSession;
import com.secondbrain.common.enums.EventType;
import com.secondbrain.common.repository.AgentEventRepository;
import com.secondbrain.common.repository.AgentSessionRepository;
import com.secondbrain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentEventService {

    private final AgentEventRepository agentEventRepository;
    private final AgentSessionRepository agentSessionRepository;

    @Transactional
    public AgentEvent recordEvent(EventDto dto) {
        AgentSession session = agentSessionRepository.findByIdForUpdate(dto.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("AgentSession", dto.getSessionId()));
        long nextSeq = (session.getEventSequence() != null ? session.getEventSequence() : 0L) + 1L;
        session.setEventSequence(nextSeq);
        agentSessionRepository.save(session);

        AgentEvent event = AgentEvent.builder()
                .session(session)
                .sequenceNumber(nextSeq)
                .eventType(dto.getEventType())
                .description(dto.getDescription())
                .filePath(dto.getFilePath())
                .details(dto.getDetails())
                .processingStatus(dto.getStatus() != null ? dto.getStatus() : "COMPLETED")
                .build();

        return agentEventRepository.save(event);
    }

    public List<AgentEvent> getBySession(UUID sessionId) {
        return agentEventRepository.findBySessionId(sessionId);
    }

    public List<AgentEvent> getRecent() {
        return agentEventRepository.findTop20ByOrderByCreatedAtDesc();
    }

    public List<AgentEvent> getByType(EventType type) {
        return agentEventRepository.findByEventType(type);
    }
}
