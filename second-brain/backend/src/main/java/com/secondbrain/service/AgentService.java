package com.secondbrain.service;

import com.secondbrain.common.entity.Agent;
import com.secondbrain.common.repository.AgentRepository;
import com.secondbrain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;

    public List<Agent> getAll() {
        return agentRepository.findAll();
    }

    public Agent getById(UUID id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", id));
    }

    public Agent getByName(String name) {
        return agentRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Agent with name: " + name));
    }

    @Transactional
    public Agent create(Agent agent) {
        return agentRepository.save(agent);
    }

    @Transactional
    public Agent update(UUID id, Agent updated) {
        Agent agent = getById(id);
        if (updated.getName() != null) agent.setName(updated.getName());
        if (updated.getType() != null) agent.setType(updated.getType());
        if (updated.getModel() != null) agent.setModel(updated.getModel());
        if (updated.getCapabilities() != null) agent.setCapabilities(updated.getCapabilities());
        return agentRepository.save(agent);
    }

    @Transactional
    public void delete(UUID id) {
        Agent agent = getById(id);
        agentRepository.delete(agent);
    }
}
