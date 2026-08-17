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

    @GetMapping("/{id}/files")
    public ResponseEntity<List<java.util.Map<String, Object>>> getWorkspaceFiles(@PathVariable UUID id) {
        Project project = projectService.getById(id);
        List<java.util.Map<String, Object>> files = new java.util.ArrayList<>();
        if (project.getPath() != null && !project.getPath().isBlank()) {
            java.nio.file.Path p = java.nio.file.Paths.get(project.getPath());
            if (java.nio.file.Files.exists(p)) {
                try {
                    java.nio.file.Files.walk(p, 3)
                            .filter(java.nio.file.Files::isRegularFile)
                            .filter(f -> !f.toString().contains("/.git/") && !f.toString().contains("/node_modules/"))
                            .forEach(f -> {
                                try {
                                    files.add(java.util.Map.of(
                                            "name", f.getFileName().toString(),
                                            "path", p.relativize(f).toString(),
                                            "size", java.nio.file.Files.size(f),
                                            "isWeb", f.getFileName().toString().endsWith(".html") || f.getFileName().toString().endsWith(".htm") || f.getFileName().toString().endsWith(".js")
                                    ));
                                } catch (Exception ignored) {}
                            });
                } catch (Exception ignored) {}
            }
        }
        return ResponseEntity.ok(files);
    }

    @GetMapping("/{id}/files/raw")
    public ResponseEntity<String> getRawFile(@PathVariable UUID id, @RequestParam(defaultValue = "car-game.html") String file) {
        Project project = projectService.getById(id);
        if (project.getPath() != null && !project.getPath().isBlank()) {
            java.nio.file.Path root = java.nio.file.Paths.get(project.getPath());
            java.nio.file.Path target = root.resolve(file).normalize();
            if (target.startsWith(root) && java.nio.file.Files.exists(target)) {
                try {
                    String content = java.nio.file.Files.readString(target);
                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    if (file.endsWith(".html") || file.endsWith(".htm")) {
                        headers.setContentType(org.springframework.http.MediaType.TEXT_HTML);
                    } else if (file.endsWith(".js")) {
                        headers.setContentType(org.springframework.http.MediaType.valueOf("application/javascript"));
                    } else if (file.endsWith(".css")) {
                        headers.setContentType(org.springframework.http.MediaType.valueOf("text/css"));
                    } else {
                        headers.setContentType(org.springframework.http.MediaType.TEXT_PLAIN);
                    }
                    return new ResponseEntity<>(content, headers, HttpStatus.OK);
                } catch (Exception ignored) {}
            }
        }
        return ResponseEntity.notFound().build();
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
