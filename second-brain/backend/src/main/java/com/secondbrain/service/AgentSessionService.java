package com.secondbrain.service;

import com.secondbrain.common.entity.Agent;
import com.secondbrain.common.entity.AgentSession;
import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.repository.AgentRepository;
import com.secondbrain.common.repository.AgentSessionRepository;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.common.repository.RepositoryEntityRepository;
import com.secondbrain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private final AgentSessionRepository agentSessionRepository;
    private final AgentRepository agentRepository;
    private final ProjectRepository projectRepository;
    private final RepositoryEntityRepository repositoryEntityRepository;

    @Transactional
    public AgentSession startSession(UUID agentId, String task, UUID repositoryId, UUID projectId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", agentId));

        AgentSession.AgentSessionBuilder builder = AgentSession.builder()
                .agent(agent)
                .task(task)
                .status("active")
                .startedAt(LocalDateTime.now());

        if (repositoryId != null) {
            RepositoryEntity repository = repositoryEntityRepository.findById(repositoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Repository", repositoryId));
            builder.repository(repository);
        }

        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
            builder.project(project);
        }

        return agentSessionRepository.save(builder.build());
    }

    @Transactional
    public AgentSession endSession(UUID sessionId, String summary) {
        AgentSession session = getById(sessionId);
        session.setStatus("completed");
        session.setEndedAt(LocalDateTime.now());
        session.setSummary(summary);
        return agentSessionRepository.save(session);
    }

    public AgentSession getById(UUID id) {
        return agentSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AgentSession", id));
    }

    public List<AgentSession> getByAgent(UUID agentId) {
        return agentSessionRepository.findByAgentId(agentId);
    }

    public List<AgentSession> getRecent() {
        return agentSessionRepository.findTop10ByOrderByStartedAtDesc();
    }
}
