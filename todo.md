# Second Brain — Implementation Todo

## Phase 1: Foundation
- [x] 1.1 Project structure & Gradle multi-module setup
- [x] 1.2 PostgreSQL schema & JPA entities
- [x] 1.3 Redis integration (connection, caching)
- [x] 1.4 Docker Compose (api, postgres, redis, minio)
- [x] 1.5 Memory API (CRUD, types, lifecycle)
- [x] 1.6 Event API (record events, query events)
- [x] 1.7 Project & Repository entities & API
- [x] 1.8 Agent & Session entities & API
- [x] 1.9 Decision & Task entities & API
- [x] 1.10 Basic health check & configuration

## Phase 2: Qdrant Vector Search
- [x] 2.1 Qdrant integration & collection setup
- [x] 2.2 Embedding pipeline
- [x] 2.3 Semantic search API
- [x] 2.4 Hybrid retrieval (vector + structured)
- [x] 2.5 Qdrant collections per memory type

## Phase 3: Neo4j Knowledge Graph
- [x] 3.1 Neo4j integration & connection
- [x] 3.2 Graph schema & nodes
- [x] 3.3 Relationships (Technology, Repository, Agent, Project)
- [x] 3.4 Graph queries API
- [x] 3.5 Graph sync with PostgreSQL

## Phase 4: Repository Intelligence
- [x] 4.1 Git integration
- [x] 4.2 Repository indexing
- [x] 4.3 AST parsing for Java
- [x] 4.4 Code symbol extraction
- [x] 4.5 Dependency graph

## Phase 5: MCP Server
- [x] 5.1 MCP server skeleton (Java)
- [x] 5.2 MCP tools (search, ask, projects, repository)
- [x] 5.3 MCP tools (memory, sessions, handoffs)
- [x] 5.4 MCP tools (activity, knowledge, decisions, tasks, skills)
- [x] 5.5 MCP resources (brain:// URIs)
- [x] 5.6 Agent authentication

## Phase 6: Agent Memory
- [x] 6.1 Agent sessions & events
- [x] 6.2 Agent handoff protocol
- [x] 6.3 Cross-agent continuity
- [x] 6.4 Handoff API

## Phase 7: Skills
- [x] 7.1 Skill model & storage
- [x] 7.2 Skill extraction
- [x] 7.3 Skill matching
- [x] 7.4 Skill evolution

## Phase 8: Documents & MinIO
- [x] 8.1 MinIO integration
- [x] 8.2 Document parsing pipeline
- [x] 8.3 Document storage API
- [x] 8.4 Visual memory (images/screenshots)

## Phase 9: Dashboard
- [x] 9.1 React + Vite project setup
- [x] 9.2 Home dashboard
- [x] 9.3 Knowledge graph visualization
- [x] 9.4 Agent activity timeline
- [x] 9.5 Repository explorer
- [x] 9.6 Memory explorer
- [x] 9.7 Skills & Handoffs views

## Phase 10: Self-Improvement & Polish
- [x] 10.1 Deduplication worker
- [x] 10.2 Contradiction detection
- [x] 10.3 Memory decay
- [x] 10.4 Knowledge evolution
- [x] 10.5 Skill evolution
- [x] 10.6 CLI tool (init, watch, search, ask, remember, context, status, handoff)
- [x] 10.7 Kubernetes manifests
- [ ] 10.8 Observability (OpenTelemetry, Prometheus, Grafana) (future)
- [x] 10.9 Security (auth, API keys, redaction)
- [x] 10.10 Documentation & examples
