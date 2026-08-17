# Architecture

## System Overview

Second Brain is a **persistent, structured, evolving external developer brain**. It is NOT a chatbot with RAG — it is the long-term system of record for all project knowledge. Agents are temporary clients.

## Module Structure

```
second-brain/
├── backend/          Spring Boot REST API + services
├── common/           Shared entities, DTOs, repositories, enums
├── mcp-server/       MCP protocol server (17 tools, 2 resources)
├── workers/          Background processing (5 scheduled workers)
├── cli/              CLI tool (brain search, brain ask, etc.)
├── dashboard/        React + Vite + Tailwind UI
└── docker/           Docker Compose deployment
```

## Core Components

### Backend (Spring Boot 3.3.5)
- **REST API**: 15+ endpoint groups (Memory, Project, Agent, Decision, Task, Technology, Skill, Session, Event, Graph, Document, Handoff, Health, Quality)
- **ContextAssemblyService**: 12-step pipeline that assembles context from all sources
- **SemanticSearchService**: Hybrid search across 8 Qdrant collections
- **GraphService**: Neo4j knowledge graph traversal and querying
- **BrainDoctorService**: Health diagnostics for all services
- **RetrievalQualityService**: Precision/recall evaluation against test dataset

### MCP Server (17 tools)
| Tool | Purpose |
|------|---------|
| `brain_search` | Search memories by keyword |
| `brain_ask` | Natural language question |
| `brain_get_context` | Full context assembly (12-step pipeline) |
| `brain_store_memory` | Store a new memory |
| `brain_record_event` | Record an agent event |
| `brain_start_session` | Start agent session |
| `brain_end_session` | End agent session |
| `brain_get_handoff` | Get latest handoff |
| `brain_create_handoff` | Create agent handoff |
| `brain_record_decision` | Record architectural decision |
| `brain_get_recent_activity` | Recent agent events |
| `brain_create_task` | Create task |
| `brain_get_open_tasks` | List open tasks |
| `brain_knowledge_graph` | Query knowledge graph |
| `brain_projects` | List projects |
| `brain_doctor` | Health diagnostics |
| `brain_evaluate_quality` | Retrieval quality evaluation |

### Workers (5 scheduled tasks)
| Worker | Schedule | Purpose |
|--------|----------|---------|
| DeduplicationWorker | Hourly | Merge near-duplicate memories |
| ContradictionDetectionWorker | Every 2 hours | Flag contradictory memories |
| MemoryDecayWorker | Daily | Decay/archive old memories |
| KnowledgeEvolutionWorker | Daily | Promote/demote memory status |
| SkillEvolutionWorker | Weekly | Update skill confidence/triggers |

## Data Flow

```
Agent connects via MCP
    ↓
Brain assembles context (Qdrant + Neo4j + Redis + Postgres)
    ↓
Agent receives structured context
    ↓
Agent works and records events/decisions/memories
    ↓
Brain indexes new knowledge
    ↓
Workers clean up (dedup, decay, contradictions)
    ↓
Next agent benefits from accumulated knowledge
```

## Storage Architecture

| Store | Purpose | Connection |
|-------|---------|------------|
| PostgreSQL/H2 | Structured data, entities | JDBC |
| Qdrant | Vector embeddings (8 collections) | gRPC :6334 |
| Neo4j | Knowledge graph, relationships | Bolt :7687 |
| Redis | Hot memory, session state | TCP :6379 |
| MinIO | Documents, artifacts | HTTP :9000 |

### Qdrant Collections
- `global_knowledge` — Global memories
- `project_knowledge` — Project-scoped memories
- `repository_knowledge` — Repository-scoped memories
- `code_knowledge` — Code-related memories
- `conversation_memory` — Conversation history
- `agent_memory` — Agent session memories
- `technical_memory` — Technical decisions
- `documentation` — Documentation content

## Context Assembly Pipeline (12 Steps)

1. **Parse query intent** — Extract keywords, detect entity types
2. **Resolve scope** — Identify project/repository from query or params
3. **Search Qdrant** — Semantic vector similarity across all collections
4. **Query Neo4j** — Knowledge graph traversal for related entities
5. **Query PostgreSQL** — Recent events, decisions, tasks
6. **Query Redis** — Hot memory (frequently accessed)
7. **Retrieve artifacts** — Handoffs, documents
8. **Deduplicate** — Remove exact/near-duplicate results
9. **Resolve contradictions** — Flag conflicting memories
10. **Rank by relevance** — Score using confidence, importance, recency, keyword match
11. **Compress** — Take top 15 most relevant results
12. **Return structured JSON** — ContextResponse format

## Security

- Self-hosted (no external data leaving your infrastructure)
- API key authentication (configurable)
- Content redaction for secrets/keys
- OAuth2/JWT support (optional)
- CORS configured per environment
