package com.secondbrain.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.common.dto.ContextResponse;
import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.*;
import com.secondbrain.common.repository.*;
import com.secondbrain.service.ContextAssemblyService;
import com.secondbrain.service.GraphService;
import com.secondbrain.service.BrainDoctorService;
import com.secondbrain.service.RetrievalQualityService;
import com.secondbrain.service.RepositoryIngestionService;
import com.secondbrain.service.GitHubCloneService;
import com.secondbrain.service.AgentBridgeService;
import io.modelcontextprotocol.spec.McpSchema.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class BrainToolHandler {

    private final MemoryRepository memoryRepository;
    private final ProjectRepository projectRepository;
    private final RepositoryEntityRepository repositoryRepository;
    private final AgentRepository agentRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentEventRepository eventRepository;
    private final AgentHandoffRepository handoffRepository;
    private final AgentAttemptRepository attemptRepository;
    private final AgentBridgeService agentBridgeService;
    private final DecisionRepository decisionRepository;
    private final TaskRepository taskRepository;
    private final ContextAssemblyService contextAssemblyService;
    private final GraphService graphService;
    private final BrainDoctorService brainDoctorService;
    private final RetrievalQualityService retrievalQualityService;
    private final RepositoryIngestionService ingestionService;
    private final GitHubCloneService gitHubCloneService;
    private final ObjectMapper objectMapper;

    public CallToolResult handleSearch(String query, String collection, int limit) {
        try {
            List<Memory> memories = memoryRepository.findByContentContainingIgnoreCase(query);
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Memory m : memories) {
                if (count >= limit) break;
                sb.append("- [").append(m.getType()).append("] (").append(m.getScope()).append(")\n");
                sb.append("  ").append(m.getContent()).append("\n\n");
                count++;
            }
            if (count == 0) {
                return new CallToolResult(List.of(new TextContent("No results found for: " + query)), false);
            }
            return new CallToolResult(List.of(new TextContent(
                "Found " + count + " results:\n\n" + sb)), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleStoreMemory(String content, String type, String scope,
            String projectId, String repositoryId, List<String> tags) {
        try {
            Memory memory = Memory.builder()
                .content(content)
                .type(MemoryType.valueOf(type))
                .scope(MemoryScope.valueOf(scope != null ? scope : "GLOBAL"))
                .status(MemoryStatus.NEW)
                .confidence(0.5)
                .importance(0.5)
                .observationCount(1)
                .tags(tags != null ? new HashSet<>(tags) : new HashSet<>())
                .firstSeenAt(LocalDateTime.now())
                .lastSeenAt(LocalDateTime.now())
                .build();

            if (projectId != null) {
                projectRepository.findById(UUID.fromString(projectId))
                    .ifPresent(memory::setProject);
            }
            if (repositoryId != null) {
                repositoryRepository.findById(UUID.fromString(repositoryId))
                    .ifPresent(memory::setRepository);
            }

            memoryRepository.save(memory);
            return new CallToolResult(List.of(new TextContent(
                "Memory stored with ID: " + memory.getId())), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleRecordEvent(String sessionId, String eventType,
            String description, String filePath, String status) {
        try {
            if (sessionId == null) {
                return new CallToolResult(List.of(new TextContent(
                    "Error: session_id is required for recording events")), true);
            }

            AgentSession session = sessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

            AgentEvent event = AgentEvent.builder()
                .session(session)
                .eventType(EventType.valueOf(eventType))
                .description(description)
                .filePath(filePath)
                .status(status != null ? status : "success")
                .build();

            eventRepository.save(event);
            return new CallToolResult(List.of(new TextContent(
                "Event recorded with ID: " + event.getId())), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleStartSession(String agentName, String task,
            String repositoryId, String projectId) {
        try {
            Agent agent = agentRepository.findByName(agentName)
                .orElseGet(() -> {
                    Agent newAgent = Agent.builder()
                        .name(agentName)
                        .type(agentName)
                        .build();
                    return agentRepository.save(newAgent);
                });

            AgentSession session = AgentSession.builder()
                .agent(agent)
                .task(task)
                .status("active")
                .startedAt(LocalDateTime.now())
                .build();

            if (repositoryId != null) {
                repositoryRepository.findById(UUID.fromString(repositoryId))
                    .ifPresent(session::setRepository);
            }
            if (projectId != null) {
                projectRepository.findById(UUID.fromString(projectId))
                    .ifPresent(session::setProject);
            }

            sessionRepository.save(session);
            return new CallToolResult(List.of(new TextContent(
                "Session started with ID: " + session.getId())), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleEndSession(String sessionId, String summary) {
        try {
            AgentSession session = sessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new RuntimeException("Session not found"));
            session.setStatus("completed");
            session.setSummary(summary);
            session.setEndedAt(LocalDateTime.now());
            sessionRepository.save(session);
            return new CallToolResult(List.of(new TextContent(
                "Session ended: " + sessionId)), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleGetHandoff(String repositoryId) {
        try {
            AgentHandoff handoff = handoffRepository
                .findFirstByRepositoryIdOrderByCreatedAtDesc(UUID.fromString(repositoryId))
                .orElse(null);

            if (handoff == null) {
                return new CallToolResult(List.of(new TextContent(
                    "No handoff found for repository")), false);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== AGENT HANDOFF ===\n\n");
            sb.append("Task: ").append(handoff.getTask()).append("\n");
            sb.append("Agent: ").append(handoff.getAgent().getName()).append("\n");
            sb.append("Session: ").append(handoff.getSession().getId()).append("\n\n");
            sb.append("Completed:\n").append(handoff.getCompletedItems()).append("\n\n");
            sb.append("In Progress:\n").append(handoff.getInProgressItems()).append("\n\n");
            sb.append("Blocked:\n").append(handoff.getBlockedItems()).append("\n\n");
            sb.append("Changed Files:\n").append(handoff.getChangedFiles()).append("\n\n");
            sb.append("Next Steps:\n").append(handoff.getNextSteps()).append("\n\n");
            sb.append("Decisions:\n").append(handoff.getDecisions()).append("\n\n");
            sb.append("Known Issues:\n").append(handoff.getKnownIssues()).append("\n");

            return new CallToolResult(List.of(new TextContent(sb.toString())), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleCreateHandoff(String sessionId, String task,
            String completedItems, String inProgressItems, String blockedItems,
            String changedFiles, String nextSteps, String decisions, String knownIssues) {
        try {
            AgentSession session = sessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new RuntimeException("Session not found"));

            AgentHandoff handoff = AgentHandoff.builder()
                .session(session)
                .agent(session.getAgent())
                .repository(session.getRepository())
                .project(session.getProject())
                .task(task)
                .completedItems(completedItems)
                .inProgressItems(inProgressItems)
                .blockedItems(blockedItems)
                .changedFiles(changedFiles)
                .nextSteps(nextSteps)
                .decisions(decisions)
                .knownIssues(knownIssues)
                .build();

            handoffRepository.save(handoff);
            return new CallToolResult(List.of(new TextContent(
                "Handoff created with ID: " + handoff.getId())), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleRecordDecision(String title, String description,
            String rationale, String projectId, String repositoryId, List<String> tags) {
        try {
            Decision decision = Decision.builder()
                .title(title)
                .description(description)
                .rationale(rationale)
                .status("active")
                .tags(tags != null ? new HashSet<>(tags) : new HashSet<>())
                .build();

            if (projectId != null) {
                projectRepository.findById(UUID.fromString(projectId))
                    .ifPresent(decision::setProject);
            }
            if (repositoryId != null) {
                repositoryRepository.findById(UUID.fromString(repositoryId))
                    .ifPresent(decision::setRepository);
            }

            decisionRepository.save(decision);
            return new CallToolResult(List.of(new TextContent(
                "Decision recorded with ID: " + decision.getId())), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleGetProjects() {
        try {
            List<Project> projects = projectRepository.findAll();
            StringBuilder sb = new StringBuilder();
            sb.append("Projects:\n\n");
            for (Project p : projects) {
                sb.append("- ").append(p.getName()).append(" (").append(p.getId()).append(")\n");
                sb.append("  Path: ").append(p.getPath()).append("\n\n");
            }
            if (projects.isEmpty()) {
                return new CallToolResult(List.of(new TextContent("No projects found")), false);
            }
            return new CallToolResult(List.of(new TextContent(sb.toString())), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleGetRecentActivity(int limit) {
        try {
            List<AgentEvent> events = eventRepository.findTop20ByOrderByCreatedAtDesc();
            StringBuilder sb = new StringBuilder();
            sb.append("Recent Activity:\n\n");
            int count = 0;
            for (AgentEvent e : events) {
                if (count >= limit) break;
                sb.append("[").append(e.getEventType()).append("] ")
                  .append(e.getDescription()).append("\n");
                sb.append("  Time: ").append(e.getCreatedAt()).append("\n");
                if (e.getFilePath() != null) {
                    sb.append("  File: ").append(e.getFilePath()).append("\n");
                }
                sb.append("\n");
                count++;
            }
            if (count == 0) {
                return new CallToolResult(List.of(new TextContent("No recent activity")), false);
            }
            return new CallToolResult(List.of(new TextContent(sb.toString())), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleCreateTask(String title, String description,
            String projectId, String repositoryId, Integer priority) {
        try {
            Task task = Task.builder()
                .title(title)
                .description(description)
                .status(TaskStatus.OPEN)
                .priority(priority != null ? priority : 3)
                .build();

            if (projectId != null) {
                projectRepository.findById(UUID.fromString(projectId))
                    .ifPresent(task::setProject);
            }
            if (repositoryId != null) {
                repositoryRepository.findById(UUID.fromString(repositoryId))
                    .ifPresent(task::setRepository);
            }

            taskRepository.save(task);
            return new CallToolResult(List.of(new TextContent(
                "Task created with ID: " + task.getId())), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleGetOpenTasks(String projectId) {
        try {
            List<Task> tasks;
            if (projectId != null) {
                tasks = taskRepository.findByStatusAndProjectId(
                    TaskStatus.OPEN, UUID.fromString(projectId));
            } else {
                tasks = taskRepository.findByStatus(TaskStatus.OPEN);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Open Tasks:\n\n");
            for (Task t : tasks) {
                sb.append("- ").append(t.getTitle()).append(" (priority: ")
                  .append(t.getPriority()).append(")\n");
                if (t.getDescription() != null) {
                    sb.append("  ").append(t.getDescription()).append("\n");
                }
                sb.append("\n");
            }
            if (tasks.isEmpty()) {
                return new CallToolResult(List.of(new TextContent("No open tasks")), false);
            }
            return new CallToolResult(List.of(new TextContent(sb.toString())), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleBrainDoctor() {
        try {
            BrainDoctorService.DoctorReport report = brainDoctorService.runDiagnostics();
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
            return new CallToolResult(List.of(new TextContent(json)), false);
        } catch (Exception e) {
            log.error("Brain doctor diagnostics failed", e);
            return new CallToolResult(List.of(new TextContent("Error running diagnostics: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleEvaluateQuality() {
        try {
            RetrievalQualityService.QualityReport report = retrievalQualityService.evaluate(null);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
            return new CallToolResult(List.of(new TextContent(json)), false);
        } catch (Exception e) {
            log.error("Retrieval quality evaluation failed", e);
            return new CallToolResult(List.of(new TextContent("Error evaluating quality: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleGetContext(String query, String projectId, String repositoryId) {
        try {
            ContextResponse context = contextAssemblyService.assembleContext(query, projectId, repositoryId);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
            return new CallToolResult(List.of(new TextContent(json)), false);
        } catch (Exception e) {
            log.error("Failed to assemble context", e);
            return new CallToolResult(List.of(new TextContent("Error assembling context: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleKnowledgeGraph(String label, String id, Integer depth) {
        try {
            int traversDepth = depth != null ? depth : 2;

            if (id != null && !id.isBlank()) {
                // Find related nodes for a specific node
                List<Map<String, Object>> related = graphService.findRelated(label, id, null, traversDepth);
                StringBuilder sb = new StringBuilder();
                sb.append("Knowledge Graph — ").append(label).append(" (").append(id).append(")\n");
                sb.append("Traversal depth: ").append(traversDepth).append("\n\n");

                if (related.isEmpty()) {
                    sb.append("No related nodes found.\n");
                } else {
                    sb.append("Related nodes:\n");
                    for (Map<String, Object> node : related) {
                        sb.append("- ").append(node.getOrDefault("id", "unknown"));
                        if (node.containsKey("depth")) {
                            sb.append(" (depth: ").append(node.get("depth")).append(")");
                        }
                        sb.append("\n");
                        // Show all properties
                        node.forEach((key, value) -> {
                            if (!"id".equals(key) && !"depth".equals(key)) {
                                sb.append("  ").append(key).append(": ").append(value).append("\n");
                            }
                        });
                        sb.append("\n");
                    }
                }
                return new CallToolResult(List.of(new TextContent(sb.toString())), false);

            } else {
                // List nodes by label
                List<Map<String, Object>> nodes = graphService.getNodesByLabel(label, 50);
                StringBuilder sb = new StringBuilder();
                sb.append("Knowledge Graph — All ").append(label).append(" nodes\n\n");

                if (nodes.isEmpty()) {
                    sb.append("No nodes found with label: ").append(label).append("\n");
                } else {
                    sb.append("Found ").append(nodes.size()).append(" nodes:\n\n");
                    for (Map<String, Object> node : nodes) {
                        sb.append("- ").append(node.getOrDefault("id", "unknown")).append("\n");
                        node.forEach((key, value) -> {
                            if (!"id".equals(key)) {
                                sb.append("  ").append(key).append(": ").append(value).append("\n");
                            }
                        });
                        sb.append("\n");
                    }
                }
                return new CallToolResult(List.of(new TextContent(sb.toString())), false);
            }
        } catch (Exception e) {
            log.error("Knowledge graph query failed", e);
            return new CallToolResult(List.of(new TextContent("Error querying knowledge graph: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleAddRepository(String url, String projectId) {
        try {
            if (url == null || url.isBlank()) {
                return new CallToolResult(List.of(new TextContent("Error: URL is required")), true);
            }

            if (!gitHubCloneService.isGitHubUrl(url)) {
                return new CallToolResult(List.of(new TextContent(
                    "Error: Not a valid GitHub URL: " + url)), true);
            }

            java.util.UUID projectUuid = null;
            if (projectId != null && !projectId.isBlank()) {
                projectUuid = java.util.UUID.fromString(projectId);
            }

            Map<String, Object> result = ingestionService.ingestFromUrl(url, projectUuid);

            StringBuilder sb = new StringBuilder();
            sb.append("=== Repository Ingested ===\n\n");
            sb.append("URL: ").append(url).append("\n");
            sb.append("Status: ").append(result.getOrDefault("status", "unknown")).append("\n");
            sb.append("Project: ").append(result.getOrDefault("projectName", "N/A"))
              .append(" (").append(result.getOrDefault("projectId", "N/A")).append(")\n");
            sb.append("Local Path: ").append(result.getOrDefault("localPath", "N/A")).append("\n");
            sb.append("Branch: ").append(result.getOrDefault("branch", "N/A")).append("\n");
            sb.append("Repository ID: ").append(result.getOrDefault("repositoryId", "N/A")).append("\n\n");

            sb.append("Languages: ").append(result.getOrDefault("languages", List.of())).append("\n");
            sb.append("Frameworks: ").append(result.getOrDefault("frameworks", List.of())).append("\n");
            sb.append("Databases: ").append(result.getOrDefault("databases", List.of())).append("\n\n");

            sb.append("Code files parsed: ").append(result.getOrDefault("codeStructureCount", 0)).append("\n");
            sb.append("Commits embedded: ").append(result.getOrDefault("commitsEmbedded", 0)).append("\n");
            sb.append("Code files embedded: ").append(result.getOrDefault("codeFilesEmbedded", 0)).append("\n");
            sb.append("Graph nodes created: ").append(result.getOrDefault("graphNodesCreated", 0)).append("\n\n");

            Object elapsed = result.get("elapsedMs");
            if (elapsed != null) {
                sb.append("Completed in ").append(elapsed).append("ms\n");
            }

            if (result.containsKey("error")) {
                sb.append("\nError: ").append(result.get("error")).append("\n");
            }

            return new CallToolResult(List.of(new TextContent(sb.toString())), false);
        } catch (Exception e) {
            log.error("Failed to add repository", e);
            return new CallToolResult(List.of(new TextContent("Error adding repository: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleRepositoryContext(String repositoryId, String query) {
        try {
            StringBuilder sb = new StringBuilder();

            if (repositoryId != null && !repositoryId.isBlank()) {
                var repo = repositoryRepository.findById(java.util.UUID.fromString(repositoryId));
                if (repo.isEmpty()) {
                    return new CallToolResult(List.of(new TextContent(
                        "Repository not found: " + repositoryId)), false);
                }
                var r = repo.get();
                sb.append("=== Repository Context ===\n\n");
                sb.append("Name: ").append(r.getName()).append("\n");
                sb.append("URL: ").append(r.getUrl()).append("\n");
                sb.append("Path: ").append(r.getPath()).append("\n");
                sb.append("Branch: ").append(r.getDefaultBranch()).append("\n");
                sb.append("Language: ").append(r.getPrimaryLanguage()).append("\n");
                sb.append("Description: ").append(r.getDescription()).append("\n\n");

                // Query knowledge graph for this repo
                String graphId = r.getUrl();
                if (graphId != null && graphId.contains("github.com")) {
                    graphId = graphId.replace("https://github.com/", "").replace(".git", "");
                }
                List<Map<String, Object>> related = graphService.findRelated("Repository", graphId, null, 2);
                if (!related.isEmpty()) {
                    sb.append("Knowledge Graph Connections:\n");
                    for (Map<String, Object> node : related) {
                        sb.append("- ").append(node.getOrDefault("id", "unknown")).append("\n");
                    }
                    sb.append("\n");
                }

                // If query provided, search for relevant memories about this repo
                if (query != null && !query.isBlank()) {
                    List<Memory> memories = memoryRepository.findByContentContainingIgnoreCase(query);
                    if (!memories.isEmpty()) {
                        sb.append("Relevant Memories:\n");
                        for (Memory m : memories) {
                            sb.append("- [").append(m.getType()).append("] ").append(m.getContent()).append("\n");
                        }
                        sb.append("\n");
                    }
                }
            } else {
                // List all repositories
                List<RepositoryEntity> repos = repositoryRepository.findAll();
                sb.append("=== All Indexed Repositories ===\n\n");
                for (RepositoryEntity r : repos) {
                    sb.append("- ").append(r.getName()).append(" (").append(r.getId()).append(")\n");
                    sb.append("  URL: ").append(r.getUrl()).append("\n");
                    sb.append("  Language: ").append(r.getPrimaryLanguage()).append("\n");
                    sb.append("  Path: ").append(r.getPath()).append("\n\n");
                }
                if (repos.isEmpty()) {
                    sb.append("No repositories indexed yet. Use brain_add_repository to add one.\n");
                }
            }

            return new CallToolResult(List.of(new TextContent(sb.toString())), false);
        } catch (Exception e) {
            log.error("Failed to get repository context", e);
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleRecordAttempt(String agentName, String taskDescription, String approach,
            String status, List<String> filesChanged, List<String> commandsExecuted,
            String workingTreeDiff, String errorMessage, String lessonLearned,
            String repositoryId, String projectId, List<String> tags) {
        try {
            var dto = AgentBridgeService.AgentAttemptDto.builder()
                    .agentName(agentName != null ? agentName : "mcp-agent")
                    .taskDescription(taskDescription)
                    .approach(approach)
                    .status(status != null ? status : "SUCCESS")
                    .filesChanged(filesChanged)
                    .commandsExecuted(commandsExecuted)
                    .workingTreeDiff(workingTreeDiff)
                    .errorMessage(errorMessage)
                    .lessonLearned(lessonLearned)
                    .repositoryId(repositoryId)
                    .projectId(projectId)
                    .tags(tags != null ? new HashSet<>(tags) : new HashSet<>())
                    .build();

            AgentAttempt attempt = agentBridgeService.recordAttempt(dto);
            return new CallToolResult(List.of(new TextContent(
                    "Engineering attempt recorded with ID: " + attempt.getId() + " [Status: " + attempt.getStatus() + "]")), false);
        } catch (Exception e) {
            log.error("Failed to record agent attempt", e);
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleGetAttempts(String repositoryId, int limit) {
        try {
            List<AgentAttempt> attempts;
            if (repositoryId != null && !repositoryId.isBlank()) {
                attempts = attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(UUID.fromString(repositoryId));
            } else {
                attempts = attemptRepository.findAllOrderByCreatedAtDesc();
            }

            if (attempts.isEmpty()) {
                return new CallToolResult(List.of(new TextContent("No prior engineering attempts found.")), false);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== Previous Engineering Attempts & Trials ===\n\n");
            int count = 0;
            for (AgentAttempt a : attempts) {
                if (count >= limit) break;
                sb.append(String.format("Attempt #%d [%s] by %s at %s\n", count + 1, a.getStatus(), a.getAgentName(), a.getCreatedAt()));
                sb.append("Task: ").append(a.getTaskDescription()).append("\n");
                sb.append("Approach: ").append(a.getApproach()).append("\n");
                if (a.getErrorMessage() != null && !a.getErrorMessage().isBlank()) {
                    sb.append("Error Encountered: ").append(a.getErrorMessage()).append("\n");
                }
                if (a.getLessonLearned() != null && !a.getLessonLearned().isBlank()) {
                    sb.append("Lesson Learned: ").append(a.getLessonLearned()).append("\n");
                }
                if (!a.getFilesChanged().isEmpty()) {
                    sb.append("Files Touched: ").append(String.join(", ", a.getFilesChanged())).append("\n");
                }
                sb.append("\n");
                count++;
            }

            return new CallToolResult(List.of(new TextContent(sb.toString())), false);
        } catch (Exception e) {
            log.error("Failed to get agent attempts", e);
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public CallToolResult handleGetContinuityState(String repositoryIdOrPath) {
        try {
            Map<String, Object> state = agentBridgeService.getContinuityState(repositoryIdOrPath);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);
            return new CallToolResult(List.of(new TextContent(
                    "=== Multi-Agent Continuity Snapshot ===\n\n" + json)), false);
        } catch (Exception e) {
            log.error("Failed to get continuity state", e);
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }
}
