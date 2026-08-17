package com.secondbrain.controller;

import com.secondbrain.common.entity.Technology;
import com.secondbrain.service.TechnologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/technologies")
@RequiredArgsConstructor
public class TechnologyController {

    private final TechnologyService technologyService;

    @PostMapping
    public ResponseEntity<Technology> create(@RequestBody Technology technology) {
        return ResponseEntity.status(HttpStatus.CREATED).body(technologyService.create(technology));
    }

    @GetMapping
    public ResponseEntity<List<Technology>> getAll() {
        return ResponseEntity.ok(technologyService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Technology> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(technologyService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Technology> update(@PathVariable UUID id, @RequestBody Technology technology) {
        return ResponseEntity.ok(technologyService.update(id, technology));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        technologyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
