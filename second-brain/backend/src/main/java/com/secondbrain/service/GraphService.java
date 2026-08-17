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

import java.util.*;

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

    public void batchCreateNodes(String label, List<Map<String, Object>> nodeList) {
        if (nodeList.isEmpty()) return;
        String cypher = String.format("UNWIND $nodes AS n MERGE (e:%s {id: n.id}) SET e += n.props", label);
        int created = 0;
        for (int i = 0; i < nodeList.size(); i += 50) {
            List<Map<String, Object>> chunk = nodeList.subList(i, Math.min(i + 50, nodeList.size()));
            try (var session = driver.session()) {
                List<Map<String, Object>> params = chunk.stream()
                    .map(m -> Map.of("id", m.get("id"), "props", m.getOrDefault("props", Map.of())))
                    .toList();
                session.run(cypher, Map.of("nodes", params));
                created += chunk.size();
            } catch (Exception e) {
                log.warn("Batch create {} nodes failed at chunk {}: {}", label, i, e.getMessage());
            }
        }
        log.debug("Batch created {}/{} {} nodes", created, nodeList.size(), label);
    }

    public void batchCreateRelationships(List<Map<String, Object>> relList) {
        if (relList.isEmpty()) return;
        String cypher = "UNWIND $rels AS r " +
            "MATCH (a {id: r.fromId}), (b {id: r.toId}) " +
            "MERGE (a)-[rel]->(b) SET rel += r.props";
        int created = 0;
        for (int i = 0; i < relList.size(); i += 50) {
            List<Map<String, Object>> chunk = relList.subList(i, Math.min(i + 50, relList.size()));
            try (var session = driver.session()) {
                List<Map<String, Object>> params = chunk.stream()
                    .map(m -> Map.of(
                        "fromId", m.get("fromId"),
                        "toId", m.get("toId"),
                        "props", m.getOrDefault("props", Map.of())))
                    .toList();
                session.run(cypher, Map.of("rels", params));
                created += chunk.size();
            } catch (Exception e) {
                log.warn("Batch create relationships failed at chunk {}: {}", i, e.getMessage());
            }
        }
        log.debug("Batch created {}/{} relationships", created, relList.size());
    }

    public void batchCreateRelationshipsTyped(String relType, List<Map<String, Object>> relList) {
        if (relList.isEmpty()) return;
        String cypher = String.format(
            "UNWIND $rels AS r MATCH (a {id: r.fromId}), (b {id: r.toId}) MERGE (a)-[rel:%s]->(b) SET rel += r.props",
            relType);
        int created = 0;
        for (int i = 0; i < relList.size(); i += 50) {
            List<Map<String, Object>> chunk = relList.subList(i, Math.min(i + 50, relList.size()));
            try (var session = driver.session()) {
                List<Map<String, Object>> params = chunk.stream()
                    .map(m -> Map.of(
                        "fromId", m.get("fromId"),
                        "toId", m.get("toId"),
                        "props", m.getOrDefault("props", Map.of())))
                    .toList();
                session.run(cypher, Map.of("rels", params));
                created += chunk.size();
            } catch (Exception e) {
                log.warn("Batch create {} relationships failed at chunk {}: {}", relType, i, e.getMessage());
            }
        }
        log.debug("Batch created {}/{} {} relationships", created, relList.size(), relType);
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
            String relFilter = (relType != null && !relType.isBlank()) ? ":" + relType : "";
            String cypher = String.format(
                "MATCH (n:%s {id: $id})-[r%s*1..%d]-(m) RETURN DISTINCT m, length(r) as depth ORDER BY depth LIMIT 50",
                label, relFilter, depth
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

    public List<Map<String, Object>> findNeighborhood(String nodeId, int depth, int limit) {
        try (var session = driver.session()) {
            String cypher = String.format(
                "MATCH (n {id: $id})-[r*1..%d]-(m) " +
                "RETURN DISTINCT m, [rel IN r | type(rel)] as relTypes, labels(m) as labels, length(r) as depth " +
                "ORDER BY depth LIMIT $limit",
                depth
            );
            var result = session.run(cypher, Map.of("id", nodeId, "limit", limit));
            List<Map<String, Object>> neighbors = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                Map<String, Object> node = new HashMap<>(record.get("m").asMap());
                node.put("relTypes", record.get("relTypes").asList().stream().map(Object::toString).toList());
                node.put("labels", record.get("labels").asList().stream().map(Object::toString).toList());
                node.put("depth", record.get("depth").asInt());
                node.put("rootId", nodeId);
                neighbors.add(node);
            }
            return neighbors;
        } catch (Exception e) {
            log.warn("Failed to find neighborhood for node {}: {}", nodeId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> findNeighborhoods(List<String> nodeIds, int depth, int limit) {
        if (nodeIds == null || nodeIds.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> allNeighbors = new ArrayList<>();
        Set<String> seenIds = new HashSet<>(nodeIds);

        for (String id : nodeIds) {
            List<Map<String, Object>> neighbors = findNeighborhood(id, depth, Math.max(5, limit / Math.max(1, nodeIds.size())));
            for (Map<String, Object> neighbor : neighbors) {
                String neighborId = (String) neighbor.getOrDefault("id", neighbor.getOrDefault("name", ""));
                if (!neighborId.isEmpty() && seenIds.add(neighborId)) {
                    allNeighbors.add(neighbor);
                    if (allNeighbors.size() >= limit) break;
                }
            }
            if (allNeighbors.size() >= limit) break;
        }
        return allNeighbors;
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

    public void deleteNode(String id) {
        try (var session = driver.session()) {
            session.run("MATCH (n {id: $id}) DETACH DELETE n", Map.of("id", id));
            log.info("Deleted node {} from Neo4j Knowledge Graph", id);
        } catch (Exception e) {
            log.error("Failed to delete node {}: {}", id, e.getMessage());
        }
    }

    public void deleteFileCascade(String fileId) {
        try (var session = driver.session()) {
            session.run("""
                MATCH (f:File {id: $fileId})
                OPTIONAL MATCH (f)-[:DECLARES]->(child)
                DETACH DELETE child, f
                """, Map.of("fileId", fileId));
            log.info("Cascaded deletion of file '{}' and declared children from Neo4j", fileId);
        } catch (Exception e) {
            log.error("Failed cascading deletion of file {}: {}", fileId, e.getMessage());
        }
    }

    public List<Map<String, String>> findFilesByPrefix(String prefix) {
        try (var session = driver.session()) {
            var result = session.run("MATCH (f:File) WHERE f.id STARTS WITH $prefix RETURN f.id AS id, f.path AS path", Map.of("prefix", prefix));
            List<Map<String, String>> files = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                files.add(Map.of(
                        "id", record.get("id").asString(),
                        "path", record.get("path").asString("")
                ));
            }
            return files;
        } catch (Exception e) {
            log.error("Failed to find files by prefix {}: {}", prefix, e.getMessage());
            return List.of();
        }
    }

    public List<String> getDeclaredChildIds(String fileId) {
        try (var session = driver.session()) {
            var result = session.run("MATCH (f:File {id: $fileId})-[:DECLARES]->(child) RETURN child.id AS id", Map.of("fileId", fileId));
            List<String> childIds = new ArrayList<>();
            while (result.hasNext()) {
                childIds.add(result.next().get("id").asString());
            }
            return childIds;
        } catch (Exception e) {
            log.debug("Error fetching children for {}: {}", fileId, e.getMessage());
            return List.of();
        }
    }

    public List<String> deleteStaleChildren(String fileId, Set<String> keepChildIds) {
        try (var session = driver.session()) {
            var result = session.run("""
                MATCH (f:File {id: $fileId})-[:DECLARES]->(child)
                WHERE NOT child.id IN $keepChildIds
                WITH child, child.id AS deletedId
                DETACH DELETE child
                RETURN deletedId
                """, Map.of("fileId", fileId, "keepChildIds", new ArrayList<>(keepChildIds)));
            List<String> deleted = new ArrayList<>();
            while (result.hasNext()) {
                deleted.add(result.next().get("deletedId").asString());
            }
            if (!deleted.isEmpty()) {
                log.info("Purged {} stale child nodes for file '{}': {}", deleted.size(), fileId, deleted);
            }
            return deleted;
        } catch (Exception e) {
            log.error("Failed purging stale children for {}: {}", fileId, e.getMessage());
            throw new IllegalStateException("Failed purging stale children for " + fileId, e);
        }
    }

    public void deleteStaleTechnologies(String fileId, Set<String> keepTechIds) {
        try (var session = driver.session()) {
            session.run("""
                MATCH (f:File {id: $fileId})-[r:USES_TECHNOLOGY]->(t:Technology)
                WHERE NOT t.id IN $keepTechIds
                DELETE r
                """, Map.of("fileId", fileId, "keepTechIds", new ArrayList<>(keepTechIds)));
            log.debug("Reconciled technology relationships for file '{}'", fileId);
        } catch (Exception e) {
            log.error("Failed reconciling technologies for {}: {}", fileId, e.getMessage());
        }
    }

    public Map<String, String> findAllFileHashes(String prefix) {
        try (var session = driver.session()) {
            var result = session.run("MATCH (f:File) WHERE f.id STARTS WITH $prefix AND f.contentHash IS NOT NULL RETURN f.id AS id, f.contentHash AS hash", Map.of("prefix", prefix));
            Map<String, String> hashes = new HashMap<>();
            while (result.hasNext()) {
                var record = result.next();
                hashes.put(record.get("id").asString(), record.get("hash").asString());
            }
            return hashes;
        } catch (Exception e) {
            log.error("Failed fetching file hashes for prefix {}: {}", prefix, e.getMessage());
            return Map.of();
        }
    }

    public void recordAgentSessionGraph(
            String agentName,
            String agentType,
            String sessionId,
            Map<String, Object> sessionProps,
            String repoId,
            List<String> touchedFileIds,
            List<Map<String, Object>> problems,
            List<Map<String, Object>> decisions,
            List<Map<String, Object>> failedAttempts,
            List<Map<String, Object>> commits,
            Map<String, Object> handoff
    ) {
        try (var session = driver.session()) {
            String agentId = "agent::" + agentName.toLowerCase().replaceAll("[^a-z0-9]", "_");

            // 1. Agent & Session nodes
            session.run("""
                MERGE (a:Agent {id: $agentId})
                ON CREATE SET a.name = $agentName, a.type = $agentType, a.createdAt = datetime()
                ON MATCH SET a.type = $agentType, a.updatedAt = datetime()
                
                MERGE (s:AgentSession {id: $sessionId})
                SET s += $sessionProps
                
                MERGE (a)-[:STARTED]->(s)
                """, Map.of(
                    "agentId", agentId,
                    "agentName", agentName,
                    "agentType", agentType != null ? agentType : "CLI",
                    "sessionId", sessionId,
                    "sessionProps", sessionProps != null ? sessionProps : Map.of()
            ));

            // 2. Repository Link
            if (repoId != null && !repoId.isBlank()) {
                session.run("""
                    MATCH (s:AgentSession {id: $sessionId})
                    MERGE (r:Repository {id: $repoId})
                    MERGE (s)-[:WORKED_ON]->(r)
                    """, Map.of("sessionId", sessionId, "repoId", repoId));
            }

            // 3. Touched Files
            if (touchedFileIds != null) {
                for (String fId : touchedFileIds) {
                    session.run("""
                        MATCH (s:AgentSession {id: $sessionId})
                        MERGE (f:File {id: $fileId})
                        MERGE (s)-[:TOUCHED]->(f)
                        """, Map.of("sessionId", sessionId, "fileId", fId));
                }
            }

            // 4. Problems Encountered
            if (problems != null) {
                for (Map<String, Object> prob : problems) {
                    String probId = (String) prob.getOrDefault("id", "prob::" + UUID.randomUUID());
                    session.run("""
                        MATCH (s:AgentSession {id: $sessionId})
                        MERGE (p:Problem {id: $probId})
                        SET p += $probProps
                        MERGE (s)-[:ENCOUNTERED]->(p)
                        """, Map.of("sessionId", sessionId, "probId", probId, "probProps", prob));
                }
            }

            // 5. Decisions Made
            if (decisions != null) {
                for (Map<String, Object> dec : decisions) {
                    String decId = (String) dec.getOrDefault("id", "dec::" + UUID.randomUUID());
                    String solvedProblemId = (String) dec.get("solvedProblemId");
                    session.run("""
                        MATCH (s:AgentSession {id: $sessionId})
                        MERGE (d:Decision {id: $decId})
                        SET d += $decProps
                        MERGE (s)-[:MADE]->(d)
                        """, Map.of("sessionId", sessionId, "decId", decId, "decProps", dec));

                    if (solvedProblemId != null && !solvedProblemId.isBlank()) {
                        session.run("""
                            MATCH (d:Decision {id: $decId}), (p:Problem {id: $probId})
                            MERGE (d)-[:SOLVED]->(p)
                            """, Map.of("decId", decId, "probId", solvedProblemId));
                    }
                }
            }

            // 6. Failed Attempts
            if (failedAttempts != null) {
                for (Map<String, Object> fa : failedAttempts) {
                    String faId = (String) fa.getOrDefault("id", "fail::" + UUID.randomUUID());
                    String relatedProblemId = (String) fa.get("problemId");
                    session.run("""
                        MATCH (s:AgentSession {id: $sessionId})
                        MERGE (f:FailedAttempt {id: $faId})
                        SET f += $faProps
                        MERGE (s)-[:TRIED_AND_FAILED]->(f)
                        """, Map.of("sessionId", sessionId, "faId", faId, "faProps", fa));

                    if (relatedProblemId != null && !relatedProblemId.isBlank()) {
                        session.run("""
                            MATCH (p:Problem {id: $probId}), (f:FailedAttempt {id: $faId})
                            MERGE (p)-[:RESULTED_IN]->(f)
                            """, Map.of("probId", relatedProblemId, "faId", faId));
                    }
                }
            }

            // 7. Commits Produced
            if (commits != null) {
                for (Map<String, Object> c : commits) {
                    String commitSha = (String) c.getOrDefault("hash", (String) c.getOrDefault("id", "c::" + UUID.randomUUID()));
                    session.run("""
                        MATCH (s:AgentSession {id: $sessionId})
                        MERGE (c:Commit {id: $commitSha})
                        SET c += $commitProps
                        MERGE (s)-[:PRODUCED]->(c)
                        """, Map.of("sessionId", sessionId, "commitSha", commitSha, "commitProps", c));
                }
            }

            // 8. Handoff Created
            if (handoff != null && !handoff.isEmpty()) {
                String handoffId = (String) handoff.getOrDefault("id", "handoff::" + UUID.randomUUID());
                String targetAgent = (String) handoff.get("targetAgent");
                session.run("""
                    MATCH (s:AgentSession {id: $sessionId})
                    MERGE (h:AgentHandoff {id: $handoffId})
                    SET h += $handoffProps
                    MERGE (s)-[:CREATED]->(h)
                    """, Map.of("sessionId", sessionId, "handoffId", handoffId, "handoffProps", handoff));

                if (targetAgent != null && !targetAgent.isBlank()) {
                    String targetAgentId = "agent::" + targetAgent.toLowerCase().replaceAll("[^a-z0-9]", "_");
                    session.run("""
                        MATCH (h:AgentHandoff {id: $handoffId})
                        MERGE (ta:Agent {id: $targetAgentId})
                        MERGE (h)-[:TARGETS]->(ta)
                        """, Map.of("handoffId", handoffId, "targetAgentId", targetAgentId));
                }
            }

            log.info("🧠 Agent Activity Memory: Recorded graph for agent '{}' on session '{}'", agentName, sessionId);
        } catch (Exception e) {
            log.error("Failed recording agent activity graph for session {}: {}", sessionId, e.getMessage());
        }
    }

    public List<Map<String, Object>> getAgentTimeline(String repoId, int limit) {
        try (var session = driver.session()) {
            String cypher = """
                MATCH (a:Agent)-[:STARTED]->(s:AgentSession)
                OPTIONAL MATCH (s)-[:WORKED_ON]->(r:Repository)
                OPTIONAL MATCH (s)-[:MADE]->(d:Decision)
                OPTIONAL MATCH (s)-[:ENCOUNTERED]->(p:Problem)
                OPTIONAL MATCH (s)-[:TRIED_AND_FAILED]->(fa:FailedAttempt)
                OPTIONAL MATCH (s)-[:PRODUCED]->(c:Commit)
                OPTIONAL MATCH (s)-[:CREATED]->(h:AgentHandoff)
                WHERE ($repoId IS NULL OR r.id = $repoId OR r.name = $repoId)
                RETURN a.name AS agentName,
                       a.type AS agentType,
                       s.id AS sessionId,
                       s.summary AS summary,
                       s.startedAt AS startedAt,
                       s.status AS status,
                       collect(DISTINCT d.title) AS decisions,
                       collect(DISTINCT p.title) AS problems,
                       collect(DISTINCT fa.approach) AS failedAttempts,
                       collect(DISTINCT c.id) AS commits,
                       h.nextSteps AS nextSteps
                ORDER BY s.startedAt DESC
                LIMIT $limit
                """;

            var result = session.run(cypher, Map.of("repoId", repoId != null ? repoId : "", "limit", limit > 0 ? limit : 20));
            List<Map<String, Object>> timeline = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                Map<String, Object> item = new HashMap<>();
                item.put("agentName", record.get("agentName").asString("Unknown"));
                item.put("agentType", record.get("agentType").asString("CLI"));
                item.put("sessionId", record.get("sessionId").asString(""));
                item.put("summary", record.get("summary").asString(""));
                item.put("startedAt", record.get("startedAt").asString(""));
                item.put("status", record.get("status").asString("COMPLETED"));
                item.put("decisions", record.get("decisions").asList(v -> v.asString()));
                item.put("problems", record.get("problems").asList(v -> v.asString()));
                item.put("failedAttempts", record.get("failedAttempts").asList(v -> v.asString()));
                item.put("commits", record.get("commits").asList(v -> v.asString()));
                item.put("nextSteps", record.get("nextSteps").asString(""));
                timeline.add(item);
            }
            return timeline;
        } catch (Exception e) {
            log.error("Failed fetching agent timeline for repo {}: {}", repoId, e.getMessage());
            return List.of();
        }
    }

    public void wipeAll() {
        try (var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
            log.info("Wiped all nodes and relationships from Neo4j Knowledge Graph");
        } catch (Exception e) {
            log.error("Failed to wipe Neo4j knowledge graph: {}", e.getMessage());
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
