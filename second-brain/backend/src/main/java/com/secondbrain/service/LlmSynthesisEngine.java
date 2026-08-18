package com.secondbrain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.common.dto.AgentProvenance;
import com.secondbrain.common.dto.KnowledgeProposal;
import com.secondbrain.common.entity.AgentAttempt;
import com.secondbrain.common.entity.Decision;
import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LlmSynthesisEngine {

    @Value("${ollama.base-url:http://192.168.0.114:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.synthesis-model:qwen2.5-coder:7b}")
    private String synthesisModel;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final ContradictionClassifier contradictionClassifier;

    public LlmSynthesisEngine(ObjectMapper objectMapper, RestTemplateBuilder restTemplateBuilder, ContradictionClassifier contradictionClassifier) {
        this.objectMapper = objectMapper;
        this.contradictionClassifier = contradictionClassifier;
        // Configure connect and read timeouts to prevent scheduled job hangs
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * Synthesizes architectural knowledge proposal from decisions, existing memories, and full Neo4j graph neighborhood.
     */
    public Optional<KnowledgeProposal> synthesizeArchitecturalProposal(
            String topic, String projectKey, List<Decision> decisions, List<Memory> existingMemories, List<Map<String, Object>> graphNeighborhood) {

        if (decisions == null || decisions.isEmpty()) return Optional.empty();

        // 1. Try LLM Generation via Ollama with full Graph & Memory context
        try {
            Optional<KnowledgeProposal> llmResult = generateWithLlm(topic, projectKey, decisions, existingMemories, graphNeighborhood);
            if (llmResult.isPresent()) return llmResult;
        } catch (Exception e) {
            log.debug("LLM synthesis via Ollama unavailable/failed, using conservative semantic fallback: {}", e.getMessage());
        }

        // 2. Conservative Fallback Engine (Emits PROPOSED with provenances)
        return fallbackSemanticArchitecturalSynthesis(topic, projectKey, decisions, existingMemories);
    }

    /**
     * Synthesizes failure anti-pattern proposal from attempts and existing memories.
     */
    public Optional<KnowledgeProposal> synthesizeFailureAntiPatternProposal(
            String topic, String projectKey, List<AgentAttempt> failures, List<Memory> existingMemories) {

        if (failures == null || failures.isEmpty()) return Optional.empty();

        Set<String> evidence = failures.stream()
                .filter(f -> f.getId() != null)
                .map(f -> "attempt:" + f.getId())
                .collect(Collectors.toSet());

        List<AgentProvenance> provenances = new ArrayList<>();
        for (AgentAttempt fa : failures) {
            provenances.add(AgentProvenance.builder()
                    .agentName(fa.getAgentName() != null ? fa.getAgentName() : "Agent")
                    .repositoryName(fa.getRepository() != null ? fa.getRepository().getName() : "repo")
                    .actionType("FAILED_ATTEMPT")
                    .timestamp(fa.getCreatedAt() != null ? fa.getCreatedAt() : java.time.LocalDateTime.now())
                    .build());
        }

        AgentAttempt sample = failures.get(0);
        String lesson = sample.getLessonLearned() != null ? sample.getLessonLearned() : "Approach failed under stress/scale";
        String approach = sample.getApproach() != null ? sample.getApproach() : "Trial approach";

        String knowledge = String.format("Anti-Pattern Prevention [%s]: Approach '%s' failed. Lesson: %s", topic, approach, lesson);
        String memoryKey = "ANTI_PATTERN:" + projectKey + ":" + topic.toUpperCase().replaceAll("[^A-Z0-9]", "_");

        Set<String> supersedes = contradictionClassifier.findContradictoryMemoryKeys(knowledge, existingMemories);

        return Optional.of(KnowledgeProposal.builder()
                .memoryKey(memoryKey)
                .knowledge(knowledge)
                .type(MemoryType.PROCEDURAL)
                .status(MemoryStatus.PROPOSED) // Always conservative initially
                .confidence(0.65)
                .evidenceSources(evidence)
                .supersedesMemoryKeys(supersedes)
                .projectKey(projectKey)
                .provenances(provenances)
                .reasoning(String.format("Derived from %d failed trial(s) and engineering post-mortems.", failures.size()))
                .suggestedTags(new HashSet<>(Set.of(topic.toLowerCase(), "anti-pattern", "failure-prevention", "learned-rule")))
                .build());
    }

    private Optional<KnowledgeProposal> generateWithLlm(
            String topic, String projectKey, List<Decision> decisions, List<Memory> existingMemories, List<Map<String, Object>> graphNeighborhood) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String prompt = buildGroundedPrompt(topic, projectKey, decisions, existingMemories, graphNeighborhood);
        Map<String, Object> body = Map.of(
                "model", synthesisModel,
                "prompt", prompt,
                "format", "json",
                "stream", false
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                ollamaBaseUrl + "/api/generate", request, Map.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String rawJson = (String) response.getBody().get("response");
            if (rawJson != null && !rawJson.isBlank()) {
                try {
                    KnowledgeProposal proposal = objectMapper.readValue(rawJson, KnowledgeProposal.class);
                    if (proposal != null) {
                        proposal.setProjectKey(projectKey);
                        if (proposal.getMemoryKey() == null || proposal.getMemoryKey().isBlank()) {
                            proposal.setMemoryKey("ARCHITECTURAL_STANDARD:" + projectKey + ":" + topic.toUpperCase().replaceAll("[^A-Z0-9]", "_"));
                        }
                        // Attach concrete provenances
                        attachDecisionProvenances(proposal, decisions);
                        return Optional.of(proposal);
                    }
                } catch (Exception e) {
                    log.warn("Failed deserializing LLM JSON proposal: {}", e.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    private Optional<KnowledgeProposal> fallbackSemanticArchitecturalSynthesis(
            String topic, String projectKey, List<Decision> decisions, List<Memory> existingMemories) {

        Set<String> evidence = decisions.stream()
                .filter(d -> d.getId() != null)
                .map(d -> "decision:" + d.getId())
                .collect(Collectors.toSet());

        List<AgentProvenance> provenances = new ArrayList<>();
        for (Decision d : decisions) {
            provenances.add(AgentProvenance.builder()
                    .agentName("Agent")
                    .repositoryName(d.getRepository() != null ? d.getRepository().getName() : "repo")
                    .actionType("DECISION_APPROVED")
                    .timestamp(d.getCreatedAt() != null ? d.getCreatedAt() : java.time.LocalDateTime.now())
                    .build());
        }

        Decision sample = decisions.get(0);
        String rationale = sample.getRationale() != null ? sample.getRationale() : sample.getTitle();
        String knowledge = String.format("Architectural Standard [%s]: %s (%s).", topic, sample.getTitle(), rationale);
        String memoryKey = "ARCHITECTURAL_STANDARD:" + projectKey + ":" + topic.toUpperCase().replaceAll("[^A-Z0-9]", "_");

        Set<String> supersedes = contradictionClassifier.findContradictoryMemoryKeys(knowledge, existingMemories);

        return Optional.of(KnowledgeProposal.builder()
                .memoryKey(memoryKey)
                .knowledge(knowledge)
                .type(MemoryType.ARCHITECTURAL)
                .status(MemoryStatus.PROPOSED) // Fallback defaults strictly to conservative PROPOSED
                .confidence(0.60)
                .evidenceSources(evidence)
                .supersedesMemoryKeys(supersedes)
                .projectKey(projectKey)
                .provenances(provenances)
                .reasoning(String.format("Synthesized across %d architectural decision(s).", decisions.size()))
                .suggestedTags(new HashSet<>(Set.of(topic.toLowerCase(), "architectural-standard", "consolidated")))
                .build());
    }

    private void attachDecisionProvenances(KnowledgeProposal proposal, List<Decision> decisions) {
        for (Decision d : decisions) {
            proposal.getProvenances().add(AgentProvenance.builder()
                    .agentName("Agent")
                    .repositoryName(d.getRepository() != null ? d.getRepository().getName() : "repo")
                    .actionType("ARCHITECTURAL_DECISION")
                    .timestamp(d.getCreatedAt() != null ? d.getCreatedAt() : java.time.LocalDateTime.now())
                    .build());
        }
    }

    private String buildGroundedPrompt(
            String topic, String projectKey, List<Decision> decisions, List<Memory> existingMemories, List<Map<String, Object>> graphNeighborhood) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are the Second Brain AI Knowledge Consolidator.\n");
        sb.append("CRITICAL RULES:\n");
        sb.append("1. You may ONLY synthesize facts explicitly grounded in the provided Evidence and Graph Relationships.\n");
        sb.append("2. Never invent technologies, evidence IDs, or unsubstantiated conclusions.\n");
        sb.append("3. Cite all supporting evidence IDs in the 'evidenceSources' array (e.g. [\"decision:<id>\"]).\n");
        sb.append("4. If the new standard contradicts an existing memory, list its memoryKey in 'supersedesMemoryKeys'.\n");
        sb.append("5. Output ONLY valid JSON matching this schema:\n");
        sb.append("{\n");
        sb.append("  \"knowledge\": \"...\",\n");
        sb.append("  \"type\": \"ARCHITECTURAL\",\n");
        sb.append("  \"evidenceSources\": [\"decision:uuid-1\", \"decision:uuid-2\"],\n");
        sb.append("  \"supersedesMemoryKeys\": [],\n");
        sb.append("  \"reasoning\": \"...\",\n");
        sb.append("  \"suggestedTags\": [\"...\"]\n");
        sb.append("}\n\n");

        sb.append("## DOMAIN TOPIC: ").append(topic).append(" (Project: ").append(projectKey).append(")\n\n");

        sb.append("## EMPIRICAL EVIDENCE (DECISIONS):\n");
        for (Decision d : decisions) {
            sb.append("- Decision ID: decision:").append(d.getId()).append("\n");
            sb.append("  Title: ").append(d.getTitle()).append("\n");
            sb.append("  Rationale: ").append(d.getRationale()).append("\n");
            sb.append("  Repo: ").append(d.getRepository() != null ? d.getRepository().getName() : "global").append("\n\n");
        }

        if (graphNeighborhood != null && !graphNeighborhood.isEmpty()) {
            sb.append("## KNOWLEDGE GRAPH RELATIONSHIPS (NEO4J):\n");
            for (Map<String, Object> rel : graphNeighborhood) {
                sb.append("- ").append(rel.toString()).append("\n");
            }
            sb.append("\n");
        }

        if (existingMemories != null && !existingMemories.isEmpty()) {
            sb.append("## EXISTING MEMORIES (POTENTIAL CONTRADICTIONS):\n");
            for (Memory m : existingMemories) {
                sb.append("- MemoryKey: ").append(m.getMemoryKey()).append("\n");
                sb.append("  Content: ").append(m.getContent()).append("\n");
                sb.append("  Status: ").append(m.getStatus()).append("\n\n");
            }
        }

        return sb.toString();
    }
}
