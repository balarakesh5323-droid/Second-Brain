package com.secondbrain.controller;

import com.secondbrain.common.dto.MemoryDto;
import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    @PostMapping
    public ResponseEntity<Memory> create(@RequestBody MemoryDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memoryService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<Memory>> getAll() {
        return ResponseEntity.ok(memoryService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Memory> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(memoryService.getById(id));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Memory>> getByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(memoryService.getByProject(projectId));
    }

    @GetMapping("/repository/{repositoryId}")
    public ResponseEntity<List<Memory>> getByRepository(@PathVariable UUID repositoryId) {
        return ResponseEntity.ok(memoryService.getByRepository(repositoryId));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<Memory>> getByType(@PathVariable MemoryType type) {
        return ResponseEntity.ok(memoryService.getByType(type));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Memory>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(memoryService.search(query));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Memory> update(@PathVariable UUID id, @RequestBody MemoryDto dto) {
        return ResponseEntity.ok(memoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        memoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
