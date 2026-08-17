package com.secondbrain.controller;

import com.secondbrain.common.entity.Skill;
import com.secondbrain.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @PostMapping
    public ResponseEntity<Skill> create(@RequestBody Skill skill) {
        return ResponseEntity.status(HttpStatus.CREATED).body(skillService.createSkill(skill));
    }

    @GetMapping
    public ResponseEntity<List<Skill>> getAll() {
        return ResponseEntity.ok(skillService.getAllSkills());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Skill> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(skillService.getById(id));
    }

    @GetMapping("/scope/{scope}")
    public ResponseEntity<List<Skill>> getByScope(@PathVariable String scope) {
        return ResponseEntity.ok(skillService.getByScope(scope));
    }

    @GetMapping("/match")
    public ResponseEntity<List<Skill>> matchSkills(@RequestParam String trigger) {
        return ResponseEntity.ok(skillService.matchSkills(trigger));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Skill> update(@PathVariable UUID id, @RequestBody Skill skill) {
        return ResponseEntity.ok(skillService.updateSkill(id, skill));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        skillService.deleteSkill(id);
        return ResponseEntity.noContent().build();
    }
}
