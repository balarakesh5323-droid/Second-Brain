package com.secondbrain.service;

import com.secondbrain.common.entity.Agent;
import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.entity.Technology;
import com.secondbrain.common.repository.AgentRepository;
import com.secondbrain.common.repository.MemoryRepository;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.common.repository.RepositoryEntityRepository;
import com.secondbrain.common.repository.TechnologyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphSyncService {

    private final GraphService graphService;
    private final ProjectRepository projectRepository;
    private final RepositoryEntityRepository repositoryRepository;
    private final AgentRepository agentRepository;
    private final TechnologyRepository technologyRepository;
    private final MemoryRepository memoryRepository;

    @Transactional(readOnly = true)
    public void syncProjectToGraph(Project project) {
        graphService.createNode("Project", project.getId().toString(), Map.of(
            "name", project.getName(),
            "description", project.getDescription() != null ? project.getDescription() : "",
            "path", project.getPath() != null ? project.getPath() : ""
        ));
    }

    @Transactional(readOnly = true)
    public void syncRepositoryToGraph(RepositoryEntity repo) {
        graphService.createNode("Repository", repo.getId().toString(), Map.of(
            "name", repo.getName(),
            "url", repo.getUrl() != null ? repo.getUrl() : "",
            "primaryLanguage", repo.getPrimaryLanguage() != null ? repo.getPrimaryLanguage() : ""
        ));
        if (repo.getProject() != null) {
            graphService.createRelationship("Repository", repo.getId().toString(),
                "Project", repo.getProject().getId().toString(), "BELONGS_TO", null);
        }
    }

    @Transactional(readOnly = true)
    public void syncAgentToGraph(Agent agent) {
        graphService.createNode("Agent", agent.getId().toString(), Map.of(
            "name", agent.getName(),
            "type", agent.getType() != null ? agent.getType() : "",
            "model", agent.getModel() != null ? agent.getModel() : ""
        ));
    }

    @Transactional(readOnly = true)
    public void syncTechnologyToGraph(Technology tech) {
        graphService.createNode("Technology", tech.getId().toString(), Map.of(
            "name", tech.getName(),
            "category", tech.getCategory() != null ? tech.getCategory() : "",
            "version", tech.getVersion() != null ? tech.getVersion() : ""
        ));
    }

    @Transactional(readOnly = true)
    public void syncMemoryToGraph(Memory memory) {
        graphService.createNode("Memory", memory.getId().toString(), Map.of(
            "content", memory.getContent(),
            "type", memory.getType().name(),
            "scope", memory.getScope().name(),
            "status", memory.getStatus().name(),
            "confidence", memory.getConfidence() != null ? memory.getConfidence() : 0.0
        ));
        if (memory.getProject() != null) {
            graphService.createRelationship("Memory", memory.getId().toString(),
                "Project", memory.getProject().getId().toString(), "ABOUT_PROJECT", null);
        }
        if (memory.getRepository() != null) {
            graphService.createRelationship("Memory", memory.getId().toString(),
                "Repository", memory.getRepository().getId().toString(), "ABOUT_REPOSITORY", null);
        }
    }

    public void syncAll() {
        log.info("Starting full graph sync...");
        projectRepository.findAll().forEach(this::syncProjectToGraph);
        repositoryRepository.findAll().forEach(this::syncRepositoryToGraph);
        agentRepository.findAll().forEach(this::syncAgentToGraph);
        technologyRepository.findAll().forEach(this::syncTechnologyToGraph);
        log.info("Full graph sync completed");
    }
}
