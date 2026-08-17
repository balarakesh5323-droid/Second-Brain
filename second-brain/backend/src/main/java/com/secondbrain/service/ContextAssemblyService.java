package com.secondbrain.service;

import com.secondbrain.common.dto.ContextResponse;
import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.TaskStatus;
import com.secondbrain.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContextAssemblyService {

    private final SemanticSearchService semanticSearchService;
    private final GraphService graphService;
    private final RedisService redisService;
    private final MemoryRepository memoryRepository;
    private final DecisionRepository decisionRepository;
    private final TaskRepository taskRepository;
    private final AgentEventRepository eventRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentRepository agentRepository;
    private final SkillRepository skillRepository;
    private final ProjectRepository projectRepository;
    private final RepositoryEntityRepository repositoryRepository;
    private final AgentHandoffRepository handoffRepository;

    public ContextResponse assembleContext(String query, String projectId, String repositoryId) {
        log.info("Assembling context for query: '{}'", query);

        // Step 1: Understand query intent (extract keywords)
        QueryIntent intent = parseQueryIntent(query);

        // Step 2: Resolve project/repository scope
        String resolvedProjectId = projectId;
        String resolvedRepoId = repositoryId;
        String projectName = null;
        String repoName = null;

        if (resolvedProjectId != null) {
            var project = projectRepository.findById(UUID.fromString(resolvedProjectId));
            if (project.isPresent()) {
                projectName = project.get().getName();
            }
        }
        if (resolvedRepoId != null) {
            var repo = repositoryRepository.findById(UUID.fromString(resolvedRepoId));
            if (repo.isPresent()) {
                repoName = repo.get().getName();
            }
        }

        // Step 3: Search Qdrant (semantic vector search)
        List<SearchResult> semanticResults = Collections.emptyList();
        try {
            semanticResults = semanticSearchService.searchAllCollections(query, 20);
        } catch (Exception e) {
            log.warn("Semantic search failed: {}", e.getMessage());
        }

        // Step 4: Query Neo4j relationships
        List<Map<String, Object>> graphResults = Collections.emptyList();
        try {
            if (resolvedProjectId != null) {
                graphResults = graphService.findRelated("Project", resolvedProjectId, null, 2);
            } else if (intent.topics != null && !intent.topics.isEmpty()) {
                graphResults = graphService.searchByProperty("Technology", "name", intent.topics.get(0), 10);
            }
        } catch (Exception e) {
            log.warn("Graph query failed: {}", e.getMessage());
        }

        // Step 5: Query recent PostgreSQL events
        List<AgentEvent> recentEvents = eventRepository.findTop20ByOrderByCreatedAtDesc();

        // Step 6: Query Redis hot state (frequently accessed memories)
        List<Memory> hotMemories = Collections.emptyList();
        try {
            hotMemories = memoryRepository.findTop10ByOrderByObservationCountDesc();
        } catch (Exception e) {
            log.warn("Redis hot state query failed: {}", e.getMessage());
        }

        // Step 7: Retrieve relevant artifacts (decisions, tasks, handoffs)
        List<Decision> decisions = getRelevantDecisions(resolvedProjectId, resolvedRepoId);
        List<Task> openTasks = getOpenTasks(resolvedProjectId, resolvedRepoId);
        List<AgentHandoff> recentHandoffs = getRecentHandoffs(resolvedRepoId);

        // Step 8: Deduplicate results
        List<Memory> deduplicatedMemories = deduplicateMemories(
            semanticResults, hotMemories, resolvedProjectId, resolvedRepoId);

        // Step 9: Resolve contradictions
        List<Memory> resolvedMemories = resolveContradictions(deduplicatedMemories);

        // Step 10: Rank memories by relevance
        List<Memory> rankedMemories = rankMemories(resolvedMemories, intent);

        // Step 11: Compress context (take top results)
        List<Memory> compressedMemories = rankedMemories.stream()
            .limit(15)
            .collect(Collectors.toList());

        // Step 12: Assemble structured response
        return buildResponse(
            projectName, repoName, compressedMemories, graphResults,
            recentEvents, decisions, openTasks, recentHandoffs, intent);
    }

    private QueryIntent parseQueryIntent(String query) {
        QueryIntent intent = new QueryIntent();
        intent.originalQuery = query;
        intent.keywords = Arrays.stream(query.toLowerCase().split("\\s+"))
            .filter(w -> w.length() > 2)
            .collect(Collectors.toList());

        // Extract potential entity types from keywords
        intent.topics = intent.keywords.stream()
            .filter(kw -> kw.matches(".*(postgres|redis|neo4j|docker|spring|react|api|auth|deploy).*"))
            .collect(Collectors.toList());

        return intent;
    }

    private List<Decision> getRelevantDecisions(String projectId, String repositoryId) {
        try {
            if (projectId != null) {
                return decisionRepository.findByProjectId(UUID.fromString(projectId));
            }
            if (repositoryId != null) {
                return decisionRepository.findByRepositoryId(UUID.fromString(repositoryId));
            }
            return decisionRepository.findTop10ByOrderByCreatedAtDesc();
        } catch (Exception e) {
            log.warn("Failed to fetch decisions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Task> getOpenTasks(String projectId, String repositoryId) {
        try {
            if (projectId != null) {
                return taskRepository.findByStatusAndProjectId(
                    TaskStatus.OPEN, UUID.fromString(projectId));
            }
            return taskRepository.findByStatus(TaskStatus.OPEN);
        } catch (Exception e) {
            log.warn("Failed to fetch open tasks: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<AgentHandoff> getRecentHandoffs(String repositoryId) {
        if (repositoryId == null) return Collections.emptyList();
        try {
            var handoff = handoffRepository
                .findFirstByRepositoryIdOrderByCreatedAtDesc(UUID.fromString(repositoryId));
            return handoff.map(List::of).orElse(Collections.emptyList());
        } catch (Exception e) {
            log.warn("Failed to fetch handoffs: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Memory> deduplicateMemories(
            List<SearchResult> semanticResults, List<Memory> hotMemories,
            String projectId, String repositoryId) {

        Map<String, Memory> uniqueMemories = new LinkedHashMap<>();

        // Add semantic search results (by ID)
        for (SearchResult result : semanticResults) {
            try {
                UUID memId = UUID.fromString(result.getId());
                memoryRepository.findById(memId).ifPresent(m -> {
                    uniqueMemories.put(m.getId().toString(), m);
                });
            } catch (Exception e) {
                // ID might not be a valid UUID, skip
            }
        }

        // Add hot memories (will overwrite if duplicate)
        for (Memory m : hotMemories) {
            uniqueMemories.putIfAbsent(m.getId().toString(), m);
        }

        // Add project/repo scoped memories
        if (projectId != null) {
            memoryRepository.findByProjectId(UUID.fromString(projectId)).stream()
                .filter(m -> m.getStatus() != MemoryStatus.ARCHIVED)
                .forEach(m -> uniqueMemories.putIfAbsent(m.getId().toString(), m));
        }
        if (repositoryId != null) {
            memoryRepository.findByRepositoryId(UUID.fromString(repositoryId)).stream()
                .filter(m -> m.getStatus() != MemoryStatus.ARCHIVED)
                .forEach(m -> uniqueMemories.putIfAbsent(m.getId().toString(), m));
        }

        return new ArrayList<>(uniqueMemories.values());
    }

    private List<Memory> resolveContradictions(List<Memory> memories) {
        // Group by similar content (first 50 chars as rough topic key)
        Map<String, List<Memory>> byTopic = memories.stream()
            .filter(m -> m.getContent() != null)
            .collect(Collectors.groupingBy(
                m -> m.getContent().length() > 50
                    ? m.getContent().substring(0, 50).toLowerCase()
                    : m.getContent().toLowerCase()));

        List<Memory> resolved = new ArrayList<>();
        for (var entry : byTopic.entrySet()) {
            List<Memory> group = entry.getValue();
            if (group.size() == 1) {
                resolved.add(group.get(0));
            } else {
                // Keep the most confident/recent memory from each group
                Memory best = group.stream()
                    .max(Comparator
                        .comparing(Memory::getConfidence, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Memory::getLastSeenAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(group.get(0));
                resolved.add(best);
            }
        }
        return resolved;
    }

    private List<Memory> rankMemories(List<Memory> memories, QueryIntent intent) {
        return memories.stream()
            .sorted(Comparator
                .comparingDouble((Memory m) -> computeRelevanceScore(m, intent)).reversed()
                .thenComparing(Comparator.comparing(Memory::getConfidence,
                    Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .thenComparing(Comparator.comparing(Memory::getImportance,
                    Comparator.nullsFirst(Comparator.naturalOrder())).reversed()))
            .collect(Collectors.toList());
    }

    private double computeRelevanceScore(Memory memory, QueryIntent intent) {
        double score = 0.0;

        // Boost for matching keywords in content
        if (memory.getContent() != null && intent.keywords != null) {
            String lowerContent = memory.getContent().toLowerCase();
            long matches = intent.keywords.stream()
                .filter(lowerContent::contains)
                .count();
            score += matches * 0.2;
        }

        // Boost for active/confirmed memories
        if (memory.getStatus() == MemoryStatus.CONFIRMED ||
            memory.getStatus() == MemoryStatus.FREQUENTLY_USED ||
            memory.getStatus() == MemoryStatus.STABLE) {
            score += 0.3;
        }

        // Boost for high observation count
        if (memory.getObservationCount() != null) {
            score += Math.min(memory.getObservationCount() * 0.05, 0.5);
        }

        // Boost for recent access
        if (memory.getLastSeenAt() != null) {
            long daysSinceAccess = java.time.Duration.between(
                memory.getLastSeenAt(), LocalDateTime.now()).toDays();
            if (daysSinceAccess < 7) score += 0.4;
            else if (daysSinceAccess < 30) score += 0.2;
            else if (daysSinceAccess < 90) score += 0.1;
        }

        return score;
    }

    private ContextResponse buildResponse(
            String projectName, String repoName,
            List<Memory> memories, List<Map<String, Object>> graphResults,
            List<AgentEvent> events, List<Decision> decisions,
            List<Task> openTasks, List<AgentHandoff> handoffs,
            QueryIntent intent) {

        // Map memories to context items
        List<ContextResponse.ContextItem> relevantContext = memories.stream()
            .map(m -> ContextResponse.ContextItem.builder()
                .id(m.getId().toString())
                .type(m.getType() != null ? m.getType().name() : "UNKNOWN")
                .content(m.getContent())
                .score(m.getConfidence())
                .source(m.getSourceType())
                .build())
            .collect(Collectors.toList());

        // Map graph results to architecture items
        List<ContextResponse.ContextItem> architecture = graphResults.stream()
            .map(node -> ContextResponse.ContextItem.builder()
                .id(String.valueOf(node.getOrDefault("id", "")))
                .type(node.containsKey("labels") ? String.join(",", (List<String>) node.get("labels")) : "Node")
                .content(node.toString())
                .build())
            .collect(Collectors.toList());

        // Map events to recent changes
        List<ContextResponse.ContextItem> recentChanges = events.stream()
            .limit(10)
            .map(e -> ContextResponse.ContextItem.builder()
                .id(e.getId().toString())
                .type(e.getEventType() != null ? e.getEventType().name() : "EVENT")
                .content(e.getDescription())
                .source(e.getFilePath())
                .build())
            .collect(Collectors.toList());

        // Map decisions
        List<ContextResponse.DecisionSummary> decisionSummaries = decisions.stream()
            .limit(10)
            .map(d -> ContextResponse.DecisionSummary.builder()
                .id(d.getId().toString())
                .title(d.getTitle())
                .rationale(d.getRationale())
                .status(d.getStatus())
                .build())
            .collect(Collectors.toList());

        // Map tasks
        List<ContextResponse.TaskSummary> taskSummaries = openTasks.stream()
            .limit(10)
            .map(t -> ContextResponse.TaskSummary.builder()
                .id(t.getId().toString())
                .title(t.getTitle())
                .description(t.getDescription())
                .priority(t.getPriority())
                .status(t.getStatus() != null ? t.getStatus().name() : "OPEN")
                .build())
            .collect(Collectors.toList());

        // Extract known problems from handoffs
        List<ContextResponse.ContextItem> knownProblems = handoffs.stream()
            .filter(h -> h.getKnownIssues() != null && !h.getKnownIssues().isBlank())
            .map(h -> ContextResponse.ContextItem.builder()
                .id(h.getId().toString())
                .type("HANDOFF_ISSUE")
                .content(h.getKnownIssues())
                .source(h.getAgent() != null ? h.getAgent().getName() : null)
                .build())
            .collect(Collectors.toList());

        // Collect sources
        List<String> sources = new ArrayList<>();
        sources.add("semantic_search");
        sources.add("knowledge_graph");
        sources.add("recent_events");
        sources.add("decisions");
        sources.add("open_tasks");
        if (!handoffs.isEmpty()) sources.add("agent_handoffs");

        return ContextResponse.builder()
            .project(projectName)
            .repository(repoName)
            .relevantContext(relevantContext)
            .architecture(architecture)
            .previousAgents(Collections.emptyList())
            .recentChanges(recentChanges)
            .decisions(decisionSummaries)
            .openTasks(taskSummaries)
            .knownProblems(knownProblems)
            .developerPreferences(Collections.emptyList())
            .skills(Collections.emptyList())
            .sources(sources)
            .build();
    }

    private static class QueryIntent {
        String originalQuery;
        List<String> keywords;
        List<String> topics;
    }
}
