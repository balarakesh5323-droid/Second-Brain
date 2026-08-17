package com.secondbrain.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.mcp.tools.BrainToolHandler;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecondBrainMcpServer {

    private final BrainToolHandler toolHandler;

    private McpSyncServer mcpServer;
    private HttpServletSseServerTransportProvider transportProvider;

    @PostConstruct
    public void init() {
        transportProvider = new HttpServletSseServerTransportProvider(
            new ObjectMapper(), "/mcp/messages");
        
        mcpServer = McpServer.sync(transportProvider)
            .serverInfo("second-brain", "1.0.0")
            .capabilities(ServerCapabilities.builder()
                .tools(true)
                .resources(true, true)
                .build())
            .tools(buildTools().toArray(new SyncToolSpecification[0]))
            .resources(buildResources().toArray(new SyncResourceSpecification[0]))
            .build();
        
        log.info("Second Brain MCP Server initialized");
    }

    private List<SyncToolSpecification> buildTools() {
        return List.of(
            buildTool("brain_search",
                "Search across all memories in the Second Brain",
                "object", Map.of(
                    "query", Map.of("type", "string", "description", "Search query"),
                    "collection", Map.of("type", "string", "description", "Optional collection to search"),
                    "limit", Map.of("type", "integer", "description", "Max results")
                ), List.of("query"),
                (exchange, args) -> {
                    String query = (String) args.get("query");
                    String collection = (String) args.get("collection");
                    int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 10;
                    return toolHandler.handleSearch(query, collection, limit);
                }),

            buildTool("brain_ask",
                "Ask a natural-language question against the brain",
                "object", Map.of(
                    "question", Map.of("type", "string", "description", "Natural language question")
                ), List.of("question"),
                (exchange, args) -> {
                    String question = (String) args.get("question");
                    return toolHandler.handleSearch(question, null, 10);
                }),

            buildTool("brain_projects",
                "List all projects in the Second Brain",
                "object", Map.of(), List.of(),
                (exchange, args) -> toolHandler.handleGetProjects()),

            buildTool("brain_store_memory",
                "Store a new memory in the Second Brain",
                "object", Map.of(
                    "content", Map.of("type", "string", "description", "Memory content"),
                    "type", Map.of("type", "string", "description", "Memory type (EPILOGICAL, SEMANTIC, PROCEDURAL, EPISODIC, DECLARATIVE)"),
                    "scope", Map.of("type", "string", "description", "Scope (GLOBAL, PROJECT, REPOSITORY)"),
                    "project_id", Map.of("type", "string", "description", "Project UUID"),
                    "repository_id", Map.of("type", "string", "description", "Repository UUID"),
                    "tags", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Tags")
                ), List.of("content", "type"),
                (exchange, args) -> {
                    String content = (String) args.get("content");
                    String type = (String) args.get("type");
                    String scope = (String) args.get("scope");
                    String projectId = (String) args.get("project_id");
                    String repositoryId = (String) args.get("repository_id");
                    @SuppressWarnings("unchecked")
                    List<String> tags = args.get("tags") instanceof List ? (List<String>) args.get("tags") : null;
                    return toolHandler.handleStoreMemory(content, type, scope, projectId, repositoryId, tags);
                }),

            buildTool("brain_record_event",
                "Record an agent event",
                "object", Map.of(
                    "session_id", Map.of("type", "string", "description", "Session UUID"),
                    "event_type", Map.of("type", "string", "description", "Event type"),
                    "description", Map.of("type", "string", "description", "Event description"),
                    "file_path", Map.of("type", "string", "description", "Related file path"),
                    "status", Map.of("type", "string", "description", "Status (success/failure/partial)")
                ), List.of("event_type", "description"),
                (exchange, args) -> {
                    String sessionId = (String) args.get("session_id");
                    String eventType = (String) args.get("event_type");
                    String description = (String) args.get("description");
                    String filePath = (String) args.get("file_path");
                    String status = (String) args.get("status");
                    return toolHandler.handleRecordEvent(sessionId, eventType, description, filePath, status);
                }),

            buildTool("brain_start_session",
                "Start a new agent session",
                "object", Map.of(
                    "agent_name", Map.of("type", "string", "description", "Agent name"),
                    "task", Map.of("type", "string", "description", "Task description"),
                    "repository_id", Map.of("type", "string", "description", "Repository UUID"),
                    "project_id", Map.of("type", "string", "description", "Project UUID")
                ), List.of("agent_name", "task"),
                (exchange, args) -> {
                    String agentName = (String) args.get("agent_name");
                    String task = (String) args.get("task");
                    String repositoryId = (String) args.get("repository_id");
                    String projectId = (String) args.get("project_id");
                    return toolHandler.handleStartSession(agentName, task, repositoryId, projectId);
                }),

            buildTool("brain_end_session",
                "End an agent session",
                "object", Map.of(
                    "session_id", Map.of("type", "string", "description", "Session UUID"),
                    "summary", Map.of("type", "string", "description", "Session summary")
                ), List.of("session_id"),
                (exchange, args) -> {
                    String sessionId = (String) args.get("session_id");
                    String summary = (String) args.get("summary");
                    return toolHandler.handleEndSession(sessionId, summary);
                }),

            buildTool("brain_get_handoff",
                "Get the latest handoff for a repository",
                "object", Map.of(
                    "repository_id", Map.of("type", "string", "description", "Repository UUID")
                ), List.of("repository_id"),
                (exchange, args) -> {
                    String repositoryId = (String) args.get("repository_id");
                    return toolHandler.handleGetHandoff(repositoryId);
                }),

            buildTool("brain_create_handoff",
                "Create an agent handoff",
                "object", Map.of(
                    "session_id", Map.of("type", "string", "description", "Session UUID"),
                    "task", Map.of("type", "string", "description", "Task description"),
                    "completed_items", Map.of("type", "string", "description", "Completed items"),
                    "in_progress_items", Map.of("type", "string", "description", "In-progress items"),
                    "blocked_items", Map.of("type", "string", "description", "Blocked items"),
                    "changed_files", Map.of("type", "string", "description", "Changed files"),
                    "next_steps", Map.of("type", "string", "description", "Next steps"),
                    "decisions", Map.of("type", "string", "description", "Decisions made"),
                    "known_issues", Map.of("type", "string", "description", "Known issues")
                ), List.of("session_id", "task"),
                (exchange, args) -> {
                    String sessionId = (String) args.get("session_id");
                    String task = (String) args.get("task");
                    String completedItems = (String) args.get("completed_items");
                    String inProgressItems = (String) args.get("in_progress_items");
                    String blockedItems = (String) args.get("blocked_items");
                    String changedFiles = (String) args.get("changed_files");
                    String nextSteps = (String) args.get("next_steps");
                    String decisions = (String) args.get("decisions");
                    String knownIssues = (String) args.get("known_issues");
                    return toolHandler.handleCreateHandoff(sessionId, task, completedItems, inProgressItems,
                        blockedItems, changedFiles, nextSteps, decisions, knownIssues);
                }),

            buildTool("brain_record_decision",
                "Record an architectural decision",
                "object", Map.of(
                    "title", Map.of("type", "string", "description", "Decision title"),
                    "description", Map.of("type", "string", "description", "Decision description"),
                    "rationale", Map.of("type", "string", "description", "Why this decision"),
                    "project_id", Map.of("type", "string", "description", "Project UUID"),
                    "repository_id", Map.of("type", "string", "description", "Repository UUID"),
                    "tags", Map.of("type", "array", "items", Map.of("type", "string"))
                ), List.of("title", "description"),
                (exchange, args) -> {
                    String title = (String) args.get("title");
                    String description = (String) args.get("description");
                    String rationale = (String) args.get("rationale");
                    String projectId = (String) args.get("project_id");
                    String repositoryId = (String) args.get("repository_id");
                    @SuppressWarnings("unchecked")
                    List<String> tags = args.get("tags") instanceof List ? (List<String>) args.get("tags") : null;
                    return toolHandler.handleRecordDecision(title, description, rationale, projectId, repositoryId, tags);
                }),

            buildTool("brain_get_recent_activity",
                "Get recent agent activity",
                "object", Map.of(
                    "limit", Map.of("type", "integer", "description", "Max results")
                ), List.of(),
                (exchange, args) -> {
                    int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 20;
                    return toolHandler.handleGetRecentActivity(limit);
                }),

            buildTool("brain_create_task",
                "Create a new task",
                "object", Map.of(
                    "title", Map.of("type", "string", "description", "Task title"),
                    "description", Map.of("type", "string", "description", "Task description"),
                    "project_id", Map.of("type", "string", "description", "Project UUID"),
                    "repository_id", Map.of("type", "string", "description", "Repository UUID"),
                    "priority", Map.of("type", "integer", "description", "Priority 1-5")
                ), List.of("title"),
                (exchange, args) -> {
                    String title = (String) args.get("title");
                    String description = (String) args.get("description");
                    String projectId = (String) args.get("project_id");
                    String repositoryId = (String) args.get("repository_id");
                    Integer priority = args.containsKey("priority") ? ((Number) args.get("priority")).intValue() : null;
                    return toolHandler.handleCreateTask(title, description, projectId, repositoryId, priority);
                }),

            buildTool("brain_get_open_tasks",
                "Get all open tasks",
                "object", Map.of(
                    "project_id", Map.of("type", "string", "description", "Project UUID filter")
                ), List.of(),
                (exchange, args) -> {
                    String projectId = (String) args.get("project_id");
                    return toolHandler.handleGetOpenTasks(projectId);
                }),

            buildTool("brain_knowledge_graph",
                "Query the knowledge graph — list nodes by label or traverse relationships from a specific node",
                "object", Map.of(
                    "label", Map.of("type", "string", "description", "Node label (Project, Repository, Technology, Agent, Memory, etc.)"),
                    "id", Map.of("type", "string", "description", "Specific node ID to traverse from (omit to list all nodes of the label)"),
                    "depth", Map.of("type", "integer", "description", "Traversal depth (default 2)")
                ), List.of("label"),
                (exchange, args) -> {
                    String label = (String) args.get("label");
                    String id = (String) args.get("id");
                    Integer depth = args.containsKey("depth") ? ((Number) args.get("depth")).intValue() : null;
                    return toolHandler.handleKnowledgeGraph(label, id, depth);
                }),

            buildTool("brain_get_context",
                "Assemble full context for a query — searches memories, graph, events, decisions, tasks, and handoffs, then deduplicates and ranks",
                "object", Map.of(
                    "query", Map.of("type", "string", "description", "Natural language query to assemble context for"),
                    "project_id", Map.of("type", "string", "description", "Optional project UUID to scope results"),
                    "repository_id", Map.of("type", "string", "description", "Optional repository UUID to scope results")
                ), List.of("query"),
                (exchange, args) -> {
                    String query = (String) args.get("query");
                    String projectId = (String) args.get("project_id");
                    String repositoryId = (String) args.get("repository_id");
                    return toolHandler.handleGetContext(query, projectId, repositoryId);
                }),

            buildTool("brain_doctor",
                "Run health diagnostics on all Second Brain services (PostgreSQL, Redis, Qdrant, Neo4j, MinIO)",
                "object", Map.of(), List.of(),
                (exchange, args) -> toolHandler.handleBrainDoctor()),

            buildTool("brain_evaluate_quality",
                "Evaluate retrieval quality against a test dataset of developer questions (precision, recall, F1)",
                "object", Map.of(), List.of(),
                (exchange, args) -> toolHandler.handleEvaluateQuality())
        );
    }

    private List<SyncResourceSpecification> buildResources() {
        return List.of(
            new SyncResourceSpecification(
                new Resource("brain://developer/profile", "Developer Profile",
                    "Developer profile and preferences", "text/plain", null),
                (exchange, request) -> new ReadResourceResult(List.of(
                    new TextResourceContents("brain://developer/profile", "text/plain",
                        "Developer profile not yet populated")))),

            new SyncResourceSpecification(
                new Resource("brain://projects", "All Projects",
                    "List of all projects", "application/json", null),
                (exchange, request) -> new ReadResourceResult(List.of(
                    new TextResourceContents("brain://projects", "application/json", "[]"))))
        );
    }

    private SyncToolSpecification buildTool(String name, String description,
            String type, Map<String, Object> properties, List<String> required,
            java.util.function.BiFunction<McpSyncServerExchange, Map<String, Object>, CallToolResult> handler) {
        JsonSchema schema = new JsonSchema(type, properties, required, null, null, null);
        Tool tool = new Tool(name, description, schema);
        return new SyncToolSpecification(tool, handler);
    }

    @PreDestroy
    public void close() {
        if (mcpServer != null) {
            mcpServer.close();
        }
        if (transportProvider != null) {
            transportProvider.close();
        }
        log.info("MCP Server closed");
    }
}
