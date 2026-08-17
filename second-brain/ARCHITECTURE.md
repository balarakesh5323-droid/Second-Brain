# Architecture

## System Components

### Backend (Spring Boot 3.3.5)
- REST API controllers
- Service layer
- JPA repositories
- Redis caching
- Qdrant vector search
- Neo4j knowledge graph
- MinIO document storage

### MCP Server
- 15 MCP tools
- 2 MCP resources
- SSE transport

### Workers
- Deduplication
- Contradiction detection
- Memory decay
- Skill evolution
- Knowledge evolution

### Dashboard (React + Vite)
- Home dashboard
- Memory explorer
- Agent activity
- Repository explorer
- Skills view
- Handoffs view

## Data Flow

1. Agent connects via MCP
2. Brain provides context (handoffs, decisions, memories)
3. Agent works and records events
4. Brain indexes new knowledge
5. Knowledge graph updates
6. Vector store updates
7. Next agent benefits from accumulated knowledge

## Storage

| Store | Purpose |
|-------|---------|
| PostgreSQL | Structured data, metadata |
| Qdrant | Vector embeddings, semantic search |
| Neo4j | Knowledge graph, relationships |
| Redis | Hot memory, session state |
| MinIO | Documents, artifacts |
