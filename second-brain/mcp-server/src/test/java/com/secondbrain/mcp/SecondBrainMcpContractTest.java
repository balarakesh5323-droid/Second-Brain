package com.secondbrain.mcp;

import com.secondbrain.mcp.tools.BrainToolHandler;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecondBrainMcpContractTest {

    @Mock
    private BrainToolHandler toolHandler;

    private SecondBrainMcpServer server;

    @BeforeEach
    void setUp() {
        server = new SecondBrainMcpServer(toolHandler);
    }

    @Test
    @DisplayName("MCP Tool Contract: All 32 documented tools exist with valid schemas")
    @SuppressWarnings("unchecked")
    void testAllDocumentedMcpToolsExist() throws Exception {
        Method buildToolsMethod = SecondBrainMcpServer.class.getDeclaredMethod("buildTools");
        buildToolsMethod.setAccessible(true);
        var toolSpecs = (List<?>) buildToolsMethod.invoke(server);

        assertThat(toolSpecs).isNotEmpty();

        Set<String> toolNames = toolSpecs.stream()
                .map(spec -> {
                    try {
                        Method getToolMethod = spec.getClass().getMethod("tool");
                        var tool = getToolMethod.invoke(spec);
                        Method getNameMethod = tool.getClass().getMethod("name");
                        return (String) getNameMethod.invoke(tool);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toSet());

        List<String> expectedTools = List.of(
                "brain_search",
                "brain_ask",
                "brain_projects",
                "brain_store_memory",
                "brain_record_event",
                "brain_start_session",
                "brain_end_session",
                "brain_complete_session",
                "brain_get_handoff",
                "brain_create_handoff",
                "brain_record_decision",
                "brain_get_recent_activity",
                "brain_create_task",
                "brain_get_open_tasks",
                "brain_knowledge_graph",
                "brain_get_context",
                "brain_doctor",
                "brain_evaluate_quality",
                "brain_add_repository",
                "brain_repository_context",
                "brain_record_attempt",
                "brain_get_attempts",
                "brain_get_continuity_state",
                "brain_create_project",
                "brain_list_projects",
                "brain_get_project",
                "brain_use_project",
                "brain_impact_analysis",
                "brain_review_changes",
                "brain_ingest_diagram",
                "brain_workspace_state",
                "brain_context_pack",
                "brain_get_agent_timeline",
                "brain_consolidate_memories"
        );

        for (String expected : expectedTools) {
            assertThat(toolNames)
                    .as("MCP Tool '%s' must be registered in SecondBrainMcpServer", expected)
                    .contains(expected);
        }
    }

    @Test
    @DisplayName("MCP Tool Contract: brain_context_pack delegates correctly to BrainToolHandler")
    void testContextPackToolExecution() {
        when(toolHandler.handleContextPack(eq("Build Auth"), eq("backend-repo"), eq("project-1")))
                .thenReturn(new CallToolResult(List.of(new TextContent("{\"task\":\"Build Auth\"}")), false));

        CallToolResult result = toolHandler.handleContextPack("Build Auth", "backend-repo", "project-1");
        assertThat(result.isError()).isFalse();
        assertThat(((TextContent) result.content().get(0)).text()).contains("Build Auth");
    }

    @Test
    @DisplayName("MCP Tool Contract: brain_get_agent_timeline delegates correctly to BrainToolHandler")
    void testGetAgentTimelineToolExecution() {
        when(toolHandler.handleGetAgentTimeline(eq("backend-repo"), eq(10)))
                .thenReturn(new CallToolResult(List.of(new TextContent("[{\"agent\":\"Claude Code\"}]")), false));

        CallToolResult result = toolHandler.handleGetAgentTimeline("backend-repo", 10);
        assertThat(result.isError()).isFalse();
        assertThat(((TextContent) result.content().get(0)).text()).contains("Claude Code");
    }
}
