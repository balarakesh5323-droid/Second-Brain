package com.secondbrain.service;

import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.MemoryScope;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.common.repository.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentBridgeService {

    private final AgentRepository agentRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentEventRepository eventRepository;
    private final AgentAttemptRepository attemptRepository;
    private final AgentHandoffRepository handoffRepository;
    private final RepositoryEntityRepository repositoryRepository;
    private final ProjectRepository projectRepository;
    private final MemoryRepository memoryRepository;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final GraphService graphService;

    @Transactional
    public Map<String, Object> ingestActivity(ActivityPayload payload) {
        log.info("Ingesting autonomous agent activity from '{}' on action '{}'", payload.getAgentName(), payload.getActionType());

        // 1. Resolve or create Agent
        String agentName = (payload.getAgentName() != null && !payload.getAgentName().isBlank())
                ? payload.getAgentName() : "unknown-agent";
        Agent agent = agentRepository.findByName(agentName).orElseGet(() -> {
            Agent newAgent = Agent.builder()
                    .name(agentName)
                    .type(agentName.toLowerCase().contains("claude") ? "CLAUDE_CODE" :
                          agentName.toLowerCase().contains("codex") ? "CODEX" :
                          agentName.toLowerCase().contains("cursor") ? "CURSOR" : "CLI")
                    .build();
            return agentRepository.save(newAgent);
        });

        // 2. Resolve Repository & Project
        RepositoryEntity repo = resolveRepository(payload.getRepositoryId(), payload.getRepositoryPath());
        Project project = repo != null ? repo.getProject() : resolveProject(payload.getProjectId());

        // 3. Resolve or create active AgentSession
        AgentSession session = resolveOrCreateSession(agent, repo, project, payload.getSessionId());

        // 4. Create AgentEvent
        com.secondbrain.common.enums.EventType evtType = com.secondbrain.common.enums.EventType.COMMAND_EXECUTED;
        if (payload.getActionType() != null) {
            try {
                evtType = com.secondbrain.common.enums.EventType.valueOf(payload.getActionType().toUpperCase());
            } catch (Exception ignored) {}
        }

        AgentEvent event = AgentEvent.builder()
                .session(session)
                .eventType(evtType)
                .description(payload.getTaskDescription() != null ? payload.getTaskDescription() : payload.getActionType())
                .filePath(payload.getFilePath())
                .details(payload.getWorkingTreeDiff() != null ? payload.getWorkingTreeDiff() : payload.getNotes())
                .status(payload.getErrorMessage() != null ? "FAILED" : "COMPLETED")
                .build();
        event = eventRepository.save(event);

        // 5. Handle Attempt or Failure Tracking
        AgentAttempt attempt = null;
        if (isAttemptAction(payload)) {
            attempt = recordAttemptFromActivity(payload, agent, session, repo, project);
        }

        // 6. Vectorize significant activity or failure into Qdrant agent_memory
        if (payload.getErrorMessage() != null && !payload.getErrorMessage().isBlank()) {
            vectorizeFailureLesson(payload, agent, repo, project);
        }

        // 7. Update Graph Nodes in Neo4j
        try {
            graphService.batchCreateNodes("Agent", List.of(
                    Map.of("id", agent.getName(), "props", Map.of("name", agent.getName(), "type", agent.getType()))
            ));
            if (repo != null) {
                graphService.batchCreateRelationshipsTyped("WORKED_ON", List.of(
                        Map.of("fromId", agent.getName(), "toId", repo.getUrl() != null ? repo.getUrl() : repo.getName(),
                               "props", Map.of("lastAction", payload.getActionType(), "timestamp", LocalDateTime.now().toString()))
                ));
            }
        } catch (Exception ignored) {}

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("eventId", event.getId());
        result.put("sessionId", session.getId());
        if (attempt != null) {
            result.put("attemptId", attempt.getId());
        }
        return result;
    }

    @Transactional
    public AgentAttempt recordAttempt(AgentAttemptDto dto) {
        RepositoryEntity repo = resolveRepository(dto.getRepositoryId(), null);
        Project project = repo != null ? repo.getProject() : resolveProject(dto.getProjectId());
        AgentSession session = dto.getSessionId() != null ? sessionRepository.findById(dto.getSessionId()).orElse(null) : null;

        AgentAttempt attempt = AgentAttempt.builder()
                .agentName(dto.getAgentName() != null ? dto.getAgentName() : "unknown-agent")
                .taskDescription(dto.getTaskDescription() != null ? dto.getTaskDescription() : "Unnamed Task")
                .approach(dto.getApproach() != null ? dto.getApproach() : "No approach specified")
                .status(dto.getStatus() != null ? dto.getStatus().toUpperCase() : "IN_PROGRESS")
                .filesChanged(dto.getFilesChanged() != null ? dto.getFilesChanged() : new ArrayList<>())
                .commandsExecuted(dto.getCommandsExecuted() != null ? dto.getCommandsExecuted() : new ArrayList<>())
                .workingTreeDiff(dto.getWorkingTreeDiff())
                .errorMessage(dto.getErrorMessage())
                .lessonLearned(dto.getLessonLearned())
                .supersededBy(dto.getSupersededBy())
                .session(session)
                .repository(repo)
                .project(project)
                .tags(dto.getTags() != null ? dto.getTags() : new HashSet<>())
                .build();

        attempt = attemptRepository.save(attempt);

        // Synthesize declarative memory if lesson learned is provided
        if (dto.getLessonLearned() != null && !dto.getLessonLearned().isBlank()) {
            Memory memory = Memory.builder()
                    .content(String.format("Engineering Trial [%s]: For task '%s', approach '%s' resulted in %s. Lesson: %s",
                            dto.getAgentName(), dto.getTaskDescription(), dto.getApproach(), dto.getStatus(), dto.getLessonLearned()))
                    .type(MemoryType.DECLARATIVE)
                    .scope(repo != null ? MemoryScope.REPOSITORY : MemoryScope.GLOBAL)
                    .status(MemoryStatus.NEW)
                    .confidence(0.9)
                    .importance(0.85)
                    .provenanceSource("AGENT_EXPERIENCE")
                    .project(project)
                    .repository(repo)
                    .tags(Set.of("attempt", "trial", dto.getAgentName().toLowerCase()))
                    .build();
            memoryRepository.save(memory);
        }

        return attempt;
    }

    public List<AgentAttempt> getAttemptsByRepository(UUID repositoryId) {
        return attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repositoryId);
    }

    public List<AgentAttempt> getAllAttempts() {
        return attemptRepository.findAllOrderByCreatedAtDesc();
    }

    /**
     * One-shot continuity state retrieval for any incoming AI tool (Codex, Claude, Cursor).
     */
    public Map<String, Object> getContinuityState(String repoIdOrPath) {
        RepositoryEntity repo = resolveRepository(repoIdOrPath, repoIdOrPath);
        Map<String, Object> state = new HashMap<>();

        if (repo != null) {
            state.put("repository", Map.of(
                    "id", repo.getId(),
                    "name", repo.getName(),
                    "path", repo.getPath(),
                    "language", repo.getPrimaryLanguage() != null ? repo.getPrimaryLanguage() : "Unknown"
            ));

            // Latest handoff
            handoffRepository.findFirstByRepositoryIdOrderByCreatedAtDesc(repo.getId())
                    .ifPresent(h -> state.put("latestHandoff", h));

            // Recent attempts
            List<AgentAttempt> attempts = attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repo.getId());
            state.put("recentAttempts", attempts.stream().limit(5).toList());

            // Recent uncommitted / activity events
            List<AgentEvent> events = eventRepository.findTop20ByOrderByCreatedAtDesc();
            state.put("recentEvents", events.stream().limit(10).toList());
        }

        return state;
    }

    private boolean isAttemptAction(ActivityPayload payload) {
        String type = payload.getActionType() != null ? payload.getActionType().toUpperCase() : "";
        return type.contains("TEST") || type.contains("BUILD") || type.contains("ATTEMPT") ||
               type.contains("ERROR") || type.contains("DIFF") || payload.getErrorMessage() != null;
    }

    private AgentAttempt recordAttemptFromActivity(ActivityPayload payload, Agent agent, AgentSession session, RepositoryEntity repo, Project project) {
        String status = (payload.getErrorMessage() != null && !payload.getErrorMessage().isBlank()) ? "FAILURE" : "SUCCESS";
        String taskDesc = (payload.getTaskDescription() != null && !payload.getTaskDescription().isBlank())
                ? payload.getTaskDescription() : "Execution of " + payload.getActionType();
        String approach = (payload.getApproach() != null && !payload.getApproach().isBlank())
                ? payload.getApproach() : (payload.getCommand() != null ? payload.getCommand() : "Executed " + payload.getActionType());

        AgentAttempt attempt = AgentAttempt.builder()
                .agentName(agent.getName())
                .taskDescription(taskDesc)
                .approach(approach)
                .status(status)
                .filesChanged(payload.getFilesChanged() != null ? payload.getFilesChanged() : new ArrayList<>())
                .commandsExecuted(payload.getCommand() != null ? List.of(payload.getCommand()) : new ArrayList<>())
                .workingTreeDiff(payload.getWorkingTreeDiff())
                .errorMessage(payload.getErrorMessage())
                .lessonLearned(payload.getNotes())
                .session(session)
                .repository(repo)
                .project(project)
                .tags(Set.of("autonomous_capture", agent.getName().toLowerCase()))
                .build();

        return attemptRepository.save(attempt);
    }

    private void vectorizeFailureLesson(ActivityPayload payload, Agent agent, RepositoryEntity repo, Project project) {
        try {
            String summary = String.format("Agent '%s' encountered error during '%s':\nError: %s\nDiff/Notes: %s",
                    agent.getName(), payload.getActionType(), payload.getErrorMessage(),
                    payload.getNotes() != null ? payload.getNotes() : (payload.getWorkingTreeDiff() != null ? payload.getWorkingTreeDiff() : ""));

            float[] embedding = embeddingService.embed(summary);
            vectorStoreService.upsert("agent_memory", UUID.randomUUID().toString(), embedding,
                    Map.of(
                            "agent", agent.getName(),
                            "type", "failure_trial",
                            "action", payload.getActionType() != null ? payload.getActionType() : "",
                            "repository", repo != null ? repo.getName() : "",
                            "summary", summary.length() > 500 ? summary.substring(0, 500) : summary
                    ),
                    Map.of()
            );
        } catch (Exception e) {
            log.warn("Failed to vectorize agent failure (non-fatal): {}", e.getMessage());
        }
    }

    private Map<String, Object> buildEventDataMap(ActivityPayload payload) {
        Map<String, Object> map = new HashMap<>();
        if (payload.getCommand() != null) map.put("command", payload.getCommand());
        if (payload.getFilePath() != null) map.put("filePath", payload.getFilePath());
        if (payload.getFilesChanged() != null) map.put("filesChanged", payload.getFilesChanged());
        if (payload.getWorkingTreeDiff() != null) map.put("workingTreeDiff", payload.getWorkingTreeDiff());
        if (payload.getErrorMessage() != null) map.put("errorMessage", payload.getErrorMessage());
        if (payload.getNotes() != null) map.put("notes", payload.getNotes());
        if (payload.getTaskDescription() != null) map.put("taskDescription", payload.getTaskDescription());
        return map;
    }

    private AgentSession resolveOrCreateSession(Agent agent, RepositoryEntity repo, Project project, UUID sessionId) {
        if (sessionId != null) {
            var existing = sessionRepository.findById(sessionId);
            if (existing.isPresent()) return existing.get();
        }

        // Check if there is an active session in the last 2 hours
        LocalDateTime recent = LocalDateTime.now().minusHours(2);
        List<AgentSession> active = sessionRepository.findByAgentIdOrderByStartedAtDesc(agent.getId());
        for (AgentSession s : active) {
            if (s.getEndedAt() == null && s.getStartedAt().isAfter(recent)) {
                return s;
            }
        }

        // Create new active session
        AgentSession newSession = AgentSession.builder()
                .agent(agent)
                .project(project)
                .repository(repo)
                .startedAt(LocalDateTime.now())
                .build();
        return sessionRepository.save(newSession);
    }

    private RepositoryEntity resolveRepository(String repoId, String repoPath) {
        if (repoId != null && !repoId.isBlank()) {
            try {
                var found = repositoryRepository.findById(UUID.fromString(repoId));
                if (found.isPresent()) return found.get();
            } catch (Exception ignored) {}
            var byName = repositoryRepository.findByName(repoId);
            if (byName.isPresent()) return byName.get();
        }
        if (repoPath != null && !repoPath.isBlank()) {
            var byPath = repositoryRepository.findByPath(repoPath);
            if (byPath.isPresent()) return byPath.get();
        }
        return null;
    }

    private Project resolveProject(String projectId) {
        if (projectId != null && !projectId.isBlank()) {
            try {
                var found = projectRepository.findById(UUID.fromString(projectId));
                if (found.isPresent()) return found.get();
            } catch (Exception ignored) {}
            var byName = projectRepository.findByName(projectId);
            if (byName.isPresent()) return byName.get();
        }
        return null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityPayload {
        private String agentName; // "claude-code", "codex", "cursor"
        private String actionType; // "FILE_EDIT", "COMMAND_EXEC", "TEST_RUN", "ERROR", "UNCOMMITTED_DIFF"
        private String repositoryId;
        private String repositoryPath;
        private String projectId;
        private UUID sessionId;
        private String filePath;
        private List<String> filesChanged;
        private String command;
        private String workingTreeDiff;
        private String errorMessage;
        private String notes;
        private String taskDescription;
        private String approach;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentAttemptDto {
        private String agentName;
        private String taskDescription;
        private String approach;
        private String status;
        private List<String> filesChanged;
        private List<String> commandsExecuted;
        private String workingTreeDiff;
        private String errorMessage;
        private String lessonLearned;
        private UUID supersededBy;
        private UUID sessionId;
        private String repositoryId;
        private String projectId;
        private Set<String> tags;
    }
}
