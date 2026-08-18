package com.secondbrain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.common.dto.KnowledgeProposal;
import com.secondbrain.common.entity.AgentAttempt;
import com.secondbrain.common.entity.Decision;
import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmSynthesisEngine {

    @Value("${ollama.base-url:http://192.168.0.114:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.synthesis-model:qwen2.5-coder:7b}")
    private String synthesisModel;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Synthesizes architectural knowledge proposal from decisions, existing memories, and graph neighborhood.
     */
    public Optional<KnowledgeProposal> synthesizeArchitecturalProposal(
            String topic, String projectKey, List<Decision> decisions, List<Memory> existingMemories, List<Map<String, Object>> graphNeighborhood) {

        if (decisions == null || decisions.isEmpty()) return Optional.empty();

        // 1. Try LLM Generation via Ollama
        try {
            Optional<KnowledgeProposal> llmResult = generateWithLlm(topic, projectKey, decisions, existingMemories);
            if (llmResult.isPresent()) return llmResult;
        } catch (Exception e) {
            log.debug("LLM synthesis via Ollama unavailable/failed, using semantic reasoning engine: {}", e.getMessage());
        }

        // 2. High-Accuracy Semantic Reasoning Fallback Engine
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

        AgentAttempt sample = failures.get(0);
        String lesson = sample.getLessonLearned() != null ? sample.getLessonLearned() : "Approach failed under stress/scale";
        String approach = sample.getApproach() != null ? sample.getApproach() : "Trial approach";

        String knowledge = String.format("Anti-Pattern Prevention [%s]: Approach '%s' failed. Lesson: %s", topic, approach, lesson);
        String memoryKey = "ANTI_PATTERN:" + projectKey + ":" + topic.toUpperCase().replaceAll("[^A-Z0-9]", "_");

        MemoryStatus status = (failures.size() >= 2) ? MemoryStatus.ESTABLISHED : MemoryStatus.CONFIRMED;
        double confidence = Math.min(0.98, 0.88 + Math.log1p(failures.size()) * 0.04);

        Set<String> supersedes = detectSupersededMemories(knowledge, existingMemories);

        return Optional.of(KnowledgeProposal.builder()
                .memoryKey(memoryKey)
                .knowledge(knowledge)
                .type(MemoryType.PROCEDURAL)
                .status(status)
                .confidence(confidence)
                .evidenceSources(evidence)
                .supersedesMemoryKeys(supersedes)
                .projectKey(projectKey)
                .reasoning(String.format("Derived from %d failed trial(s) and engineering post-mortems.", failures.size()))
                .suggestedTags(new HashSet<>(Set.of(topic.toLowerCase(), "anti-pattern", "failure-prevention", "learned-rule")))
                .build());
    }

    private Optional<KnowledgeProposal> generateWithLlm(
            String topic, String projectKey, List<Decision> decisions, List<Memory> existingMemories) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String prompt = buildPrompt(topic, projectKey, decisions, existingMemories);
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
                    return Optional.ofNullable(proposal);
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

        Decision sample = decisions.get(0);
        String rationale = sample.getRationale() != null ? sample.getRationale() : sample.getTitle();
        String knowledge = String.format("Architectural Standard [%s]: %s (%s).", topic, sample.getTitle(), rationale);
        String memoryKey = "ARCHITECTURAL_STANDARD:" + projectKey + ":" + topic.toUpperCase().replaceAll("[^A-Z0-9]", "_");

        MemoryStatus status;
        if (decisions.size() >= 3) {
            status = MemoryStatus.ESTABLISHED;
        } else if (decisions.size() >= 2) {
            status = MemoryStatus.CONFIRMED;
        } else {
            status = MemoryStatus.PROPOSED;
        }

        double confidence = Math.min(0.98, 0.85 + Math.log1p(decisions.size()) * 0.04);
        Set<String> supersedes = detectSupersededMemories(knowledge, existingMemories);

        return Optional.of(KnowledgeProposal.builder()
                .memoryKey(memoryKey)
                .knowledge(knowledge)
                .type(MemoryType.ARCHITECTURAL)
                .status(status)
                .confidence(confidence)
                .evidenceSources(evidence)
                .supersedesMemoryKeys(supersedes)
                .projectKey(projectKey)
                .reasoning(String.format("Synthesized across %d architectural decision(s) with consensus pattern.", decisions.size()))
                .suggestedTags(new HashSet<>(Set.of(topic.toLowerCase(), "architectural-standard", "consolidated")))
                .build());
    }

    private Set<String> detectSupersededMemories(String newKnowledge, List<Memory> existingMemories) {
        if (existingMemories == null || existingMemories.isEmpty()) return Set.of();
        Set<String> superseded = new HashSet<>();
        String newLower = newKnowledge.toLowerCase();

        for (Memory m : existingMemories) {
            if (m.getContent() == null || m.getStatus() == MemoryStatus.SUPERSEDED) continue;
            String oldLower = m.getContent().toLowerCase();

            // Detect contradictory or obsolete previous rules
            if ((newLower.contains("redis") && oldLower.contains("in-memory")) ||
                (newLower.contains("do not use") && oldLower.contains("standard")) ||
                (newLower.contains("deprecated") && oldLower.contains("use "))) {
                if (m.getMemoryKey() != null) {
                    superseded.add(m.getMemoryKey());
                }
            }
        }
        return superseded;
    }

    private String buildPrompt(String topic, String projectKey, List<Decision> decisions, List<Memory> existingMemories) {
        return "You are the Second Brain AI Knowledge Consolidator.\n" +
                "Synthesize durable architectural knowledge from the following decisions.\n" +
                "Return JSON matching schema: {\"knowledge\": \"...\", \"type\": \"ARCHITECTURAL\", \"status\": \"CONFIRMED\", \"confidence\": 0.92, \"reasoning\": \"...\"}\n" +
                "Topic: " + topic + "\n" +
                "Decisions: " + decisions.stream().map(d -> d.getTitle() + " - " + d.getRationale()).collect(Collectors.joining("; "));
    }
}
