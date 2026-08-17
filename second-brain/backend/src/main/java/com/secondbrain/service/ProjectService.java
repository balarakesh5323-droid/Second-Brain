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
    private final RepositoryIngestionService ingestionService;
    private final GitHubCloneService cloneService;
    private final GraphSyncService graphSyncService;

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
        project = projectRepository.save(project);
        try {
            graphSyncService.syncProjectToGraph(project);
        } catch (Exception e) {
            // non-fatal
        }
        return project;
    }

    @Transactional
    public java.util.Map<String, Object> createWithRepo(String name, String description, String path, String gitRepo) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();

        if (gitRepo != null && !gitRepo.isBlank()) {
            String repoName = (name != null && !name.isBlank()) ? name : extractRepoNameFromUrl(gitRepo);
            String projectPath = (path != null && !path.isBlank()) ? path : "/repos/" + repoName;

            Project project = projectRepository.findByName(repoName)
                    .orElseGet(() -> {
                        Project p = Project.builder()
                                .name(repoName)
                                .description(description != null ? description : "Project for " + gitRepo)
                                .path(projectPath)
                                .build();
                        return projectRepository.save(p);
                    });

            var ingestResult = ingestionService.ingestFromUrl(gitRepo, project.getId());
            try {
                graphSyncService.syncProjectToGraph(project);
            } catch (Exception ignored) {}
            response.put("project", project);
            response.put("ingestion", ingestResult);
            response.put("status", "success");
        } else {
            Project project = create(name, description, path);
            response.put("project", project);
            response.put("status", "success");
        }

        return response;
    }

    private String extractRepoNameFromUrl(String url) {
        if (url == null || url.isBlank()) return "project-" + UUID.randomUUID().toString().substring(0, 8);
        String clean = url.trim();
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        if (clean.endsWith(".git")) clean = clean.substring(0, clean.length() - 4);
        int slashIdx = clean.lastIndexOf('/');
        return (slashIdx >= 0 && slashIdx < clean.length() - 1) ? clean.substring(slashIdx + 1) : clean;
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
