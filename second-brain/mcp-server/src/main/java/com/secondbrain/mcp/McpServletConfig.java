package com.secondbrain.mcp;

import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServletConfig {
    // MCP server transport is configured in SecondBrainMcpServer
    // Servlet registration is handled by HttpServletSseServerTransportProvider
}
