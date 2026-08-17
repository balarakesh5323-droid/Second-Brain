package com.secondbrain.mcp.tools;

import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.*;
import com.secondbrain.common.repository.*;
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
    private final DecisionRepository decisionRepository;
    private final TaskRepository taskRepository;

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
}
