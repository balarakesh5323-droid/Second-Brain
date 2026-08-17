package com.secondbrain.service;

import com.secondbrain.config.Neo4jConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphService {

    private final Neo4jConfig neo4jConfig;
    private Driver driver;

    @PostConstruct
    public void init() {
        driver = GraphDatabase.driver(
            neo4jConfig.getUri(),
            AuthTokens.basic(neo4jConfig.getUsername(), neo4jConfig.getPassword())
        );
        log.info("Neo4j driver initialized: {}", neo4jConfig.getUri());
    }

    public void createNode(String label, String id, Map<String, Object> properties) {
        try (var session = driver.session()) {
            String cypher = String.format("MERGE (n:%s {id: $id}) SET n += $props", label);
            session.run(cypher, Map.of("id", id, "props", properties));
            log.debug("Created/updated node: {} ({})", label, id);
        }
    }

    public void createRelationship(String fromLabel, String fromId, String toLabel, String toId, String relType, Map<String, Object> properties) {
        try (var session = driver.session()) {
            String cypher = String.format(
                "MATCH (a:%s {id: $fromId}), (b:%s {id: $toId}) MERGE (a)-[r:%s]->(b) SET r += $props",
                fromLabel, toLabel, relType
            );
            session.run(cypher, Map.of("fromId", fromId, "toId", toId, "props", properties != null ? properties : Map.of()));
            log.debug("Created relationship: {} ({}) -[{}]-> {} ({})", fromLabel, fromId, relType, toLabel, toId);
        }
    }

    public List<Map<String, Object>> getNodesByLabel(String label, int limit) {
        try (var session = driver.session()) {
            var result = session.run(String.format("MATCH (n:%s) RETURN n LIMIT $limit", label), Map.of("limit", limit));
            List<Map<String, Object>> nodes = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                nodes.add(record.get("n").asMap());
            }
            return nodes;
        }
    }

    public List<Map<String, Object>> findRelated(String label, String id, String relType, int depth) {
        try (var session = driver.session()) {
            String cypher = String.format(
                "MATCH (n:%s {id: $id})-[r*1..%d]-(m) RETURN DISTINCT m, length(r) as depth ORDER BY depth LIMIT 50",
                label, depth
            );
            var result = session.run(cypher, Map.of("id", id));
            List<Map<String, Object>> related = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                Map<String, Object> node = record.get("m").asMap();
                node.put("depth", record.get("depth").asInt());
                related.add(node);
            }
            return related;
        }
    }

    public List<Map<String, Object>> findPath(String fromLabel, String fromId, String toLabel, String toId, int maxDepth) {
        try (var session = driver.session()) {
            String cypher = String.format(
                "MATCH path = shortestPath((a:%s {id: $fromId})-[*..%d]-(b:%s {id: $toId})) RETURN [n IN nodes(path) | n] as nodes, [r IN relationships(path) | type(r)] as relationships",
                fromLabel, maxDepth, toLabel
            );
            var result = session.run(cypher, Map.of("fromId", fromId, "toId", toId));
            List<Map<String, Object>> paths = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                paths.add(Map.of(
                    "nodes", record.get("nodes").asList(),
                    "relationships", record.get("relationships").asList()
                ));
            }
            return paths;
        }
    }

    public List<Map<String, Object>> searchByProperty(String label, String property, String value, int limit) {
        try (var session = driver.session()) {
            String cypher = String.format(
                "MATCH (n:%s) WHERE n.%s CONTAINS $value RETURN n LIMIT $limit",
                label, property
            );
            var result = session.run(cypher, Map.of("value", value, "limit", limit));
            List<Map<String, Object>> nodes = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                nodes.add(record.get("n").asMap());
            }
            return nodes;
        }
    }

    public Map<String, Object> getStats() {
        try (var session = driver.session()) {
            var nodeCount = session.run("MATCH (n) RETURN count(n) as count").single().get("count").asInt();
            var relCount = session.run("MATCH ()-[r]->() RETURN count(r) as count").single().get("count").asInt();
            var labels = session.run("CALL db.labels() YIELD label RETURN collect(label) as labels").single().get("labels").asList();
            return Map.of(
                "nodeCount", nodeCount,
                "relationshipCount", relCount,
                "labels", labels
            );
        }
    }

    public Map<String, Object> getVisualGraph(int limit) {
        try (var session = driver.session()) {
            var nodeResult = session.run(
                "MATCH (n) RETURN n, labels(n) as labels LIMIT $limit",
                Map.of("limit", limit)
            );

            List<Map<String, Object>> nodes = new ArrayList<>();
            List<String> nodeIds = new ArrayList<>();
            while (nodeResult.hasNext()) {
                var record = nodeResult.next();
                Map<String, Object> props = record.get("n").asMap();
                List<String> labels = record.get("labels").asList().stream()
                    .map(Object::toString).toList();
                String id = (String) props.getOrDefault("id", props.getOrDefault("name", "unknown"));
                String label = labels.isEmpty() ? "Unknown" : labels.get(0);

                nodes.add(Map.of(
                    "id", id,
                    "label", label,
                    "properties", props
                ));
                nodeIds.add(id);
            }

            var relResult = session.run(
                "MATCH (a)-[r]->(b) RETURN a.id as fromId, b.id as toId, type(r) as relType, properties(r) as props LIMIT $limit",
                Map.of("limit", limit * 3)
            );

            List<Map<String, Object>> edges = new ArrayList<>();
            int edgeIdx = 0;
            while (relResult.hasNext()) {
                var record = relResult.next();
                String fromId = record.get("fromId").asString("unknown");
                String toId = record.get("toId").asString("unknown");
                edges.add(Map.of(
                    "id", "e" + edgeIdx++,
                    "source", fromId,
                    "target", toId,
                    "label", record.get("relType").asString()
                ));
            }

            return Map.of("nodes", nodes, "edges", edges);
        }
    }

    @PreDestroy
    public void close() {
        if (driver != null) {
            driver.close();
            log.info("Neo4j driver closed");
        }
    }
}
