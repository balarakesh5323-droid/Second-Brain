# Second Brain — Implementation Todo

## Phase 1: Foundation
- [ ] 1.1 Project structure & Gradle multi-module setup
- [ ] 1.2 PostgreSQL schema & JPA entities
- [ ] 1.3 Redis integration (connection, caching)
- [ ] 1.4 Docker Compose (api, postgres, redis, minio)
- [ ] 1.5 Memory API (CRUD, types, lifecycle)
- [ ] 1.6 Event API (record events, query events)
- [ ] 1.7 Project & Repository entities & API
- [ ] 1.8 Agent & Session entities & API
- [ ] 1.9 Decision & Task entities & API
- [ ] 1.10 Basic health check & configuration

## Phase 2: Qdrant Vector Search
- [ ] 2.1 Qdrant integration & collection setup
- [ ] 2.2 Embedding pipeline
- [ ] 2.3 Semantic search API
- [ ] 2.4 Hybrid retrieval (vector + structured)
- [ ] 2.5 Qdrant collections per memory type

## Phase 3: Neo4j Knowledge Graph
- [ ] 3.1 Neo4j integration & connection
- [ ] 3.2 Graph schema & nodes
- [ ] 3.3 Relationships (Technology, Repository, Agent, Project)
- [ ] 3.4 Graph queries API
- [ ] 3.5 Graph sync with PostgreSQL

## Phase 4: Repository Intelligence
- [ ] 4.1 Git integration
- [ ] 4.2 Repository indexing
- [ ] 4.3 AST parsing for Java
- [ ] 4.4 Code symbol extraction
- [ ] 4.5 Dependency graph

## Phase 5: MCP Server
- [ ] 5.1 MCP server skeleton (Java)
- [ ] 5.2 MCP tools (search, ask, projects, repository)
- [ ] 5.3 MCP tools (memory, sessions, handoffs)
- [ ] 5.4 MCP tools (activity, knowledge, decisions, tasks, skills)
- [ ] 5.5 MCP resources (brain:// URIs)
- [ ] 5.6 Agent authentication

## Phase 6: Agent Memory
- [ ] 6.1 Agent sessions & events
- [ ] 6.2 Agent handoff protocol
- [ ] 6.3 Cross-agent continuity
- [ ] 6.4 Handoff API

## Phase 7: Skills
- [ ] 7.1 Skill model & storage
- [ ] 7.2 Skill extraction
- [ ] 7.3 Skill matching
- [ ] 7.4 Skill evolution

## Phase 8: Documents & MinIO
- [ ] 8.1 MinIO integration
- [ ] 8.2 Document parsing pipeline
- [ ] 8.3 Document storage API
- [ ] 8.4 Visual memory (images/screenshots)

## Phase 9: Dashboard
- [ ] 9.1 React + Vite project setup
- [ ] 9.2 Home dashboard
- [ ] 9.3 Knowledge graph visualization
- [ ] 9.4 Agent activity timeline
- [ ] 9.5 Repository explorer
- [ ] 9.6 Memory explorer
- [ ] 9.7 Skills & Handoffs views

## Phase 10: Self-Improvement & Polish
- [ ] 10.1 Deduplication worker
- [ ] 10.2 Contradiction detection
- [ ] 10.3 Memory decay
- [ ] 10.4 Knowledge evolution
- [ ] 10.5 Skill evolution
- [ ] 10.6 CLI tool
- [ ] 10.7 Kubernetes manifests
- [ ] 10.8 Observability (OpenTelemetry, Prometheus, Grafana)
- [ ] 10.9 Security (auth, API keys, redaction)
- [ ] 10.10 Documentation & examples
