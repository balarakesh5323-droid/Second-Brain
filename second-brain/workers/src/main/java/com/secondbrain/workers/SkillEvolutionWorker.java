package com.secondbrain.workers;

import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.entity.Skill;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.repository.MemoryRepository;
import com.secondbrain.common.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SkillEvolutionWorker {

    private final SkillRepository skillRepository;
    private final MemoryRepository memoryRepository;

    private static final int USAGE_CONFIDENCE_BOOST_THRESHOLD = 3;
    private static final double MAX_CONFIDENCE = 1.0;
    private static final double CONFIDENCE_BOOST = 0.05;
    private static final double CONFIDENCE_DECAY = 0.02;

    @Scheduled(fixedDelay = 604800000)
    public void evolveSkills() {
        log.info("Running skill evolution worker...");

        List<Skill> skills = skillRepository.findAll();
        List<Memory> recentMemories = memoryRepository.findAll().stream()
            .filter(m -> m.getStatus() != MemoryStatus.ARCHIVED)
            .filter(m -> m.getStatus() != MemoryStatus.SUPERSEDED)
            .filter(m -> m.getLastSeenAt() != null)
            .filter(m -> m.getLastSeenAt().isAfter(LocalDateTime.now().minusDays(30)))
            .toList();

        // Build a frequency map of memory content keywords
        Map<String, Integer> keywordFrequency = new HashMap<>();
        for (Memory memory : recentMemories) {
            if (memory.getContent() == null) continue;
            String[] words = memory.getContent().toLowerCase().split("\\s+");
            for (String word : words) {
                if (word.length() > 3) {
                    keywordFrequency.merge(word, 1, Integer::sum);
                }
            }
        }

        int updated = 0;

        for (Skill skill : skills) {
            boolean usedRecently = skill.getLastUsedAt() != null &&
                skill.getLastUsedAt().isAfter(LocalDateTime.now().minusDays(30));

            // Boost confidence for frequently used skills
            if (skill.getUsageCount() != null && skill.getUsageCount() >= USAGE_CONFIDENCE_BOOST_THRESHOLD) {
                double currentConf = skill.getConfidence() != null ? skill.getConfidence() : 0.5;
                double newConf = Math.min(MAX_CONFIDENCE, currentConf + CONFIDENCE_BOOST);
                if (newConf != currentConf) {
                    skill.setConfidence(newConf);
                    updated++;
                }
            }

            // Decay confidence for unused skills
            if (!usedRecently && skill.getUsageCount() != null && skill.getUsageCount() == 0) {
                double currentConf = skill.getConfidence() != null ? skill.getConfidence() : 0.5;
                double newConf = Math.max(0.1, currentConf - CONFIDENCE_DECAY);
                if (newConf != currentConf) {
                    skill.setConfidence(newConf);
                    updated++;
                }
            }

            // Update skill triggers based on recent memory keywords
            if (skill.getTriggers() != null && !skill.getTriggers().isEmpty()) {
                Set<String> newTriggers = new HashSet<>(skill.getTriggers());
                boolean triggersUpdated = false;

                for (var entry : keywordFrequency.entrySet()) {
                    if (entry.getValue() >= 3 && !newTriggers.contains(entry.getKey())) {
                        // Only add if related to existing triggers
                        boolean related = newTriggers.stream()
                            .anyMatch(t -> t.contains(entry.getKey()) || entry.getKey().contains(t));
                        if (related) {
                            newTriggers.add(entry.getKey());
                            triggersUpdated = true;
                        }
                    }
                }

                if (triggersUpdated) {
                    skill.setTriggers(newTriggers);
                    updated++;
                }
            }

            skillRepository.save(skill);
        }

        log.info("Skill evolution completed: {} skills updated", updated);
    }
}
