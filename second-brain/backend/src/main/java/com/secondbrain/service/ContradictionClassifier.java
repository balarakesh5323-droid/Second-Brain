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
 * Intelligent Semantic Contradiction Classifier.
 * Classifies relationships between new knowledge and existing memories (CONTRADICTORY, SUPPORTING, REFINED, UNRELATED)
 * via LLM analysis or contextual semantic reasoning.
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
     * Classifies relations between a proposed knowledge item and existing memories.
     * Returns the set of memory keys that are genuinely CONTRADICTORY and should be superseded.
     */
    public Set<String> findContradictoryMemoryKeys(String newKnowledge, List<Memory> existingMemories) {
        Set<String> contradictoryKeys = new HashSet<>();
        if (existingMemories == null || existingMemories.isEmpty() || newKnowledge == null) {
            return contradictoryKeys;
        }

        for (Memory existing : existingMemories) {
            if (existing.getContent() == null || existing.getMemoryKey() == null) continue;
            Relation relation = classify(newKnowledge, existing.getContent());
            if (relation == Relation.CONTRADICTORY) {
                contradictoryKeys.add(existing.getMemoryKey());
                log.info("⚔️ Semantic Contradiction detected: Proposed [{}] CONTRADICTS existing [{}]",
                        newKnowledge, existing.getContent());
            }
        }
        return contradictoryKeys;
    }

    /**
     * Classifies the relationship between two knowledge statements via LLM or semantic domain reasoning.
     */
    public Relation classify(String newText, String oldText) {
        if (newText == null || oldText == null) return Relation.UNRELATED;

        // 1. Try LLM semantic classification if available
        try {
            Optional<Relation> llmRelation = classifyWithLlm(newText, oldText);
            if (llmRelation.isPresent()) {
                return llmRelation.get();
            }
        } catch (Exception e) {
            log.debug("LLM contradiction classification unavailable, using semantic context reasoner: {}", e.getMessage());
        }

        // 2. Semantic Context & Scope Fallback Reasoner
        return fallbackSemanticClassify(newText, oldText);
    }

    private Optional<Relation> classifyWithLlm(String newText, String oldText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String prompt = "You are a semantic knowledge classifier.\n" +
                "Classify the relationship between Statement A (New) and Statement B (Existing).\n" +
                "Options: CONTRADICTORY, SUPPORTING, REFINED, UNRELATED.\n" +
                "Return JSON: {\"relation\": \"CONTRADICTORY\"}\n\n" +
                "Statement A: " + newText + "\n" +
                "Statement B: " + oldText;

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
                    Map parsed = objectMapper.readValue(rawJson, Map.class);
                    if (parsed.containsKey("relation")) {
                        String relStr = parsed.get("relation").toString().toUpperCase();
                        return Optional.of(Relation.valueOf(relStr));
                    }
                } catch (Exception e) {
                    log.warn("Failed deserializing LLM relation: {}", e.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    private Relation fallbackSemanticClassify(String newText, String oldText) {
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
