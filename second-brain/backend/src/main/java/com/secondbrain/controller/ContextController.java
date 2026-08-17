package com.secondbrain.controller;

import com.secondbrain.common.dto.ContextResponse;
import com.secondbrain.service.ContextAssemblyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/context")
@RequiredArgsConstructor
@Slf4j
public class ContextController {

    private final ContextAssemblyService contextAssemblyService;

    @PostMapping("/assemble")
    public ResponseEntity<ContextResponse> assembleContext(@RequestBody Map<String, String> request) {
        String query = request.getOrDefault("query", "");
        String projectId = request.get("projectId");
        String repositoryId = request.get("repositoryId");

        log.info("Assembling context for API query: '{}' (proj: {}, repo: {})", query, projectId, repositoryId);
        ContextResponse response = contextAssemblyService.assembleContext(query, projectId, repositoryId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askBrain(@RequestBody Map<String, String> request) {
        String query = request.getOrDefault("query", "");
        String projectId = request.get("projectId");
        String repositoryId = request.get("repositoryId");

        log.info("Answering brain query: '{}'", query);
        ContextResponse context = contextAssemblyService.assembleContext(query, projectId, repositoryId);

        // Synthesize a structured AI answer with citations and evidence
        StringBuilder answerBuilder = new StringBuilder();
        List<Map<String, Object>> citations = new ArrayList<>();

        if (context.getRelevantContext() != null && !context.getRelevantContext().isEmpty()) {
            answerBuilder.append("Based on the Second Brain's knowledge base:\n\n");
            for (ContextResponse.ContextItem item : context.getRelevantContext()) {
                answerBuilder.append("- ").append(item.getContent()).append("\n");
                citations.add(Map.of(
                    "id", item.getId(),
                    "type", item.getType(),
                    "title", item.getContent() != null && item.getContent().length() > 60 ? item.getContent().substring(0, 57) + "..." : item.getContent(),
                    "source", item.getSource() != null ? item.getSource() : "memory",
                    "score", item.getScore() != null ? item.getScore() : 0.85
                ));
            }
        } else {
            answerBuilder.append("No direct memories found for query: '").append(query).append("'.\n\n");
        }

        if (context.getDecisions() != null && !context.getDecisions().isEmpty()) {
            answerBuilder.append("\n**Architectural Decisions:**\n");
            for (ContextResponse.DecisionSummary decision : context.getDecisions()) {
                answerBuilder.append("- **").append(decision.getTitle()).append("**: ")
                    .append(decision.getRationale() != null ? decision.getRationale() : "").append("\n");
                citations.add(Map.of(
                    "id", decision.getId(),
                    "type", "DECISION",
                    "title", decision.getTitle(),
                    "source", "decision",
                    "status", decision.getStatus() != null ? decision.getStatus() : "ACCEPTED"
                ));
            }
        }

        if (context.getArchitecture() != null && !context.getArchitecture().isEmpty()) {
            answerBuilder.append("\n**Graph-RAG Relational Context:**\n");
            for (ContextResponse.ContextItem arch : context.getArchitecture()) {
                answerBuilder.append("- `").append(arch.getType()).append("` ").append(arch.getContent()).append("\n");
                citations.add(Map.of(
                    "id", arch.getId(),
                    "type", arch.getType(),
                    "title", arch.getId(),
                    "source", "graph_rag_neighborhood",
                    "score", arch.getScore() != null ? arch.getScore() : 0.8
                ));
            }
        }

        if (context.getOpenTasks() != null && !context.getOpenTasks().isEmpty()) {
            answerBuilder.append("\n**Related Open Tasks:**\n");
            for (ContextResponse.TaskSummary task : context.getOpenTasks()) {
                answerBuilder.append("- [").append(task.getStatus()).append("] ").append(task.getTitle()).append("\n");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("query", query);
        result.put("answer", answerBuilder.toString().trim());
        result.put("citations", citations);
        result.put("context", context);
        result.put("sources", context.getSources());
        result.put("project", context.getProject());
        result.put("repository", context.getRepository());

        return ResponseEntity.ok(result);
    }
}
