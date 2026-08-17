package com.secondbrain.service;

import com.secondbrain.common.entity.Decision;
import com.secondbrain.common.repository.DecisionRepository;
import com.secondbrain.config.Neo4jConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImpactAnalysisService {

    private final Neo4jConfig neo4jConfig;
    private final DecisionRepository decisionRepository;

    private static final Pattern FUNCTION_SIGNATURE = Pattern.compile(
            "(?:function|def|public|private|protected)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)"
    );

    public Map<String, Object> analyzeImpact(String filePath, String diffOrCode, UUID projectId) {
        Map<String, Object> report = new HashMap<>();
        List<Map<String, Object>> affectedCallSites = new ArrayList<>();
        List<Map<String, Object>> decisionConflicts = new ArrayList<>();
        List<String> detectedFunctions = new ArrayList<>();

        if (diffOrCode == null || diffOrCode.isBlank()) {
            report.put("risk", "LOW");
            report.put("message", "No changes provided for impact analysis");
            report.put("affectedCallSites", List.of());
            report.put("decisionConflicts", List.of());
            return report;
        }

        // 1. Extract changed / declared function names
        Matcher matcher = FUNCTION_SIGNATURE.matcher(diffOrCode);
        while (matcher.find()) {
            detectedFunctions.add(matcher.group(1));
        }

        // 2. Query Neo4j call graph for affected callers
        try (Driver driver = GraphDatabase.driver(neo4jConfig.getUri(),
                AuthTokens.basic(neo4jConfig.getUsername(), neo4jConfig.getPassword()));
             var session = driver.session()) {

            for (String funcName : detectedFunctions) {
                var result = session.run(
                        "MATCH (caller:Function)-[:CALLS]->(callee:Function) " +
                                "WHERE callee.name = $name " +
                                "RETURN caller.name AS callerName, caller.file AS callerFile, callee.name AS targetFunc",
                        Map.of("name", funcName)
                );

                while (result.hasNext()) {
                    var record = result.next();
                    affectedCallSites.add(Map.of(
                            "targetFunction", record.get("targetFunc").asString(),
                            "callerFunction", record.get("callerName").asString(),
                            "callerFile", record.get("callerFile").asString("unknown")
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Neo4j call graph query failed during impact analysis: {}", e.getMessage());
        }

        // 3. Check for architectural decision conflicts
        List<Decision> decisions = (projectId != null)
                ? decisionRepository.findByProjectId(projectId)
                : decisionRepository.findAll();

        String lowerDiff = diffOrCode.toLowerCase();
        for (Decision d : decisions) {
            String title = d.getTitle() != null ? d.getTitle().toLowerCase() : "";
            String rationale = d.getRationale() != null ? d.getRationale().toLowerCase() : "";

            // Check if decision specifies technologies or constraints
            if (title.contains("postgres") && (lowerDiff.contains("mongo") || lowerDiff.contains("dynamodb"))) {
                decisionConflicts.add(Map.of(
                        "decisionId", d.getId().toString(),
                        "decisionTitle", d.getTitle(),
                        "conflictReason", "Code uses NoSQL database, conflicting with approved decision: " + d.getTitle()
                ));
            }
            if (title.contains("redis") && lowerDiff.contains("concurrenthashmap")) {
                decisionConflicts.add(Map.of(
                        "decisionId", d.getId().toString(),
                        "decisionTitle", d.getTitle(),
                        "conflictReason", "Code uses in-memory Map, conflicting with distributed Redis decision: " + d.getTitle()
                ));
            }
            if (title.contains("oauth2") && lowerDiff.contains("basicauth")) {
                decisionConflicts.add(Map.of(
                        "decisionId", d.getId().toString(),
                        "decisionTitle", d.getTitle(),
                        "conflictReason", "Code uses Basic Auth, conflicting with OAuth2 decision: " + d.getTitle()
                ));
            }
        }

        // 4. Calculate Risk Level
        String risk = "LOW";
        if (!decisionConflicts.isEmpty()) {
            risk = "HIGH";
        } else if (affectedCallSites.size() > 5) {
            risk = "HIGH";
        } else if (!affectedCallSites.isEmpty()) {
            risk = "MEDIUM";
        }

        report.put("risk", risk);
        report.put("file", filePath != null ? filePath : "N/A");
        report.put("detectedFunctions", detectedFunctions);
        report.put("affectedCallSites", affectedCallSites);
        report.put("affectedCallSiteCount", affectedCallSites.size());
        report.put("decisionConflicts", decisionConflicts);

        List<String> recommendations = new ArrayList<>();
        if (!affectedCallSites.isEmpty()) {
            recommendations.add(String.format("Found %d downstream call sites. Verify argument types and return contracts.", affectedCallSites.size()));
        }
        if (!decisionConflicts.isEmpty()) {
            recommendations.add("Architectural drift detected! Align implementation with registered Project Decisions.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("No breaking call-site dependencies or decision conflicts detected. Safe to proceed.");
        }
        report.put("recommendations", recommendations);

        return report;
    }
}
