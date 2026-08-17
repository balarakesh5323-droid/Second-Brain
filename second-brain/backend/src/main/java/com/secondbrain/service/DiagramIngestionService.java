package com.secondbrain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiagramIngestionService {

    private final GraphService graphService;

    private static final Pattern MERMAID_ARROW = Pattern.compile(
            "([A-Za-z0-9_-]+)\\s*(?:\\[\"?([^\\]\"]+)\"?\\]|\\(\"?([^)\"]+)\"?\\)|\\{\"([^\"]+)\"\\})?\\s*(-{1,2}>|={1,2}>|-\\.->)(?:\\|([^|]+)\\|)?\\s*([A-Za-z0-9_-]+)\\s*(?:\\[\"?([^\\]\"]+)\"?\\]|\\(\"?([^)\"]+)\"?\\)|\\{\"([^\"]+)\"\\})?"
    );

    private static final Pattern SEQUENCE_MESSAGE = Pattern.compile(
            "([A-Za-z0-9_-]+)\\s*(->>|-->>|->|-->)\\s*([A-Za-z0-9_-]+)\\s*:\\s*(.+)"
    );

    public Map<String, Object> ingestDiagram(String diagramText, String format, UUID projectId) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> nodesCreated = new ArrayList<>();
        List<Map<String, Object>> relsCreated = new ArrayList<>();

        if (diagramText == null || diagramText.isBlank()) {
            result.put("status", "error");
            result.put("message", "Empty diagram content provided.");
            return result;
        }

        String projIdStr = (projectId != null) ? projectId.toString() : "global";
        Set<String> discoveredNodeIds = new HashSet<>();

        // 1. Check Sequence Diagram
        if (diagramText.contains("sequenceDiagram")) {
            Matcher seqMatcher = SEQUENCE_MESSAGE.matcher(diagramText);
            while (seqMatcher.find()) {
                String from = seqMatcher.group(1).trim();
                String relType = "CALLS";
                String to = seqMatcher.group(3).trim();
                String message = seqMatcher.group(4).trim();

                String fromId = projIdStr + "::diagram::" + from;
                String toId = projIdStr + "::diagram::" + to;

                if (discoveredNodeIds.add(fromId)) {
                    graphService.createNode("Component", fromId, Map.of("name", from, "type", "actor", "projectId", projIdStr));
                    nodesCreated.add(Map.of("id", fromId, "name", from));
                }
                if (discoveredNodeIds.add(toId)) {
                    graphService.createNode("Component", toId, Map.of("name", to, "type", "actor", "projectId", projIdStr));
                    nodesCreated.add(Map.of("id", toId, "name", to));
                }

                graphService.createRelationship("Component", fromId, "Component", toId, relType, Map.of("message", message));
                relsCreated.add(Map.of("from", from, "to", to, "type", relType, "message", message));
            }
        } else {
            // 2. Flowchart / Architecture graph
            Matcher flowMatcher = MERMAID_ARROW.matcher(diagramText);
            while (flowMatcher.find()) {
                String fromRaw = flowMatcher.group(1);
                String fromLabel = flowMatcher.group(2) != null ? flowMatcher.group(2) : (flowMatcher.group(3) != null ? flowMatcher.group(3) : fromRaw);
                String arrow = flowMatcher.group(5);
                String edgeLabel = flowMatcher.group(6) != null ? flowMatcher.group(6).trim() : "DEPENDS_ON";
                String toRaw = flowMatcher.group(7);
                String toLabel = flowMatcher.group(8) != null ? flowMatcher.group(8) : (flowMatcher.group(9) != null ? flowMatcher.group(9) : toRaw);

                if (fromRaw != null && toRaw != null) {
                    String fromId = projIdStr + "::diagram::" + fromRaw.trim();
                    String toId = projIdStr + "::diagram::" + toRaw.trim();

                    String fromType = inferComponentType(fromLabel);
                    String toType = inferComponentType(toLabel);

                    if (discoveredNodeIds.add(fromId)) {
                        graphService.createNode(fromType, fromId, Map.of("name", fromLabel.trim(), "projectId", projIdStr));
                        nodesCreated.add(Map.of("id", fromId, "name", fromLabel, "type", fromType));
                    }
                    if (discoveredNodeIds.add(toId)) {
                        graphService.createNode(toType, toId, Map.of("name", toLabel.trim(), "projectId", projIdStr));
                        nodesCreated.add(Map.of("id", toId, "name", toLabel, "type", toType));
                    }

                    String normalizedRel = edgeLabel.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase();
                    if (normalizedRel.isBlank()) normalizedRel = "CONNECTS_TO";

                    graphService.createRelationship(fromType, fromId, toType, toId, normalizedRel, Map.of("label", edgeLabel));
                    relsCreated.add(Map.of("from", fromLabel, "to", toLabel, "type", normalizedRel));
                }
            }
        }

        // Link all nodes to Project if projectId provided
        if (projectId != null) {
            for (String nodeId : discoveredNodeIds) {
                try {
                    graphService.createRelationship("Project", projIdStr, "Component", nodeId, "DEFINES_ARCHITECTURE", null);
                } catch (Exception ignored) {}
            }
        }

        log.info("Diagram ingestion complete: created {} nodes and {} relationships", nodesCreated.size(), relsCreated.size());
        result.put("status", "success");
        result.put("nodesCount", nodesCreated.size());
        result.put("relationshipsCount", relsCreated.size());
        result.put("nodes", nodesCreated);
        result.put("relationships", relsCreated);

        return result;
    }

    private String inferComponentType(String label) {
        String l = label.toLowerCase();
        if (l.contains("db") || l.contains("database") || l.contains("postgres") || l.contains("mysql") || l.contains("redis")) {
            return "Database";
        }
        if (l.contains("queue") || l.contains("kafka") || l.contains("rabbitmq") || l.contains("event")) {
            return "Queue";
        }
        if (l.contains("ui") || l.contains("client") || l.contains("browser") || l.contains("frontend") || l.contains("app")) {
            return "Client";
        }
        return "Service";
    }
}
