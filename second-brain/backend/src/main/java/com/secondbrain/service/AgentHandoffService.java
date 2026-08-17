package com.secondbrain.service;

import com.secondbrain.common.entity.AgentHandoff;
import com.secondbrain.common.repository.AgentHandoffRepository;
import com.secondbrain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentHandoffService {

    private final AgentHandoffRepository agentHandoffRepository;

    @Transactional
    public AgentHandoff createHandoff(AgentHandoff handoff) {
        return agentHandoffRepository.save(handoff);
    }

    public java.util.List<AgentHandoff> getAll() {
        return agentHandoffRepository.findAllByOrderByCreatedAtDesc();
    }

    public AgentHandoff getLatestForRepository(UUID repositoryId) {
        return agentHandoffRepository.findFirstByRepositoryIdOrderByCreatedAtDesc(repositoryId)
                .orElse(null);
    }

    public AgentHandoff getBySession(UUID sessionId) {
        return agentHandoffRepository.findBySessionId(sessionId)
                .orElse(null);
    }
}
