package com.secondbrain.controller;

import com.secondbrain.common.entity.Agent;
import com.secondbrain.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping
    public ResponseEntity<Agent> create(@RequestBody Agent agent) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.create(agent));
    }

    @GetMapping
    public ResponseEntity<List<Agent>> getAll() {
        return ResponseEntity.ok(agentService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Agent> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(agentService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Agent> update(@PathVariable UUID id, @RequestBody Agent agent) {
        return ResponseEntity.ok(agentService.update(id, agent));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        agentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
