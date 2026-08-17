package com.secondbrain.service;

import com.secondbrain.common.entity.Decision;
import com.secondbrain.common.repository.DecisionRepository;
import com.secondbrain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DecisionService {

    private final DecisionRepository decisionRepository;

    public List<Decision> getAll() {
        return decisionRepository.findAll();
    }

    public Decision getById(UUID id) {
        return decisionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Decision", id));
    }

    public List<Decision> getByProject(UUID projectId) {
        return decisionRepository.findByProjectId(projectId);
    }

    public List<Decision> getRecent() {
        return decisionRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @Transactional
    public Decision create(Decision decision) {
        return decisionRepository.save(decision);
    }

    @Transactional
    public Decision update(UUID id, Decision updated) {
        Decision decision = getById(id);
        if (updated.getTitle() != null) decision.setTitle(updated.getTitle());
        if (updated.getDescription() != null) decision.setDescription(updated.getDescription());
        if (updated.getRationale() != null) decision.setRationale(updated.getRationale());
        if (updated.getStatus() != null) decision.setStatus(updated.getStatus());
        if (updated.getTags() != null) decision.setTags(updated.getTags());
        return decisionRepository.save(decision);
    }

    @Transactional
    public void delete(UUID id) {
        Decision decision = getById(id);
        decisionRepository.delete(decision);
    }
}
