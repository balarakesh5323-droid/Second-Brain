# MCP Server

## Overview

The Second Brain MCP Server exposes the knowledge base to any MCP-compatible AI agent (Claude Code, Codex, Cursor, etc.) via the Model Context Protocol.

## Connection

### SSE Transport
```
Endpoint: http://localhost:8080/mcp/messages
Protocol: Server-Sent Events (SSE)
```

### Claude Desktop Configuration
```json
{
  "mcpServers": {
    "second-brain": {
      "url": "http://localhost:8080/mcp/messages"
    }
  }
}
```

### Cursor Configuration
```json
{
  "mcpServers": {
    "second-brain": {
      "url": "http://localhost:8080/mcp/messages"
    }
  }
}
```

## Tools (17)

### Brain Search
Search memories by keyword across all collections.
```json
{"query": "authentication", "limit": 10}
```

### Brain Ask
Natural language question against the knowledge base.
```json
{"question": "What database does the auth service use?"}
```

### Brain Get Context
Assemble full context using the 12-step pipeline. Returns structured JSON with relevant_context, architecture, decisions, open_tasks, recent_changes, known_problems.
```json
{"query": "payment implementation", "project_id": "...", "repository_id": "..."}
```

### Brain Store Memory
Store a new memory with type, scope, and tags.
```json
{"content": "Use Redis for session caching", "type": "DECLARATIVE", "scope": "PROJECT", "tags": ["redis", "caching"]}
```

### Brain Record Event
Record an agent event during a session.
```json
{"session_id": "...", "event_type": "CODE_CHANGE", "description": "Updated PaymentService"}
```

### Brain Start/End Session
Manage agent session lifecycle.
```json
{"agent_name": "claude-code", "task": "Implement payment webhook"}
```

### Brain Get/Create Handoff
Pass context between agents.
```json
{"repository_id": "...", "task": "...", "completed_items": "...", "next_steps": "..."}
```

### Brain Record Decision
Record architectural decisions with rationale.
```json
{"title": "Use Stripe", "description": "...", "rationale": "...", "tags": ["payment"]}
```

### Brain Knowledge Graph
Query the Neo4j knowledge graph — list nodes or traverse relationships.
```json
{"label": "Technology", "id": "...", "depth": 2}
```

### Brain Doctor
Run health diagnostics on all services.
```json
{}
```

### Brain Evaluate Quality
Evaluate retrieval precision/recall against test dataset.
```json
{}
```

## Resources (2)

| URI | Description |
|-----|-------------|
| `brain://developer/profile` | Developer profile and preferences |
| `brain://projects` | All projects |

## Agent Integration Examples

### Claude Code
```bash
# In .claude/settings.json
{
  "mcpServers": {
    "second-brain": {
      "url": "http://localhost:8080/mcp/messages"
    }
  }
}
```

### Python Client
```python
from mcp import ClientSession, SSEClientTransport

async with SSEClientTransport("http://localhost:8080/mcp/messages") as transport:
    async with ClientSession(transport) as session:
        await session.initialize()
        result = await session.call_tool("brain_ask", {"question": "What is the auth architecture?"})
        print(result)
```
