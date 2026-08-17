package com.secondbrain.controller;

import com.secondbrain.common.entity.Decision;
import com.secondbrain.service.DecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/decisions")
@RequiredArgsConstructor
public class DecisionController {

    private final DecisionService decisionService;

    @PostMapping
    public ResponseEntity<Decision> create(@RequestBody Decision decision) {
        return ResponseEntity.status(HttpStatus.CREATED).body(decisionService.create(decision));
    }

    @GetMapping
    public ResponseEntity<List<Decision>> getAll() {
        return ResponseEntity.ok(decisionService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Decision> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(decisionService.getById(id));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Decision>> getByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(decisionService.getByProject(projectId));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Decision>> getRecent() {
        return ResponseEntity.ok(decisionService.getRecent());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Decision> update(@PathVariable UUID id, @RequestBody Decision decision) {
        return ResponseEntity.ok(decisionService.update(id, decision));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        decisionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
