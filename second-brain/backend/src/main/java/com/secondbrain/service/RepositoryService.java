package com.secondbrain.service;

import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.common.repository.RepositoryEntityRepository;
import com.secondbrain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepositoryService {

    private final RepositoryEntityRepository repositoryEntityRepository;
    private final ProjectRepository projectRepository;

    public List<RepositoryEntity> getAll() {
        return repositoryEntityRepository.findAll();
    }

    public RepositoryEntity getById(UUID id) {
        return repositoryEntityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", id));
    }

    public List<RepositoryEntity> getByProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        return project.getRepositories();
    }

    @Transactional
    public RepositoryEntity create(String name, String url, String path, UUID projectId) {
        RepositoryEntity.RepositoryEntityBuilder builder = RepositoryEntity.builder()
                .name(name)
                .url(url)
                .path(path);

        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
            builder.project(project);
        }

        return repositoryEntityRepository.save(builder.build());
    }

    @Transactional
    public RepositoryEntity update(UUID id, String name, String url, String path, String defaultBranch, String primaryLanguage, String description) {
        RepositoryEntity repository = getById(id);
        if (name != null) repository.setName(name);
        if (url != null) repository.setUrl(url);
        if (path != null) repository.setPath(path);
        if (defaultBranch != null) repository.setDefaultBranch(defaultBranch);
        if (primaryLanguage != null) repository.setPrimaryLanguage(primaryLanguage);
        if (description != null) repository.setDescription(description);
        return repositoryEntityRepository.save(repository);
    }

    @Transactional
    public void delete(UUID id) {
        RepositoryEntity repository = getById(id);
        repositoryEntityRepository.delete(repository);
    }
}
