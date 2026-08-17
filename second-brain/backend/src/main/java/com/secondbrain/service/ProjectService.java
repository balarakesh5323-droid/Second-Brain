package com.secondbrain.service;

import com.secondbrain.common.entity.Project;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    public List<Project> getAll() {
        return projectRepository.findAll();
    }

    public Project getById(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    @Transactional
    public Project create(String name, String description, String path) {
        Project project = Project.builder()
                .name(name)
                .description(description)
                .path(path)
                .build();
        return projectRepository.save(project);
    }

    @Transactional
    public Project update(UUID id, String name, String description, String path) {
        Project project = getById(id);
        if (name != null) project.setName(name);
        if (description != null) project.setDescription(description);
        if (path != null) project.setPath(path);
        return projectRepository.save(project);
    }

    @Transactional
    public void delete(UUID id) {
        Project project = getById(id);
        projectRepository.delete(project);
    }
}
