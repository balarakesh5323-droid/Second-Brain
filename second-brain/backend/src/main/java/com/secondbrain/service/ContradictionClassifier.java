package com.secondbrain.service;

import com.secondbrain.common.entity.Memory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContradictionClassifier {

    public enum Relation {
        CONTRADICTORY,
        SUPPORTING,
        REFINED,
        UNRELATED
    }

    /**
     * Classifies relation between a proposed knowledge item and existing memories.
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
                log.info("⚔️ Contradiction detected: Proposed [{}] contradicts existing [{}]", newKnowledge, existing.getContent());
            }
        }
        return contradictoryKeys;
    }

    /**
     * Semantic and semantic-pattern relation classifier.
     */
    public Relation classify(String newText, String oldText) {
        if (newText == null || oldText == null) return Relation.UNRELATED;
        String sNew = newText.toLowerCase().trim();
        String sOld = oldText.toLowerCase().trim();

        // 1. Direct contradiction patterns (standard vs deprecated, do not use vs use)
        if (sNew.contains("deprecated") && sOld.contains("standard")) return Relation.CONTRADICTORY;
        if (sNew.contains("do not use") && sOld.contains("standard")) return Relation.CONTRADICTORY;
        if (sNew.contains("replaced by") && sOld.contains("primary")) return Relation.CONTRADICTORY;

        // 2. Paradigm shifts on the same architectural domain
        boolean sameDomain = (sNew.contains("token") && sOld.contains("token")) ||
                (sNew.contains("session") && sOld.contains("session")) ||
                (sNew.contains("cache") && sOld.contains("cache")) ||
                (sNew.contains("queue") && sOld.contains("queue"));

        if (sameDomain) {
            if ((sNew.contains("redis") || sNew.contains("distributed")) && sOld.contains("in-memory")) {
                return Relation.CONTRADICTORY;
            }
            if (sNew.contains("redis streams") && sOld.contains("polling")) {
                return Relation.CONTRADICTORY;
            }
        }

        // 3. Supporting / Reinforcing
        if (sameDomain && ((sNew.contains("redis") && sOld.contains("redis")) ||
                (sNew.contains("jwt") && sOld.contains("jwt")))) {
            return Relation.SUPPORTING;
        }

        return Relation.UNRELATED;
    }
}
