package com.secondbrain.service;

import com.secondbrain.common.entity.Skill;
import com.secondbrain.common.repository.SkillRepository;
import com.secondbrain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;

    @Transactional
    public Skill createSkill(Skill skill) {
        skill.setUsageCount(0);
        skill.setLastUsedAt(LocalDateTime.now());
        return skillRepository.save(skill);
    }

    public List<Skill> getAllSkills() {
        return skillRepository.findAll();
    }

    public Skill getById(UUID id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Skill", id));
    }

    public Skill getByName(String name) {
        Skill skill = skillRepository.findByName(name);
        if (skill == null) {
            throw new ResourceNotFoundException("Skill with name: " + name);
        }
        return skill;
    }

    public List<Skill> getByScope(String scope) {
        return skillRepository.findByScope(scope);
    }

    public List<Skill> matchSkills(String trigger) {
        return skillRepository.findByTriggersContaining(trigger);
    }

    @Transactional
    public Skill incrementUsage(UUID id) {
        Skill skill = getById(id);
        skill.setUsageCount(skill.getUsageCount() + 1);
        skill.setLastUsedAt(LocalDateTime.now());
        return skillRepository.save(skill);
    }

    @Transactional
    public Skill updateSkill(UUID id, Skill updated) {
        Skill skill = getById(id);
        if (updated.getName() != null) skill.setName(updated.getName());
        if (updated.getDescription() != null) skill.setDescription(updated.getDescription());
        if (updated.getVersion() != null) skill.setVersion(updated.getVersion());
        if (updated.getConfidence() != null) skill.setConfidence(updated.getConfidence());
        if (updated.getTriggers() != null) skill.setTriggers(updated.getTriggers());
        if (updated.getKnowledge() != null) skill.setKnowledge(updated.getKnowledge());
        if (updated.getScope() != null) skill.setScope(updated.getScope());
        return skillRepository.save(skill);
    }

    @Transactional
    public void deleteSkill(UUID id) {
        Skill skill = getById(id);
        skillRepository.delete(skill);
    }
}
