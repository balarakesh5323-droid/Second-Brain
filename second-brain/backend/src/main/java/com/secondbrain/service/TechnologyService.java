package com.secondbrain.service;

import com.secondbrain.common.entity.Technology;
import com.secondbrain.common.repository.TechnologyRepository;
import com.secondbrain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TechnologyService {

    private final TechnologyRepository technologyRepository;

    public List<Technology> getAll() {
        return technologyRepository.findAll();
    }

    public Technology getById(UUID id) {
        return technologyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Technology", id));
    }

    public Technology getByName(String name) {
        return technologyRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Technology with name: " + name));
    }

    @Transactional
    public Technology create(Technology technology) {
        return technologyRepository.save(technology);
    }

    @Transactional
    public Technology update(UUID id, Technology updated) {
        Technology technology = getById(id);
        if (updated.getName() != null) technology.setName(updated.getName());
        if (updated.getCategory() != null) technology.setCategory(updated.getCategory());
        if (updated.getVersion() != null) technology.setVersion(updated.getVersion());
        if (updated.getDescription() != null) technology.setDescription(updated.getDescription());
        return technologyRepository.save(technology);
    }

    @Transactional
    public void delete(UUID id) {
        Technology technology = getById(id);
        technologyRepository.delete(technology);
    }
}
