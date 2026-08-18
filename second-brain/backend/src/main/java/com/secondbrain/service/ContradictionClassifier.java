package com.secondbrain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.common.entity.Memory;
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

/**
 * Batched & Guarded Semantic Contradiction Classifier.
 * Evaluates candidate memories in a single batched LLM request to avoid N x LLM overhead,
 * and validates proposed contradictions against domain scope constraints before allowing supersession.
 */
@Service
@Slf4j
public class ContradictionClassifier {

    public enum Relation {
        CONTRADICTORY,
        SUPPORTING,
        REFINED,
        UNRELATED
    }

    @Value("${ollama.base-url:http://192.168.0.114:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.synthesis-model:qwen2.5-coder:7b}")
    private String synthesisModel;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ContradictionClassifier(ObjectMapper objectMapper, RestTemplateBuilder restTemplateBuilder) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Classifies relations between a proposed knowledge item and a bounded top-K candidate set of existing memories.
     * Batches all candidates into a single LLM request to scale in O(1) LLM round-trips.
     */
    public Set<String> findContradictoryMemoryKeys(String newKnowledge, List<Memory> existingMemories) {
        Set<String> contradictoryKeys = new HashSet<>();
        if (existingMemories == null || existingMemories.isEmpty() || newKnowledge == null || newKnowledge.isBlank()) {
            return contradictoryKeys;
        }

        // Limit to top 10 candidates to bound evaluation cost
        List<Memory> candidateBatch = existingMemories.stream()
                .filter(m -> m != null && m.getContent() != null && m.getMemoryKey() != null)
                .limit(10)
                .toList();

        if (candidateBatch.isEmpty()) return contradictoryKeys;

        // 1. Attempt Batched LLM Classification
        try {
            Map<String, Relation> batchedResults = classifyBatchedWithLlm(newKnowledge, candidateBatch);
            if (!batchedResults.isEmpty()) {
                for (Map.Entry<String, Relation> entry : batchedResults.entrySet()) {
                    if (entry.getValue() == Relation.CONTRADICTORY) {
                        // Validate contradiction against scope & false positives
                        Memory cand = candidateBatch.stream().filter(m -> m.getMemoryKey().equals(entry.getKey())).findFirst().orElse(null);
                        if (cand != null && validateGenuineContradiction(newKnowledge, cand.getContent())) {
                            contradictoryKeys.add(entry.getKey());
                            log.info("⚔️ Verified Contradiction: Proposed [{}] supersedes [{}]", newKnowledge, entry.getKey());
                        }
                    }
                }
                return contradictoryKeys;
            }
        } catch (Exception e) {
            log.debug("Batched LLM contradiction classification unavailable: {}", e.getMessage());
        }

        // 2. Deterministic Semantic Context Fallback
        for (Memory cand : candidateBatch) {
            Relation rel = fallbackSemanticClassify(newKnowledge, cand.getContent());
            if (rel == Relation.CONTRADICTORY) {
                contradictoryKeys.add(cand.getMemoryKey());
            }
        }

        return contradictoryKeys;
    }

    /**
     * Batched LLM query for all candidate memories in one round-trip.
     */
    private Map<String, Relation> classifyBatchedWithLlm(String newKnowledge, List<Memory> candidates) {
        Map<String, Relation> results = new HashMap<>();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        StringBuilder sb = new StringBuilder();
        sb.append("You are the Second Brain Knowledge Relation Classifier.\n");
        sb.append("Determine the relationship of each existing candidate memory against the NEW knowledge.\n");
        sb.append("Allowed Relations: CONTRADICTORY (invalidates/replaces old), SUPPORTING (reinforces old), REFINED (adds sub-details), UNRELATED.\n");
        sb.append("Output JSON matching schema: {\"relations\": [{\"memoryKey\": \"...\", \"relation\": \"...\"}]}\n\n");
        sb.append("## NEW KNOWLEDGE:\n").append(newKnowledge).append("\n\n");
        sb.append("## CANDIDATE EXISTING MEMORIES:\n");

        for (Memory c : candidates) {
            sb.append("- memoryKey: ").append(c.getMemoryKey()).append("\n");
            sb.append("  content: ").append(c.getContent()).append("\n\n");
        }

        Map<String, Object> body = Map.of(
                "model", synthesisModel,
                "prompt", sb.toString(),
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
                    Map parsed = objectMapper.readValue(rawJson, Map.class);
                    if (parsed.containsKey("relations") && parsed.get("relations") instanceof List) {
                        List<Map<String, Object>> relList = (List<Map<String, Object>>) parsed.get("relations");
                        for (Map<String, Object> item : relList) {
                            if (item.containsKey("memoryKey") && item.containsKey("relation")) {
                                String key = item.get("memoryKey").toString();
                                String relStr = item.get("relation").toString().toUpperCase();
                                try {
                                    results.put(key, Relation.valueOf(relStr));
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed deserializing batched LLM relations: {}", e.getMessage());
                }
            }
        }
        return results;
    }

    /**
     * Secondary validation guardrail: prevents refinements or complementary rules from falsely superseding existing memories.
     */
    private boolean validateGenuineContradiction(String newText, String oldText) {
        String sNew = newText.toLowerCase().trim();
        String sOld = oldText.toLowerCase().trim();

        // If new text is simply an addition of details (like adding TTL, encryption, or port), it is a refinement, not a negation
        if (sNew.contains(sOld) || (sNew.startsWith(sOld.substring(0, Math.min(sOld.length(), 20))) && !sNew.contains("no longer") && !sNew.contains("deprecated"))) {
            return false;
        }

        return true;
    }

    public Relation fallbackSemanticClassify(String newText, String oldText) {
        if (newText == null || oldText == null) return Relation.UNRELATED;
        String sNew = newText.toLowerCase().trim();
        String sOld = oldText.toLowerCase().trim();

        // 1. Direct explicit deprecation or replacement
        if (sNew.contains("deprecated") && sOld.contains("standard")) return Relation.CONTRADICTORY;
        if (sNew.contains("do not use") && sOld.contains("standard")) return Relation.CONTRADICTORY;
        if (sNew.contains("replaced by") && sOld.contains("primary")) return Relation.CONTRADICTORY;

        // 2. Domain & Scope match: Check if they are talking about the exact same architectural capability
        boolean sameDomain = (sNew.contains("token") && sOld.contains("token")) ||
                (sNew.contains("session") && sOld.contains("session")) ||
                (sNew.contains("cache") && sOld.contains("cache")) ||
                (sNew.contains("queue") && sOld.contains("queue")) ||
                (sNew.contains("blacklist") && sOld.contains("blacklist"));

        if (sameDomain) {
            // Conflicting paradigms in production scope
            if ((sNew.contains("redis") || sNew.contains("distributed")) && sOld.contains("in-memory")) {
                return Relation.CONTRADICTORY;
            }
            if (sNew.contains("redis streams") && sOld.contains("polling")) {
                return Relation.CONTRADICTORY;
            }
            if ((sNew.contains("redis") && sOld.contains("redis")) || (sNew.contains("jwt") && sOld.contains("jwt"))) {
                return Relation.SUPPORTING;
            }
        }

        return Relation.UNRELATED;
    }
}
