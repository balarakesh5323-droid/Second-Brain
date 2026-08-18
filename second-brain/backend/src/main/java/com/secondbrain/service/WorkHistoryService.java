package com.secondbrain.service;

import com.secondbrain.common.dto.WorkHistoryResponse;
import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkHistoryService {

    private final RepositoryEntityRepository repositoryRepository;
    private final ProjectRepository projectRepository;
    private final AgentAttemptRepository attemptRepository;
    private final DecisionRepository decisionRepository;
    private final MemoryRepository memoryRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Transactional(readOnly = true)
    public WorkHistoryResponse getWorkHistory(String repositoryName, String projectIdStr, String taskQuery, Integer limit) {
        int maxItems = (limit != null && limit > 0) ? limit : 25;

        RepositoryEntity repo = null;
        if (repositoryName != null && !repositoryName.isBlank()) {
            repo = repositoryRepository.findByName(repositoryName).orElse(null);
        }

        Project project = null;
        if (projectIdStr != null && !projectIdStr.isBlank()) {
            try {
                project = projectRepository.findById(UUID.fromString(projectIdStr)).orElse(null);
            } catch (Exception ignored) {}
        } else if (repo != null && repo.getProject() != null) {
            project = repo.getProject();
        }

        List<WorkHistoryResponse.AgentWorkItem> workItems = new ArrayList<>();
        List<WorkHistoryResponse.DecisionItem> decisions = new ArrayList<>();
        List<WorkHistoryResponse.MemoryItem> memories = new ArrayList<>();

        // 1. Gather Agent Attempts & Trials
        List<AgentAttempt> attempts;
        if (repo != null) {
            attempts = attemptRepository.findByRepositoryIdOrderByCreatedAtDesc(repo.getId(), PageRequest.of(0, maxItems));
        } else if (project != null) {
            attempts = attemptRepository.findByProjectIdOrderByCreatedAtDesc(project.getId(), PageRequest.of(0, maxItems));
        } else {
            attempts = attemptRepository.findAllOrderByCreatedAtDesc(PageRequest.of(0, maxItems));
        }

        for (AgentAttempt a : attempts) {
            workItems.add(WorkHistoryResponse.AgentWorkItem.builder()
                    .agentName(a.getAgentName() != null ? a.getAgentName() : "UNKNOWN")
                    .sessionId(a.getSession() != null ? a.getSession().getId().toString() : "session-na")
                    .timestamp(a.getCreatedAt() != null ? a.getCreatedAt() : java.time.LocalDateTime.now())
                    .activityType("AGENT_TRIAL")
                    .task(a.getTaskDescription())
                    .approach(a.getApproach())
                    .outcome(a.getStatus())
                    .failureReason(a.getErrorMessage())
                    .lessonLearned(a.getLessonLearned())
                    .filesChanged(a.getFilesChanged() != null ? new ArrayList<>(a.getFilesChanged()) : List.of())
                    .build());
        }

        // 2. Gather Architectural Decisions
        List<Decision> decisionList;
        if (repo != null) {
            decisionList = decisionRepository.findByRepositoryIdOrderByCreatedAtDesc(repo.getId(), PageRequest.of(0, maxItems));
        } else if (project != null) {
            decisionList = decisionRepository.findByProjectIdOrderByCreatedAtDesc(project.getId(), PageRequest.of(0, maxItems));
        } else {
            decisionList = decisionRepository.findTop10ByOrderByCreatedAtDesc();
        }

        for (Decision d : decisionList) {
            String agent = (d.getAgent() != null && d.getAgent().getName() != null) ? d.getAgent().getName() : "Team/Agent";
            decisions.add(WorkHistoryResponse.DecisionItem.builder()
                    .title(d.getTitle())
                    .rationale(d.getRationale())
                    .agentName(agent)
                    .createdAt(d.getCreatedAt() != null ? d.getCreatedAt() : java.time.LocalDateTime.now())
                    .build());

            workItems.add(WorkHistoryResponse.AgentWorkItem.builder()
                    .agentName(agent)
                    .sessionId(d.getSession() != null ? d.getSession().getId().toString() : "session-na")
                    .timestamp(d.getCreatedAt() != null ? d.getCreatedAt() : java.time.LocalDateTime.now())
                    .activityType("DECISION_APPROVED")
                    .task(d.getTitle())
                    .approach(d.getDescription())
                    .outcome("APPROVED")
                    .lessonLearned(d.getRationale())
                    .filesChanged(List.of())
                    .build());
        }

        // 3. Gather Active Established Knowledge
        final Project targetProject = project;
        List<Memory> activeMems = memoryRepository.findAll().stream()
                .filter(m -> m.getStatus() == MemoryStatus.ESTABLISHED || m.getStatus() == MemoryStatus.CONFIRMED)
                .filter(m -> {
                    if (targetProject != null && m.getProject() != null) {
                        return m.getProject().getId().equals(targetProject.getId());
                    }
                    return true;
                })
                .limit(10)
                .toList();

        for (Memory m : activeMems) {
            memories.add(WorkHistoryResponse.MemoryItem.builder()
                    .memoryKey(m.getMemoryKey())
                    .content(m.getContent())
                    .status(m.getStatus() != null ? m.getStatus().name() : "CONFIRMED")
                    .confidence(m.getConfidence())
                    .provenanceSource(m.getProvenanceSource())
                    .build());
        }

        // Sort work items chronologically
        workItems.sort(Comparator.comparing(WorkHistoryResponse.AgentWorkItem::getTimestamp).reversed());

        // 4. Build Formatted Narrative Markdown
        String formattedNarrative = buildFormattedNarrative(
                repositoryName != null ? repositoryName : (repo != null ? repo.getName() : "Global"),
                project != null ? project.getName() : "Default",
                taskQuery,
                workItems,
                decisions,
                memories
        );

        return WorkHistoryResponse.builder()
                .repository(repo != null ? repo.getName() : repositoryName)
                .project(project != null ? project.getName() : projectIdStr)
                .taskQuery(taskQuery)
                .totalActivities(workItems.size())
                .chronologicalWork(workItems)
                .activeDecisions(decisions)
                .consolidatedKnowledge(memories)
                .formattedNarrative(formattedNarrative)
                .build();
    }

    private String buildFormattedNarrative(
            String repoName, String projectName, String taskQuery,
            List<WorkHistoryResponse.AgentWorkItem> workItems,
            List<WorkHistoryResponse.DecisionItem> decisions,
            List<WorkHistoryResponse.MemoryItem> memories) {

        StringBuilder sb = new StringBuilder();
        sb.append("# 🧠 Second Brain: Cross-Agent Work History & Continuity\n\n");
        sb.append("**Repository:** `").append(repoName).append("` | **Project:** `").append(projectName).append("`\n");
        if (taskQuery != null && !taskQuery.isBlank()) {
            sb.append("**Target Task:** *").append(taskQuery).append("*\n");
        }
        sb.append("\n---\n\n");

        sb.append("## 📜 Chronological Work Narrative\n\n");
        if (workItems.isEmpty()) {
            sb.append("*No previous work sessions recorded for this scope.*\n\n");
        } else {
            // Group by Agent
            Map<String, List<WorkHistoryResponse.AgentWorkItem>> byAgent = workItems.stream()
                    .collect(Collectors.groupingBy(WorkHistoryResponse.AgentWorkItem::getAgentName, LinkedHashMap::new, Collectors.toList()));

            for (Map.Entry<String, List<WorkHistoryResponse.AgentWorkItem>> entry : byAgent.entrySet()) {
                String agent = entry.getKey();
                sb.append("### 🤖 Agent: ").append(agent).append("\n\n");
                for (WorkHistoryResponse.AgentWorkItem item : entry.getValue()) {
                    String timeStr = item.getTimestamp() != null ? item.getTimestamp().format(DATE_FMT) : "Recent";
                    sb.append("- **[").append(timeStr).append("]** ");
                    if ("FAILURE".equalsIgnoreCase(item.getOutcome()) || "FAILED_ATTEMPT".equalsIgnoreCase(item.getActivityType())) {
                        sb.append("❌ **Failed Trial:** ").append(item.getTask()).append("\n");
                        sb.append("  - **Approach:** ").append(item.getApproach() != null ? item.getApproach() : "Trial implementation").append("\n");
                        if (item.getFailureReason() != null) {
                            sb.append("  - **Error/Failure:** `").append(item.getFailureReason()).append("`\n");
                        }
                        if (item.getLessonLearned() != null) {
                            sb.append("  - **💡 Lesson Learned:** ").append(item.getLessonLearned()).append("\n");
                        }
                    } else if ("DECISION_APPROVED".equalsIgnoreCase(item.getActivityType())) {
                        sb.append("🏛️ **Architectural Decision:** ").append(item.getTask()).append("\n");
                        if (item.getLessonLearned() != null) {
                            sb.append("  - **Rationale:** ").append(item.getLessonLearned()).append("\n");
                        }
                    } else {
                        sb.append("✅ **Completed Work:** ").append(item.getTask()).append("\n");
                        if (item.getApproach() != null) {
                            sb.append("  - **Details:** ").append(item.getApproach()).append("\n");
                        }
                    }
                    if (item.getFilesChanged() != null && !item.getFilesChanged().isEmpty()) {
                        sb.append("  - **Files Touched:** `").append(String.join("`, `", item.getFilesChanged())).append("`\n");
                    }
                    sb.append("\n");
                }
            }
        }

        if (!decisions.isEmpty()) {
            sb.append("## 🏛️ Active Architectural Standards\n\n");
            for (WorkHistoryResponse.DecisionItem d : decisions) {
                sb.append("- **").append(d.getTitle()).append("**");
                if (d.getRationale() != null) {
                    sb.append(" — *").append(d.getRationale()).append("*");
                }
                sb.append(" (By: `").append(d.getAgentName()).append("`)\n");
            }
            sb.append("\n");
        }

        if (!memories.isEmpty()) {
            sb.append("## 🔒 Consolidated Long-Term Knowledge\n\n");
            for (WorkHistoryResponse.MemoryItem m : memories) {
                sb.append("- **[").append(m.getStatus()).append(" | ").append(String.format("%.2f", m.getConfidence())).append("]** ");
                sb.append(m.getContent()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
