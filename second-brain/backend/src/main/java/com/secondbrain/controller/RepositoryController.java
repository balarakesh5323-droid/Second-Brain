package com.secondbrain.controller;

import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.service.RepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/repositories")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;

    @PostMapping
    public ResponseEntity<RepositoryEntity> create(@RequestBody RepositoryEntity repository) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repositoryService.create(repository.getName(), repository.getUrl(), repository.getPath(),
                        repository.getProject() != null ? repository.getProject().getId() : null));
    }

    @GetMapping
    public ResponseEntity<List<RepositoryEntity>> getAll() {
        return ResponseEntity.ok(repositoryService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepositoryEntity> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(repositoryService.getById(id));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<RepositoryEntity>> getByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(repositoryService.getByProject(projectId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RepositoryEntity> update(@PathVariable UUID id, @RequestBody RepositoryEntity repository) {
        return ResponseEntity.ok(repositoryService.update(id, repository.getName(), repository.getUrl(),
                repository.getPath(), repository.getDefaultBranch(), repository.getPrimaryLanguage(),
                repository.getDescription()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        repositoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
