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
    private final DecisionRepository decisionRepository;
    private final TaskRepository taskRepository;
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
            var latestHandoff = handoffRepository.findFirstByRepositoryIdOrderByCreatedAtDesc(repo.getId());
            latestHandoff.ifPresent(h -> state.put("latestHandoff", h));

            // Recent attempts
            List<AgentAttempt> attempts = attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repo.getId());
            state.put("recentAttempts", attempts.stream().limit(5).toList());

            // Recent uncommitted / activity events
            List<AgentEvent> events = eventRepository.findTop20ByRepositoryIdOrderByCreatedAtDesc(repo.getId());
            state.put("recentEvents", events.stream().limit(10).toList());

            // Open tasks
            List<Task> openTasks = taskRepository.findByRepositoryIdAndStatus(repo.getId(), com.secondbrain.common.enums.TaskStatus.OPEN);
            state.put("openTasks", openTasks);

            // Recent decisions
            List<Decision> decisions = decisionRepository.findByRepositoryIdOrderByCreatedAtDesc(repo.getId());
            state.put("recentDecisions", decisions.stream().limit(5).toList());

            // Synthesize Natural Language Briefing for incoming agent
            StringBuilder briefing = new StringBuilder();
            briefing.append("=== AUTOMATIC CONTINUITY BRIEFING ===\n");
            briefing.append("Repository: ").append(repo.getName()).append(" (").append(repo.getPath()).append(")\n");
            if (!events.isEmpty()) {
                AgentEvent lastEvent = events.get(0);
                briefing.append("Last Active Event: [").append(lastEvent.getEventType()).append("] ")
                        .append(lastEvent.getDescription() != null ? lastEvent.getDescription() : "").append("\n");
            }
            if (!attempts.isEmpty()) {
                briefing.append("\nPrevious Engineering Trials:\n");
                for (AgentAttempt att : attempts.stream().limit(3).toList()) {
                    briefing.append(String.format("  • [%s] by %s: %s\n", att.getStatus(), att.getAgentName(), att.getTaskDescription()));
                    briefing.append("    Approach: ").append(att.getApproach()).append("\n");
                    if (att.getErrorMessage() != null && !att.getErrorMessage().isBlank()) {
                        briefing.append("    Error: ").append(att.getErrorMessage()).append("\n");
                    }
                    if (att.getLessonLearned() != null && !att.getLessonLearned().isBlank()) {
                        briefing.append("    Lesson: ").append(att.getLessonLearned()).append("\n");
                    }
                }
            }
            if (!openTasks.isEmpty()) {
                briefing.append("\nRemaining Open Tasks:\n");
                for (Task t : openTasks) {
                    briefing.append("  [ ] ").append(t.getTitle()).append("\n");
                }
            }
            state.put("structuredBriefing", briefing.toString());
        }

        return state;
    }

    /**
     * Master 1-shot workspace state gathering: projects, workspace files, recent attempts,
     * latest handoffs, decisions, open tasks, and a structured executive briefing.
     */
    public Map<String, Object> getWorkspaceState(String projectQuery, String repoQuery) {
        Project project = resolveProject(projectQuery);
        RepositoryEntity repo = resolveRepository(repoQuery, repoQuery);

        if (project == null && repo != null && repo.getProject() != null) {
            project = repo.getProject();
        }

        Map<String, Object> state = new LinkedHashMap<>();

        // 1. Project info
        if (project != null) {
            state.put("project", Map.of(
                    "id", project.getId().toString(),
                    "name", project.getName(),
                    "path", project.getPath() != null ? project.getPath() : "",
                    "status", project.getStatus() != null ? project.getStatus() : "active",
                    "description", project.getDescription() != null ? project.getDescription() : ""
            ));
        }

        // 2. Repository info
        if (repo != null) {
            state.put("repository", Map.of(
                    "id", repo.getId().toString(),
                    "name", repo.getName(),
                    "path", repo.getPath() != null ? repo.getPath() : "",
                    "language", repo.getPrimaryLanguage() != null ? repo.getPrimaryLanguage() : "Unknown"
            ));
        }

        // 3. Deep Scan workspace files with accurate metadata (Repository path prioritized over project path)
        String targetPath = (repo != null && repo.getPath() != null && !repo.getPath().isBlank())
                ? repo.getPath()
                : (project != null && project.getPath() != null ? project.getPath() : null);

        List<String> workspaceFiles = new ArrayList<>();
        int totalFilesCount = 0;
        int maxDepth = 5;
        int sampleLimit = 40;

        if (targetPath != null) {
            java.nio.file.Path p = java.nio.file.Paths.get(targetPath);
            if (java.nio.file.Files.exists(p)) {
                try (var stream = java.nio.file.Files.walk(p, maxDepth)) {
                    List<java.nio.file.Path> allFiles = stream
                            .filter(java.nio.file.Files::isRegularFile)
                            .filter(f -> {
                                String pathStr = f.toString();
                                return !pathStr.contains("/.git/") &&
                                       !pathStr.contains("/node_modules/") &&
                                       !pathStr.contains("/target/") &&
                                       !pathStr.contains("/build/") &&
                                       !pathStr.contains("/.gradle/") &&
                                       !pathStr.contains("/__pycache__/");
                            })
                            .toList();

                    totalFilesCount = allFiles.size();

                    // Prioritize architecture-critical configuration, source, and infra files
                    allFiles.stream()
                            .sorted((a, b) -> {
                                int scoreA = scoreFileImportance(a.toString());
                                int scoreB = scoreFileImportance(b.toString());
                                if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
                                return a.toString().compareToIgnoreCase(b.toString());
                            })
                            .limit(sampleLimit)
                            .forEach(f -> workspaceFiles.add(p.relativize(f).toString()));
                } catch (Exception ignored) {}
            }
        }

        Map<String, Object> workspaceMeta = new LinkedHashMap<>();
        workspaceMeta.put("totalFilesCount", totalFilesCount);
        workspaceMeta.put("sampled", totalFilesCount > sampleLimit);
        workspaceMeta.put("sampleLimit", sampleLimit);
        workspaceMeta.put("maxDepth", maxDepth);
        workspaceMeta.put("files", workspaceFiles);
        state.put("workspace", workspaceMeta);
        state.put("workspaceFiles", workspaceFiles);

        // 4. Latest handoff (strictly ordered by recency)
        Optional<AgentHandoff> latestHandoff = Optional.empty();
        if (repo != null) {
            latestHandoff = handoffRepository.findFirstByRepositoryIdOrderByCreatedAtDesc(repo.getId());
        }
        if (latestHandoff.isEmpty()) {
            latestHandoff = handoffRepository.findFirstByOrderByCreatedAtDesc();
        }
        latestHandoff.ifPresent(h -> state.put("latestHandoff", Map.of(
                "task", h.getTask() != null ? h.getTask() : "",
                "completedItems", h.getCompletedItems() != null ? h.getCompletedItems() : "",
                "inProgressItems", h.getInProgressItems() != null ? h.getInProgressItems() : "",
                "blockedItems", h.getBlockedItems() != null ? h.getBlockedItems() : "",
                "nextSteps", h.getNextSteps() != null ? h.getNextSteps() : "",
                "changedFiles", h.getChangedFiles() != null ? h.getChangedFiles() : ""
        )));

        // 5. Recent attempts
        List<AgentAttempt> attempts = (project != null)
                ? attemptRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
                : (repo != null ? attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repo.getId()) : attemptRepository.findAllOrderByCreatedAtDesc());
        state.put("recentAttempts", attempts.stream().limit(6).map(a -> Map.of(
                "agent", a.getAgentName() != null ? a.getAgentName() : "agent",
                "task", a.getTaskDescription() != null ? a.getTaskDescription() : "",
                "approach", a.getApproach() != null ? a.getApproach() : "",
                "status", a.getStatus() != null ? a.getStatus() : "",
                "error", a.getErrorMessage() != null ? a.getErrorMessage() : "none",
                "lessonLearned", a.getLessonLearned() != null ? a.getLessonLearned() : "N/A"
        )).toList());

        // 6. Active decisions
        List<Decision> decisions = (project != null)
                ? decisionRepository.findByProjectId(project.getId())
                : decisionRepository.findAll();
        state.put("activeDecisions", decisions.stream().limit(6).map(d -> Map.of(
                "title", d.getTitle(),
                "rationale", d.getRationale() != null ? d.getRationale() : ""
        )).toList());

        // 7. Open tasks
        List<Task> openTasks = (project != null)
                ? taskRepository.findByProjectIdAndStatus(project.getId(), com.secondbrain.common.enums.TaskStatus.OPEN)
                : taskRepository.findByStatus(com.secondbrain.common.enums.TaskStatus.OPEN);
        state.put("openTasks", openTasks.stream().limit(6).map(Task::getTitle).toList());

        // 8. One-shot textual briefing summary
        StringBuilder sb = new StringBuilder();
        sb.append("=== 🧠 SECOND BRAIN MASTER WORKSPACE STATE ===\n\n");
        if (project != null) {
            sb.append(String.format("📁 Active Project: %s (Path: %s)\n", project.getName(), project.getPath()));
        }
        if (repo != null) {
            sb.append(String.format("📦 Repository: %s (%s)\n", repo.getName(), repo.getPrimaryLanguage()));
        }
        sb.append(String.format("📂 Workspace: %d files tracked (Showing %d sampled)\n", totalFilesCount, workspaceFiles.size()));

        if (latestHandoff.isPresent()) {
            var h = latestHandoff.get();
            sb.append(String.format("\n📋 Latest Handoff Task: %s\n", h.getTask()));
            if (h.getCompletedItems() != null) sb.append(String.format("   • Completed: %s\n", h.getCompletedItems()));
            if (h.getInProgressItems() != null) sb.append(String.format("   • In Progress: %s\n", h.getInProgressItems()));
            if (h.getBlockedItems() != null) sb.append(String.format("   • Blocked: %s\n", h.getBlockedItems()));
            if (h.getNextSteps() != null) sb.append(String.format("   • Next Steps: %s\n", h.getNextSteps()));
        }
        if (!attempts.isEmpty()) {
            sb.append("\n🔬 Recent Engineering Trials (Failures & Successes):\n");
            for (AgentAttempt a : attempts.stream().limit(4).toList()) {
                sb.append(String.format("   • [%s] %s: %s (Lesson: %s)\n",
                        a.getStatus(), a.getAgentName(), a.getApproach(), a.getLessonLearned() != null ? a.getLessonLearned() : "none"));
            }
        }
        if (!decisions.isEmpty()) {
            sb.append("\n⚖️ Active Architectural Decisions:\n");
            for (Decision d : decisions.stream().limit(4).toList()) {
                sb.append(String.format("   • %s: %s\n", d.getTitle(), d.getRationale() != null ? d.getRationale() : ""));
            }
        }
        if (!openTasks.isEmpty()) {
            sb.append("\n📌 Remaining Open Tasks:\n");
            for (Task t : openTasks.stream().limit(6).toList()) {
                sb.append(String.format("   [ ] %s\n", t.getTitle()));
            }
        }
        state.put("briefing", sb.toString());

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

        // Check if there is an active session in the last 2 hours for this specific project / repository
        LocalDateTime recent = LocalDateTime.now().minusHours(2);
        List<AgentSession> active = (repo != null && project != null)
                ? sessionRepository.findByAgentIdAndProjectIdAndRepositoryIdOrderByStartedAtDesc(agent.getId(), project.getId(), repo.getId())
                : (project != null
                    ? sessionRepository.findByAgentIdAndProjectIdOrderByStartedAtDesc(agent.getId(), project.getId())
                    : sessionRepository.findByAgentIdOrderByStartedAtDesc(agent.getId()));

        for (AgentSession s : active) {
            if (s.getEndedAt() == null && s.getStartedAt().isAfter(recent)) {
                return s;
            }
        }

        // Create new active scoped session
        AgentSession newSession = AgentSession.builder()
                .agent(agent)
                .project(project)
                .repository(repo)
                .startedAt(LocalDateTime.now())
                .status("active")
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

    private int scoreFileImportance(String pathStr) {
        String lower = pathStr.toLowerCase();
        // 1. Core build & runtime configurations
        if (lower.endsWith("pom.xml") || lower.endsWith("build.gradle") || lower.endsWith("build.gradle.kts") ||
            lower.endsWith("package.json") || lower.endsWith("tsconfig.json") || lower.endsWith("dockerfile") ||
            lower.endsWith("docker-compose.yml") || lower.endsWith("docker-compose.yaml")) {
            return 100;
        }
        // 2. Spring & Application configs & migrations
        if (lower.endsWith("application.yml") || lower.endsWith("application.yaml") || lower.endsWith("application.properties") ||
            lower.contains("/application-") || lower.endsWith("schema.sql") || lower.endsWith("data.sql") || lower.endsWith(".sql")) {
            return 90;
        }
        // 3. Kubernetes / Helm / Cloud manifests
        if (lower.contains("/k8s/") || lower.contains("/helm/") || lower.contains("/argocd/") || lower.contains("/docker/")) {
            return 80;
        }
        // 4. Primary source files
        if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".ts") || lower.endsWith(".js") || lower.endsWith(".go") || lower.endsWith(".rs")) {
            return 70;
        }
        // 5. Documentation & web UI
        if (lower.endsWith("readme.md") || lower.endsWith(".md") || lower.endsWith(".html")) {
            return 50;
        }
        return 10;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FullSessionPayload {
        private String agentName;
        private String agentType; // "CLAUDE_CODE", "CODEX", "CURSOR", "CLI"
        private String repositoryIdOrPath;
        private String projectId;
        private String branch;
        private String headCommit;
        private String taskSummary;
        private String status; // "IN_PROGRESS", "COMPLETED", "FAILED"
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private List<String> touchedFiles;
        private List<Map<String, Object>> problems;
        private List<Map<String, Object>> decisions;
        private List<Map<String, Object>> failedAttempts;
        private List<Map<String, Object>> commits;
        private Map<String, Object> handoff;
    }

    @Transactional
    public Map<String, Object> recordFullSession(FullSessionPayload payload) {
        log.info("Recording complete Agent Activity Session for agent '{}' on repo '{}'",
                payload.getAgentName(), payload.getRepositoryIdOrPath());

        // 1. Resolve or create Agent
        String agentName = (payload.getAgentName() != null && !payload.getAgentName().isBlank())
                ? payload.getAgentName() : "unknown-agent";
        String agentType = payload.getAgentType() != null ? payload.getAgentType() :
                (agentName.toLowerCase().contains("claude") ? "CLAUDE_CODE" :
                 agentName.toLowerCase().contains("codex") ? "CODEX" :
                 agentName.toLowerCase().contains("cursor") ? "CURSOR" : "CLI");

        Agent agent = agentRepository.findByName(agentName).orElseGet(() -> {
            Agent newAgent = Agent.builder()
                    .name(agentName)
                    .type(agentType)
                    .build();
            return agentRepository.save(newAgent);
        });

        // 2. Resolve Repository & Project
        RepositoryEntity repo = resolveRepository(payload.getRepositoryIdOrPath(), payload.getRepositoryIdOrPath());
        Project project = repo != null ? repo.getProject() : resolveProject(payload.getProjectId());

        // 3. Create Persistent JPA AgentSession with accurate lifecycle
        LocalDateTime startedAt = payload.getStartedAt() != null ? payload.getStartedAt() : LocalDateTime.now();
        String sessionStatus = payload.getStatus() != null ? payload.getStatus() : "completed";
        LocalDateTime endedAt = ("completed".equalsIgnoreCase(sessionStatus) || "failed".equalsIgnoreCase(sessionStatus))
                ? (payload.getEndedAt() != null ? payload.getEndedAt() : LocalDateTime.now()) : null;

        AgentSession session = AgentSession.builder()
                .agent(agent)
                .repository(repo)
                .project(project)
                .task(payload.getTaskSummary() != null ? payload.getTaskSummary() : "Autonomous Agent Session")
                .status(sessionStatus)
                .summary(payload.getTaskSummary() != null ? payload.getTaskSummary() : "Autonomous Agent Session")
                .startedAt(startedAt)
                .endedAt(endedAt)
                .build();
        session = sessionRepository.save(session);
        UUID sessionId = session.getId();

        // 4. Record Handoff in JPA and Graph with canonical ID
        Map<String, Object> handoffGraphProps = null;
        if (payload.getHandoff() != null && !payload.getHandoff().isEmpty()) {
            Map<String, Object> h = payload.getHandoff();
            AgentHandoff handoff = AgentHandoff.builder()
                    .agent(agent)
                    .session(session)
                    .repository(repo)
                    .project(project)
                    .task((String) h.getOrDefault("task", payload.getTaskSummary()))
                    .completedItems((String) h.getOrDefault("completedItems", ""))
                    .inProgressItems((String) h.getOrDefault("inProgressItems", ""))
                    .blockedItems((String) h.getOrDefault("blockedItems", ""))
                    .nextSteps((String) h.getOrDefault("nextSteps", ""))
                    .changedFiles(payload.getTouchedFiles() != null ? String.join(", ", payload.getTouchedFiles()) : "")
                    .build();
            handoff = handoffRepository.save(handoff);

            handoffGraphProps = new HashMap<>(h);
            handoffGraphProps.put("id", "handoff::" + handoff.getId().toString());
        }

        // 5. Record Failed Attempts & Lessons Learned with canonical IDs across JPA, Qdrant, and Neo4j
        List<Map<String, Object>> failedAttemptsGraph = new ArrayList<>();
        if (payload.getFailedAttempts() != null) {
            for (Map<String, Object> fa : payload.getFailedAttempts()) {
                AgentAttempt attempt = AgentAttempt.builder()
                        .agentName(agentName)
                        .taskDescription((String) fa.getOrDefault("task", payload.getTaskSummary()))
                        .approach((String) fa.getOrDefault("approach", "Trial"))
                        .status("FAILED")
                        .errorMessage((String) fa.get("errorMessage"))
                        .lessonLearned((String) fa.get("lessonLearned"))
                        .session(session)
                        .repository(repo)
                        .project(project)
                        .filesChanged(payload.getTouchedFiles() != null ? payload.getTouchedFiles() : new ArrayList<>())
                        .build();
                attempt = attemptRepository.save(attempt);
                String canonicalFailId = "fail::" + attempt.getId().toString();

                Map<String, Object> faGraph = new HashMap<>(fa);
                faGraph.put("id", canonicalFailId);
                failedAttemptsGraph.add(faGraph);

                // Vectorize failed lesson into Qdrant using canonical point ID
                try {
                    String doc = String.format("Failed Attempt by %s in %s: %s (Error: %s). Lesson: %s",
                            agentName, repo != null ? repo.getName() : "repo",
                            fa.get("approach"), fa.get("errorMessage"), fa.get("lessonLearned"));
                    float[] vec = embeddingService.embed(doc);
                    if (vec != null) {
                        String pointId = attempt.getId().toString();
                        Map<String, String> p = new HashMap<>();
                        p.put("agentName", agentName);
                        p.put("type", "failed_attempt");
                        p.put("doc", doc);
                        if (repo != null) p.put("repositoryId", repo.getId().toString());
                        vectorStoreService.upsert("agent_memory", pointId, vec, p, Map.of());
                    }
                } catch (Exception ignored) {}
            }
        }

        // 6. Record Decisions into JPA & Vector Store with canonical IDs
        List<Map<String, Object>> decisionsGraph = new ArrayList<>();
        if (payload.getDecisions() != null) {
            for (Map<String, Object> d : payload.getDecisions()) {
                Decision decision = Decision.builder()
                        .title((String) d.getOrDefault("title", "Architectural Decision"))
                        .rationale((String) d.getOrDefault("rationale", ""))
                        .project(project)
                        .repository(repo)
                        .status("approved")
                        .build();
                decision = decisionRepository.save(decision);
                String canonicalDecId = "dec::" + decision.getId().toString();

                Map<String, Object> decGraph = new HashMap<>(d);
                decGraph.put("id", canonicalDecId);
                decisionsGraph.add(decGraph);

                // Vectorize decision into Qdrant using canonical point ID
                try {
                    String doc = String.format("Architectural Decision [%s]: %s. Rationale: %s",
                            decision.getTitle(), decision.getTitle(), decision.getRationale());
                    float[] vec = embeddingService.embed(doc);
                    if (vec != null) {
                        String pointId = decision.getId().toString();
                        Map<String, String> p = new HashMap<>();
                        p.put("title", decision.getTitle());
                        p.put("doc", doc);
                        if (repo != null) p.put("repositoryId", repo.getId().toString());
                        vectorStoreService.upsert("decision_knowledge", pointId, vec, p, Map.of());
                    }
                } catch (Exception ignored) {}
            }
        }

        // 7. Commit Full Graph into Neo4j
        String repoIdStr = repo != null ? repo.getId().toString() : "";
        Map<String, Object> sessionProps = new HashMap<>();
        sessionProps.put("summary", payload.getTaskSummary() != null ? payload.getTaskSummary() : "");
        sessionProps.put("status", sessionStatus);
        sessionProps.put("startedAt", startedAt.toString());
        if (endedAt != null) sessionProps.put("endedAt", endedAt.toString());
        sessionProps.put("branch", payload.getBranch() != null ? payload.getBranch() : "main");
        sessionProps.put("headCommit", payload.getHeadCommit() != null ? payload.getHeadCommit() : "uncommitted");

        graphService.recordAgentSessionGraph(
                agentName,
                agentType,
                sessionId.toString(),
                sessionProps,
                repoIdStr,
                payload.getTouchedFiles(),
                payload.getProblems(),
                decisionsGraph,
                failedAttemptsGraph,
                payload.getCommits(),
                handoffGraphProps
        );

        Map<String, Object> res = new HashMap<>();
        res.put("status", "success");
        res.put("sessionId", sessionId);
        res.put("agent", agentName);
        res.put("summary", payload.getTaskSummary());
        return res;
    }

    public List<Map<String, Object>> getAgentTimeline(String repoIdOrPath, int limit) {
        RepositoryEntity repo = resolveRepository(repoIdOrPath, repoIdOrPath);
        String repoId = repo != null ? repo.getId().toString() : repoIdOrPath;
        return graphService.getAgentTimeline(repoId, limit);
    }
}
