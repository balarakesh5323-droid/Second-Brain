# Second Brain

A persistent, MCP-native, multi-repository, self-growing AI knowledge system for developers.

## Architecture

```
AI Agents (Claude Code, Codex, Cursor, etc.)
    |
    | MCP Protocol
    v
Second Brain MCP Server
    |
    +-- Knowledge Layer (Qdrant - Vector Search)
    +-- Memory Layer (PostgreSQL - Structured Data)
    +-- Graph Layer (Neo4j - Knowledge Graph)
    +-- Hot Memory (Redis - Session State)
    +-- Documents (MinIO - File Storage)
```

## Quick Start

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Node.js 18+ (for dashboard)

### Development

1. Start infrastructure:
```bash
cd second-brain
docker-compose up -d postgres redis neo4j minio
```

2. Build and run backend:
```bash
./gradlew :backend:bootRun
```

3. Run dashboard:
```bash
cd dashboard
npm install
npm run dev
```

4. Access:
- API: http://localhost:8080
- Dashboard: http://localhost:3000
- MinIO Console: http://localhost:9001
- Neo4j Browser: http://localhost:7474

### Docker Compose (Full)

```bash
docker-compose up -d
```

## MCP Integration

### Claude Code

Add to `.claude/settings.json`:
```json
{
  "mcpServers": {
    "second-brain": {
      "url": "http://localhost:8080/mcp/messages"
    }
  }
}
```

### Codex

Add to `~/.codex/config.json`:
```json
{
  "mcpServers": {
    "second-brain": {
      "url": "http://localhost:8080/mcp/messages"
    }
  }
}
```

## API Reference

### Memory
- `POST /api/v1/memory` - Create memory
- `GET /api/v1/memory` - List all memories
- `GET /api/v1/memory/{id}` - Get memory by ID
- `GET /api/v1/memory/search?q=` - Search memories
- `PUT /api/v1/memory/{id}` - Update memory
- `DELETE /api/v1/memory/{id}` - Delete memory

### Projects
- `POST /api/v1/projects` - Create project
- `GET /api/v1/projects` - List projects
- `GET /api/v1/projects/{id}` - Get project

### Repositories
- `POST /api/v1/repositories` - Create repository
- `GET /api/v1/repositories` - List repositories

### Agents
- `POST /api/v1/agents` - Create agent
- `GET /api/v1/agents` - List agents

### Sessions
- `POST /api/v1/sessions/start` - Start session
- `POST /api/v1/sessions/{id}/end` - End session
- `GET /api/v1/sessions/recent` - Recent sessions

### Events
- `POST /api/v1/events` - Record event
- `GET /api/v1/events` - Recent events

### Handoffs
- `POST /api/v1/handoffs` - Create handoff
- `GET /api/v1/handoffs/repository/{id}/latest` - Latest handoff

### Decisions
- `POST /api/v1/decisions` - Record decision
- `GET /api/v1/decisions` - List decisions

### Tasks
- `POST /api/v1/tasks` - Create task
- `GET /api/v1/tasks/open` - Open tasks

### Skills
- `POST /api/v1/skills` - Create skill
- `GET /api/v1/skills` - List skills
- `GET /api/v1/skills/match?trigger=` - Match skills

### Documents
- `POST /api/v1/documents/upload` - Upload document
- `GET /api/v1/documents` - List documents

### Graph
- `GET /api/v1/graph/stats` - Graph statistics
- `GET /api/v1/graph/nodes/{label}` - Get nodes
- `POST /api/v1/graph/sync` - Sync to graph

## MCP Tools

| Tool | Description |
|------|-------------|
| `brain_search` | Search across all memories |
| `brain_ask` | Ask natural language questions |
| `brain_projects` | List projects |
| `brain_store_memory` | Store a new memory |
| `brain_record_event` | Record an agent event |
| `brain_start_session` | Start an agent session |
| `brain_end_session` | End an agent session |
| `brain_get_handoff` | Get latest handoff |
| `brain_create_handoff` | Create agent handoff |
| `brain_record_decision` | Record a decision |
| `brain_get_recent_activity` | Get recent activity |
| `brain_create_task` | Create a task |
| `brain_get_open_tasks` | Get open tasks |
| `brain_knowledge_graph` | Query knowledge graph |

## Configuration

See `.env.example` for all configuration options.

## License

MIT
