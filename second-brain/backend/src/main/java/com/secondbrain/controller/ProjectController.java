package com.secondbrain.controller;

import com.secondbrain.common.entity.Project;
import com.secondbrain.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final com.secondbrain.service.GraphSyncService graphSyncService;

    @PostMapping
    public ResponseEntity<Project> create(@RequestBody Project project) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.create(project.getName(), project.getDescription(), project.getPath()));
    }

    @PostMapping("/create-with-repo")
    public ResponseEntity<java.util.Map<String, Object>> createWithRepo(@RequestBody java.util.Map<String, String> payload) {
        String name = payload.get("name");
        String description = payload.get("description");
        String path = payload.get("path");
        String gitRepo = payload.get("gitRepo") != null ? payload.get("gitRepo") : payload.get("git_repo");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createWithRepo(name, description, path, gitRepo));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<java.util.Map<String, Object>> syncProject(@PathVariable UUID id) {
        Project project = projectService.getById(id);
        graphSyncService.syncProjectToGraph(project);
        return ResponseEntity.ok(java.util.Map.of(
                "status", "synced",
                "projectId", project.getId().toString(),
                "name", project.getName(),
                "path", project.getPath() != null ? project.getPath() : ""
        ));
    }

    @GetMapping
    public ResponseEntity<List<Project>> getAll() {
        return ResponseEntity.ok(projectService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> update(@PathVariable UUID id, @RequestBody Project project) {
        return ResponseEntity.ok(projectService.update(id, project.getName(), project.getDescription(), project.getPath()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
