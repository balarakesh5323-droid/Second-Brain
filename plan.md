# Build a Persistent Developer Second Brain — MCP-Native, Multi-Repository, Self-Growing AI Knowledge System

## Role

You are a senior AI systems architect, backend engineer, knowledge-graph engineer, RAG engineer, and developer-tools engineer.

Design and implement a production-grade **Developer Second Brain** for a full-stack microservice developer who works across:

- Multiple Git repositories
- Multiple Spring Boot microservices
- React/Vite frontends
- Java, Python, TypeScript/JavaScript, SQL, Bash and other languages
- Databases and infrastructure
- Docker / Docker Compose
- Kubernetes / K3s
- CI/CD
- AI/LLM development
- RAG systems
- MCP
- AI agents
- Multiple coding agents such as Claude Code, Codex, Qwen, Cursor, OpenCode, etc.

The system must behave like a **persistent external brain** that grows with the developer over months and years.

The brain must NOT belong to a single AI provider.

Any compatible AI agent should be able to connect to it through **MCP** and immediately access the same knowledge.

---

# 1. Core Vision

Build a system that answers:

> "What does my AI/development ecosystem already know about this?"

Examples:

- "What did Claude Code change yesterday?"
- "Codex, continue what Claude Code was doing."
- "Why did we choose PostgreSQL instead of MongoDB?"
- "How does authentication work across my services?"
- "Which repositories use RabbitMQ?"
- "Show me all services that depend on auth-service."
- "What was the reason we introduced Redis?"
- "How did I configure this project six months ago?"
- "What database migration strategy do we use?"
- "What did we learn about Qdrant?"
- "What is my preferred Spring Boot architecture?"
- "Which AI models have I tested?"
- "What happened when I tried model X?"
- "What commands did I use to deploy this service?"
- "What did the previous agent attempt before it failed?"
- "What remains unfinished from the previous coding session?"
- "What conventions do my repositories follow?"
- "How does this particular codebase differ from my other projects?"

The Second Brain should make these questions answerable without relying on the current AI agent's context window.

---

# 2. Most Important Principle

## Separate MEMORY from AGENTS

The Second Brain must be an independent service.

Architecture:

```text
                    ┌─────────────────────┐
                    │     AI AGENTS       │
                    │                     │
                    │ Claude Code         │
                    │ Codex               │
                    │ Cursor              │
                    │ Qwen                │
                    │ OpenCode             │
                    │ Custom Agents       │
                    └──────────┬──────────┘
                               │
                               │ MCP
                               ▼
                    ┌─────────────────────┐
                    │ SECOND BRAIN MCP    │
                    │       SERVER        │
                    └──────────┬──────────┘
                               │
                 ┌─────────────┼─────────────┐
                 │             │             │
                 ▼             ▼             ▼
             Knowledge       Memory        Skills
               Layer          Layer         Layer
                 │             │             │
                 └─────────────┼─────────────┘
                               │
             ┌─────────────────┼─────────────────┐
             │                 │                 │
             ▼                 ▼                 ▼
          Qdrant             Neo4j             Redis
        Vector Memory     Knowledge Graph    Hot Memory
             │                 │                 │
             └─────────────────┼─────────────────┘
                               │
                               ▼
                             MinIO
                      Documents / Artifacts
                               │
                               ▼
                         PostgreSQL
                  Metadata / Transactions

```

The AI agent is replaceable.

The Second Brain is permanent.

---

# 3. Technology Stack

Use:

## Backend

Prefer:

- Java 21+
- Spring Boot
- Spring WebFlux where appropriate
- LangChain4j where useful
- MCP Java SDK / MCP-compatible implementation
- PostgreSQL

## Storage

### PostgreSQL

Use for:

- Users
- Projects
- repositories
- sessions
- events
- memories
- decisions
- tasks
- documents metadata
- agent sessions
- agent actions
- skills
- memory lifecycle
- audit records

### Qdrant

Use for semantic/vector retrieval.

Store embeddings for:

- source code chunks
- documentation
- architecture decisions
- conversations
- agent sessions
- lessons learned
- errors
- troubleshooting
- technical concepts
- personal development knowledge
- project knowledge
- commands
- API documentation
- database knowledge
- technology knowledge

Support multiple collections or payload-based namespaces.

At minimum:

```text
global_knowledge
project_knowledge
repository_knowledge
code_knowledge
conversation_memory
agent_memory
technical_memory
documentation

```

### Neo4j

Use as the long-term knowledge graph.

Represent relationships such as:

```text
Developer
  ↓
Project
  ↓
Repository
  ↓
Service
  ↓
Module
  ↓
Class
  ↓
Method
  ↓
Database
  ↓
Table

```

Also:

```text
Agent
 ↓
Session
 ↓
Action
 ↓
FileChange
 ↓
Commit
 ↓
Decision

```

And:

```text
Technology
 ↓
UsedBy
 ↓
Repository

```

Examples:

```text
SpringBoot ──USED_BY──> auth-service

auth-service ──DEPENDS_ON──> PostgreSQL

order-service ──COMMUNICATES_WITH──> payment-service

ClaudeCode ──MODIFIED──> AuthController.java

Codex ──CONTINUED──> ClaudeCodeSession

Developer ──PREFERS──> Spring Boot

ProjectA ──USES──> Redis

```

### Redis

Use for:

- hot memory
- session state
- agent context
- MCP request caching
- retrieval cache
- short-term memory
- locks
- distributed coordination
- event queues where appropriate

### MinIO

Use for large/unstructured artifacts:

- repository snapshots
- documents
- PDFs
- images
- architecture diagrams
- screenshots
- logs
- generated reports
- agent transcripts
- code analysis artifacts
- embeddings metadata exports
- repository archives

Do not put large binary artifacts into PostgreSQL.

---

# 4. Memory Architecture

Implement multiple memory types.

## 4.1 Short-Term Memory

Temporary context.

Examples:

```text
Current task
Current repository
Current branch
Current files
Current conversation
Current agent session
Current debugging state

```

Store primarily in Redis.

---

## 4.2 Episodic Memory

Remember what happened.

Examples:

```text
Claude Code modified AuthService.java.

Claude Code attempted migration X.

Codex fixed an error introduced by Claude.

Developer installed Qdrant.

Developer experimented with Qwen.

Developer changed Redis configuration.

Deployment failed because port 8080 was already occupied.

```

Each event should have:

```json
{
  "timestamp": "...",
  "agent": "claude-code",
  "project": "...",
  "repository": "...",
  "branch": "...",
  "action": "...",
  "files": [],
  "result": "...",
  "reason": "...",
  "status": "success|failure|partial",
  "commit": "...",
  "session_id": "..."
}

```

---

# 5. Agent Activity Memory

This is a critical feature.

Every agent connected to the Second Brain should be able to record:

```text
SESSION_STARTED
TASK_STARTED
PLAN_CREATED
FILE_READ
FILE_CREATED
FILE_MODIFIED
FILE_DELETED
COMMAND_EXECUTED
TEST_EXECUTED
TEST_FAILED
TEST_PASSED
BUILD_STARTED
BUILD_FAILED
BUILD_SUCCEEDED
GIT_COMMIT
GIT_PUSH
DECISION_MADE
PROBLEM_DISCOVERED
SOLUTION_FOUND
TASK_COMPLETED
TASK_BLOCKED
SESSION_ENDED

```

The brain should maintain an immutable event history.

Example:

```text
Claude Code
  Session 9821
       │
       ├── inspected auth-service
       ├── identified JWT bug
       ├── modified JwtFilter.java
       ├── modified SecurityConfig.java
       ├── ran tests
       ├── tests failed
       ├── changed token expiration logic
       ├── tests passed
       └── committed changes

```

Later:

```text
Codex:
"What did Claude do to authentication?"

Second Brain:
"Claude Code investigated a JWT expiration issue in auth-service,
modified JwtFilter.java and SecurityConfig.java, initially failed
tests, corrected the token expiration logic, and committed the
successful change."

```

This is essential.

---

# 6. Cross-Agent Continuity

Implement a concept called:

## Agent Handoff

Any agent can leave a structured handoff.

Example:

```json
{
  "session": "claude-9821",
  "repository": "automorium_backend",
  "task": "Implement OAuth refresh tokens",
  "completed": [
    "Created RefreshToken entity",
    "Added repository",
    "Added service layer"
  ],
  "in_progress": [
    "Implement controller"
  ],
  "blocked": [
    "Need decision about token expiration"
  ],
  "changed_files": [
    "RefreshToken.java",
    "RefreshTokenRepository.java",
    "RefreshTokenService.java"
  ],
  "next_steps": [
    "Implement controller",
    "Add integration tests"
  ],
  "decisions": [
    "Use PostgreSQL for refresh token persistence"
  ]
}

```

When Codex starts working:

```text
brain.get_handoff(
    repository="automorium_backend"
)

```

Codex should automatically receive:

```text
Previous agent:
Claude Code

Task:
OAuth refresh tokens

Completed:
...

Remaining:
...

Important decisions:
...

Known problems:
...

```

---

# 7. Developer Knowledge

The brain should learn the developer's knowledge over time.

Examples:

```text
Java
Spring Boot
Hibernate
PostgreSQL
Redis
RabbitMQ
Kafka
Docker
Kubernetes
Qdrant
Neo4j
MinIO
React
TypeScript
Python
LangChain4j
MCP
RAG
LLMs

```

But don't simply store facts.

Store:

```text
Concept
Experience
Confidence
Source
Projects
Usage
Lessons
Related technologies

```

Example:

```text
Technology: Redis

Developer Experience:
High

Used In:
project-a
project-b
project-c

Known Use Cases:
- caching
- distributed locks
- session storage

Developer Preference:
Prefer Redis for short-lived hot state.

Evidence:
Multiple projects.

```

---

# 8. Learning System

The brain must continuously grow.

When the developer encounters something new:

```text
Developer learns ClickHouse

```

The brain should create:

```text
Technology: ClickHouse

Category:
Database

Concepts:
Columnar storage
OLAP
Compression
Distributed queries

Related:
PostgreSQL
Kafka
Analytics

Source:
Documentation
Project X
Conversation Y

Experience:
Beginner

Confidence:
0.55

```

After using it repeatedly:

```text
Experience:
Intermediate

Confidence:
0.82

```

Eventually:

```text
Experience:
Advanced

```

Do NOT automatically assume knowledge.

Track:

```text
FACT
EXPERIENCE
PREFERENCE
DECISION
ASSUMPTION
HYPOTHESIS
LESSON
UNKNOWN

```

---

# 9. Knowledge Provenance

Every memory must have provenance.

Never create mysterious knowledge.

Store:

```text
source_type
source_id
source_url
repository
file
line_start
line_end
commit
agent
session
timestamp
confidence

```

Example:

```text
"Redis is used as a distributed lock"

Source:
automorium_backend

File:
RedisLockService.java

Commit:
a8f29d1

Observed:
2026-08-17

```

This allows agents to verify memories.

---

# 10. Repository Intelligence

The Second Brain must understand repositories.

When a repository is connected:

```text
Repository
 ├── README
 ├── source code
 ├── configuration
 ├── Dockerfiles
 ├── compose files
 ├── Kubernetes manifests
 ├── Git history
 ├── CI/CD
 └── documentation

```

Index:

- files
- directories
- classes
- methods
- interfaces
- APIs
- dependencies
- database entities
- endpoints
- configuration
- environment variables
- Docker services
- Kubernetes resources
- tests
- Git history

Use AST parsing where possible.

Do not rely only on text chunking.

For Java use a Java parser/AST system.

Build relationships such as:

```text
Class -> imports -> Class

Controller -> calls -> Service

Service -> uses -> Repository

Repository -> accesses -> Entity

Entity -> maps_to -> DatabaseTable

```

---

# 11. Git Intelligence

Integrate deeply with Git.

Understand:

```text
repositories
branches
commits
authors
changed files
diffs
pull requests
tags
releases

```

Store semantic commit summaries.

Example:

```text
Commit:
a93b12

Summary:
"Refactored authentication token validation."

Files:
JwtFilter.java
SecurityConfig.java

Related:
authentication
JWT
security

Agent:
Claude Code

Project:
Automorium

```

---

# 12. Conversation Memory

The Second Brain should optionally ingest conversations from:

- Claude Code
- Codex
- local agents
- MCP agents
- terminal agents
- IDE agents

Store:

```text
conversation
session
messages
decisions
questions
solutions
code changes
tasks
errors

```

Do NOT blindly embed every message.

Extract structured memories first.

---

# 13. Intelligent Memory Extraction

After an agent session:

```text
Conversation
     ↓
Memory Extraction
     ↓
Classification
     ↓
Deduplication
     ↓
Confidence scoring
     ↓
Knowledge graph update
     ↓
Vector embedding
     ↓
Persistent memory

```

Extract:

```text
facts
decisions
preferences
lessons
tasks
errors
solutions
architecture
commands
technology knowledge
project knowledge
agent actions

```

---

# 14. Memory Deduplication

Avoid storing:

```text
PostgreSQL is used.
PostgreSQL is used.
PostgreSQL is used.

```

Instead merge memories:

```text
PostgreSQL
Used by:
- auth-service
- order-service
- billing-service

Evidence:
17 observations

```

Track memory strength:

```text
observation_count
last_seen
first_seen
confidence

```

---

# 15. Contradiction Detection

If the brain contains:

```text
"Redis is used only for caching."

```

and later:

```text
"Redis is used for distributed locking."

```

Do not silently overwrite.

Detect contradiction.

Create:

```text
Memory A
Memory B

Conflict:
Redis usage

Resolution:
Redis is used for both caching and distributed locks.

```

Agents should be informed about unresolved contradictions.

---

# 16. Memory Lifecycle

Implement:

```text
new
observed
confirmed
frequently_used
stable
deprecated
superseded
archived

```

Example:

```text
Spring Boot 3.4
     ↓
Spring Boot 3.5
     ↓
Spring Boot 4

```

The brain should preserve history instead of deleting old knowledge.

---

# 17. Personal Developer Preferences

Learn preferences from repeated evidence.

Examples:

```text
Prefers Java for backend services.

Prefers Spring Boot.

Prefers PostgreSQL.

Prefers Docker Compose for local development.

Prefers Kubernetes for production.

Prefers Qdrant for vector search.

Prefers Neo4j for knowledge graphs.

Prefers MCP-compatible agents.

```

But distinguish:

```text
Explicit preference
Observed behavior
Inferred preference

```

Never treat an inference as an explicit preference.

---

# 18. Project Isolation + Global Knowledge

Support hierarchical memory:

```text
GLOBAL
│
├── Developer Knowledge
├── Programming Knowledge
├── AI Knowledge
├── Infrastructure Knowledge
│
├── PROJECT A
│   ├── Repository A
│   └── Repository B
│
├── PROJECT B
│   ├── Backend
│   └── Frontend
│
└── PROJECT C

```

A project should inherit global knowledge.

Example:

Global:

```text
Developer prefers PostgreSQL.

```

Project:

```text
Project A uses PostgreSQL.

```

Repository:

```text
auth-service uses PostgreSQL.

```

---

# 19. Retrieval Architecture

Never use vector search alone.

Implement hybrid retrieval:

```text
User Query
    │
    ├── Semantic Search → Qdrant
    │
    ├── Graph Search → Neo4j
    │
    ├── Structured Search → PostgreSQL
    │
    ├── Hot Context → Redis
    │
    └── Artifact Search → MinIO metadata
             │
             ▼
       Retrieval Fusion
             │
             ▼
       Reranking
             │
             ▼
       Context Builder
             │
             ▼
          AI Agent

```

Use query classification.

Example:

```text
"What did Claude change yesterday?"

```

→ episodic/event retrieval.

```text
"How does authentication work?"

```

→ graph + code + semantic retrieval.

```text
"What is my preferred database?"

```

→ preference memory.

```text
"What happened in auth-service last week?"

```

→ Git + agent activity + project memory.

---

# 20. Context Optimization

The Second Brain must NOT dump thousands of memories into an agent.

Build an intelligent context compressor.

Given:

```text
Agent query

```

return:

```text
Relevant facts
Relevant relationships
Relevant recent events
Relevant code
Relevant decisions
Relevant previous agent sessions
Relevant unresolved tasks

```

Example:

```text
SECOND BRAIN CONTEXT

Project:
Automorium

Repository:
automorium_backend

Current task:
OAuth refresh token implementation

Relevant architecture:
auth-service handles authentication.

Relevant decisions:
Refresh tokens are persisted in PostgreSQL.

Previous agent:
Claude Code

Previous work:
RefreshToken entity created.

Remaining:
Controller + integration tests.

Known issue:
Token expiration policy undecided.

Related files:
...

```

This should be compact enough to fit inside an agent's context window.

---

# 21. MCP Server

Expose the entire brain through MCP.

Design MCP tools such as:

## Search

```text
brain_search

```

Search across all memory.

---

## Ask

```text
brain_ask

```

Ask a natural-language question against the brain.

---

## Project

```text
brain_projects
brain_project_context

```

---

## Repository

```text
brain_repository_context
brain_repository_search
brain_repository_history

```

---

## Memory

```text
brain_store_memory
brain_update_memory
brain_forget_memory
brain_memory_history

```

---

## Agent Sessions

```text
brain_start_session
brain_end_session
brain_get_session
brain_get_previous_sessions
brain_get_agent_handoff
brain_create_handoff

```

---

## Activity

```text
brain_record_event
brain_get_recent_activity
brain_get_file_history
brain_get_agent_activity

```

---

## Knowledge

```text
brain_knowledge
brain_related_knowledge
brain_knowledge_graph

```

---

## Decisions

```text
brain_record_decision
brain_get_decisions
brain_find_decision

```

---

## Tasks

```text
brain_create_task
brain_update_task
brain_get_open_tasks
brain_get_related_tasks

```

---

## Skills

```text
brain_list_skills
brain_get_skill
brain_match_skills

```

---

# 22. MCP Resources

Expose resources such as:

```text
brain://developer/profile
brain://project/{id}
brain://repository/{id}
brain://repository/{id}/architecture
brain://repository/{id}/recent-activity
brain://session/{id}
brain://handoff/{id}
brain://knowledge/{id}
brain://skills

```

---

# 23. Second Brain Skill

Create a special skill that every connected agent should be instructed to use.

Call it:

# `second-brain`

The skill teaches an agent:

```text
You have access to the developer's persistent Second Brain.

Before beginning substantial work:

1. Identify the project.
2. Identify the repository.
3. Query relevant architecture.
4. Query previous agent sessions.
5. Query unresolved tasks.
6. Query relevant decisions.
7. Query developer preferences.
8. Query relevant technical knowledge.

During work:

1. Record important discoveries.
2. Record architectural decisions.
3. Record significant file changes.
4. Record failed approaches.
5. Record successful solutions.
6. Record new technologies learned.
7. Update task progress.

Before ending:

1. Create a handoff.
2. Record completed work.
3. Record remaining work.
4. Record blockers.
5. Record important decisions.
6. Record changed files.
7. Record tests performed.
8. Record known issues.

```

The skill should teach agents **when NOT to query the brain** as well.

Avoid unnecessary retrieval for trivial tasks.

---

# 24. Automatic Agent Behavior

Ideally an agent should be able to start with:

```text
Use my Second Brain.

```

The agent automatically executes:

```text
brain_project_context
brain_get_agent_handoff
brain_get_recent_activity
brain_get_relevant_decisions
brain_get_repository_context

```

Then begins work.

---

# 25. Claude Code → Codex Continuity

This is a first-class requirement.

Example:

### Claude Code

```text
Session:
claude-123

Repository:
backend

Task:
Implement payment service.

Changes:
PaymentService.java
PaymentController.java

Decision:
Use Stripe API.

Remaining:
Webhook handling.

Problem:
Webhook signature verification failing.

```

Claude ends session.

Second Brain persists everything.

Then Codex starts.

Codex asks:

```text
brain_get_agent_handoff

```

and receives:

```text
Previous agent: Claude Code

Task:
Implement payment service.

Completed:
PaymentService
PaymentController

Decision:
Stripe API

Remaining:
Webhook handling

Known problem:
Signature verification failing.

Continue from here.

```

Codex should never need Claude's original context window.

---

# 26. Learning From Failed Attempts

Failures are valuable knowledge.

Store:

```text
Attempt
Problem
Approach
Result
Error
Resolution

```

Example:

```text
Attempt:
Use Redis Pub/Sub for event distribution.

Result:
Failed because events were lost during subscriber downtime.

Lesson:
Use RabbitMQ/Kafka when durable delivery is required.

```

Later an agent asks:

```text
"Should we use Redis Pub/Sub?"

```

The brain should answer:

```text
You previously tried Redis Pub/Sub for durable event distribution.

It failed because messages were lost when subscribers were unavailable.

Your later architecture uses RabbitMQ for this requirement.

```

---

# 27. Technical Knowledge Graph

Build a graph containing technologies and relationships.

Example:

```text
Spring Boot
   │
   ├── uses → Hibernate
   ├── integrates → Redis
   ├── integrates → RabbitMQ
   ├── integrates → PostgreSQL
   └── integrates → LangChain4j

```

Developer:

```text
Developer
   │
   ├── experienced_with → Spring Boot
   ├── learning → Neo4j
   ├── experimenting_with → Qwen
   └── prefers → PostgreSQL

```

---

# 28. Skill System

Skills must be dynamic.

A skill may contain:

```yaml
name: springboot-microservice-development
description: Spring Boot microservice architecture used by this developer
version: 3
confidence: 0.91

trigger:
  - spring boot
  - microservice
  - controller
  - service
  - repository

knowledge:
  - architecture conventions
  - package conventions
  - exception handling
  - DTO patterns
  - logging
  - testing
  - observability

```

Skills can be:

```text
global
project
repository
technology
task-specific
agent-specific

```

---

# 29. Skill Evolution

Skills should evolve.

If the developer repeatedly changes:

```text
Controller
Service
Repository
DTO
Mapper

```

the system should eventually identify the recurring architecture.

It can propose:

```text
I noticed your last 12 Spring Boot services follow a common architecture.

Would you like me to create a reusable skill?

```

Never silently create high-impact inferred preferences.

---

# 30. Screenshots and Visual Memory

Support visual knowledge.

Store:

- screenshots
- architecture diagrams
- UI references
- error screenshots
- terminal screenshots
- application screenshots

Store image metadata and embeddings where supported.

Example:

```text
Screenshot
 ↓
Project
 ↓
UI screen
 ↓
Component
 ↓
Repository

```

Use MinIO for the actual image.

---

# 31. Documents

Support:

```text
PDF
Markdown
TXT
DOCX
HTML
code
images
logs

```

Pipeline:

```text
Document
 ↓
Parser
 ↓
Metadata
 ↓
Chunks
 ↓
Embeddings
 ↓
Qdrant
 ↓
Entities
 ↓
Neo4j

```

---

# 32. Event-Driven Architecture

Prefer event-driven internal architecture.

Example:

```text
Agent
 ↓
MCP
 ↓
Memory Event
 ↓
Redis/Event Bus
 ├── PostgreSQL
 ├── Qdrant
 ├── Neo4j
 └── MinIO

```

Make indexing asynchronous.

Do not block an agent request while performing expensive indexing.

---

# 33. Memory Processing Pipeline

Implement workers:

```text
Ingestion Worker
Embedding Worker
Knowledge Extraction Worker
Graph Worker
Summarization Worker
Deduplication Worker
Conflict Detection Worker
Skill Evolution Worker
Git Worker
Repository Indexer
Document Worker

```

Each worker should be independently scalable.

---

# 34. API

Create REST APIs for administration and integrations.

Examples:

```text
POST /api/v1/memory
GET  /api/v1/memory/search

POST /api/v1/events
GET  /api/v1/events

POST /api/v1/sessions
GET  /api/v1/sessions/{id}

POST /api/v1/handoffs
GET  /api/v1/handoffs/latest

GET /api/v1/projects
GET /api/v1/repositories

GET /api/v1/knowledge
GET /api/v1/knowledge/graph

GET /api/v1/skills

```

MCP should be the primary agent interface.

REST should be for administration and integrations.

---

# 35. Web Dashboard

Build a React dashboard.

Views:

## Home

```text
Second Brain Health
Knowledge Count
Projects
Repositories
Recent Agent Activity
Recent Decisions
Open Tasks
Learning Progress

```

## Knowledge Graph

Interactive Neo4j visualization.

## Agent Activity

Timeline:

```text
Claude Code
   ↓
Codex
   ↓
Qwen
   ↓
Developer

```

## Repository Explorer

Show:

```text
repository
architecture
dependencies
services
agents
recent changes
knowledge

```

## Memory Explorer

Search and inspect memories.

## Skills

Show:

```text
skills
confidence
usage
version
evolution

```

## Handoffs

Show agent-to-agent continuity.

---

# 36. Security

Implement:

- authentication
- API keys
- MCP authentication
- project-level authorization
- repository-level authorization
- encryption where appropriate
- audit logs
- secret filtering
- PII filtering
- credential detection

Never store:

```text
passwords
API keys
private keys
tokens
.env secrets

```

If encountered, redact before persistence.

---

# 37. Privacy

The Second Brain is local/self-hostable first.

Everything should be designed to run on:

```text
Developer workstation
Home server
Private Kubernetes cluster
Private cloud

```

No mandatory cloud dependency.

---

# 38. Docker Compose

Provide a complete development environment:

```text
second-brain-api
second-brain-worker
postgres
qdrant
neo4j
redis
minio

```

Optional:

```text
grafana
prometheus

```

Provide:

```text
docker-compose.yml
.env.example

```

with sensible defaults.

---

# 39. Kubernetes

Also provide production-ready Kubernetes manifests/Helm charts.

Support:

```text
API
Workers
PostgreSQL
Qdrant
Neo4j
Redis
MinIO

```

Persistent volumes are mandatory for stateful services.

---

# 40. Observability

Use:

```text
OpenTelemetry
Prometheus
Grafana
structured JSON logs

```

Track:

```text
MCP requests
retrieval latency
embedding latency
Qdrant latency
Neo4j latency
memory ingestion rate
memory retrieval quality
agent sessions
events
errors
worker queue depth

```

---

# 41. Retrieval Quality

Implement evaluation.

Track:

```text
retrieval_precision
retrieval_recall
context_relevance
memory_hit_rate
false_memory_rate
stale_memory_rate

```

Create a test dataset of developer questions.

Example:

```text
"What database does auth-service use?"
"Why was Redis introduced?"
"What did Claude do yesterday?"
"What is the current OAuth implementation?"
"What did the previous agent leave unfinished?"

```

Measure whether the correct memory is retrieved.

---

# 42. Important Design Rule

Do not turn the Second Brain into:

> "A giant vector database containing everything."

Instead implement:

```text
Memory
+
Knowledge Graph
+
Structured Events
+
Code Intelligence
+
Git Intelligence
+
Agent Activity
+
Developer Preferences
+
Skills
+
Documents
+
Semantic Search

```

The combination is the actual brain.

---

# 43. Recommended Data Model

At minimum create entities:

```text
Developer
Project
Repository
Branch
Commit
File
CodeSymbol
Service
Technology
Database
Document
Memory
MemorySource
KnowledgeEntity
KnowledgeRelationship
Decision
Task
Agent
AgentSession
AgentEvent
AgentHandoff
Skill
SkillVersion
Conversation
Message
Artifact
Tag

```

---

# 44. Unified Memory ID

Every memory must have a globally unique ID.

Example:

```text
mem_01J...

```

Use this ID across:

```text
PostgreSQL
Qdrant
Neo4j
Redis
MinIO metadata

```

This allows cross-store traceability.

---

# 45. Memory Object

Design a canonical memory schema similar to:

```json
{
  "id": "mem_123",
  "type": "DECISION",
  "content": "Use PostgreSQL for persistent authentication data.",
  "scope": "project",
  "project_id": "automorium",
  "repository_id": "backend",
  "confidence": 0.94,
  "importance": 0.88,
  "source": {
    "type": "agent_session",
    "agent": "claude-code",
    "session_id": "session_123"
  },
  "created_at": "...",
  "updated_at": "...",
  "last_seen_at": "...",
  "observation_count": 8,
  "status": "stable",
  "tags": [
    "postgresql",
    "authentication",
    "architecture"
  ]
}

```

---

# 46. Context Assembly Algorithm

For every MCP query:

```text
1. Understand query intent.

2. Identify:
   project
   repository
   technology
   task
   agent

3. Search Qdrant.

4. Query Neo4j relationships.

5. Query recent PostgreSQL events.

6. Query Redis hot state.

7. Retrieve relevant artifacts.

8. Deduplicate.

9. Resolve contradictions.

10. Rank memories.

11. Compress context.

12. Return structured context.

```

---

# 47. Context Response Format

MCP should return structured information.

Example:

```json
{
  "project": "Automorium",
  "repository": "automorium_backend",

  "relevant_context": [],

  "architecture": [],

  "previous_agents": [],

  "recent_changes": [],

  "decisions": [],

  "open_tasks": [],

  "known_problems": [],

  "developer_preferences": [],

  "skills": [],

  "sources": []
}

```

---

# 48. Automatic Git Integration

When a repository is registered:

```text
git clone/index

```

Track:

```text
commit history
branches
diffs
file history
authors
semantic changes

```

When a commit occurs:

```text
Git Hook
 ↓
Second Brain
 ↓
Event
 ↓
Knowledge extraction

```

---

# 49. IDE / CLI Integration

Create:

```text
second-brain CLI

```

Examples:

```bash
brain search "authentication architecture"

brain ask "what did Claude change yesterday?"

brain handoff

brain remember "We use Redis for distributed locks"

brain project current

brain session start

brain session end

brain learn ./docs/new-technology.md

brain index .

brain status

```

---

# 50. Repository Bootstrap

Command:

```bash
brain init

```

should:

```text
detect Git repository
detect language
detect frameworks
detect databases
detect Docker
detect Kubernetes
detect package managers
detect CI/CD

```

Then create the initial knowledge graph.

---

# 51. Continuous Learning

Support:

```bash
brain watch .

```

Watch:

```text
file changes
git changes
build output
test output
logs
documentation

```

Do NOT index every keystroke.

Use intelligent batching/debouncing.

---

# 52. Agent-Aware Memory

Each agent should have:

```text
agent_id
agent_type
model
capabilities
sessions
actions
success_rate
specializations

```

Example:

```text
Claude Code
  specializes in:
  repository modification

Codex
  specializes in:
  coding/reasoning

Qwen
  specializes in:
  local/private inference

```

These are metadata, not hardcoded assumptions.

---

# 53. Agent Handoff Protocol

Define a standard handoff schema that every MCP-capable agent can use.

```text
Agent A
   ↓
create_handoff()
   ↓
Second Brain
   ↓
Agent B
   ↓
get_handoff()

```

This protocol must be agent-independent.

---

# 54. Self-Improvement

The Second Brain should analyze itself.

Periodically identify:

```text
unused memories
duplicate memories
contradictions
stale knowledge
frequently accessed knowledge
missing knowledge
new technologies
repeated patterns
potential skills

```

Generate recommendations:

```text
"You have used RabbitMQ in 5 projects.
Create a RabbitMQ architecture skill?"

"You repeatedly use this Spring Boot architecture.
Create a reusable project template?"

"Your knowledge about Neo4j is growing rapidly."

```

Require user approval for major automatic changes.

---

# 55. Brain Health

Create:

```text
brain doctor

```

It should detect:

```text
Qdrant unavailable
Neo4j unavailable
Redis unavailable
MinIO unavailable
PostgreSQL unavailable
embedding failures
orphaned vectors
orphaned graph nodes
duplicate memories
stale indexes
broken references

```

---

# 56. Development Phases

Build in phases.

## Phase 1

Foundation:

```text
Spring Boot API
PostgreSQL
Redis
Docker Compose
basic memory API

```

## Phase 2

Qdrant:

```text
embeddings
semantic search
hybrid retrieval

```

## Phase 3

Neo4j:

```text
knowledge graph
relationships
graph queries

```

## Phase 4

Repository Intelligence:

```text
Git
AST
code indexing
dependency graph

```

## Phase 5

MCP:

```text
MCP server
tools
resources
agent authentication

```

## Phase 6

Agent Memory:

```text
sessions
events
handoffs
Claude/Codex continuity

```

## Phase 7

Skills:

```text
skill extraction
skill matching
skill evolution

```

## Phase 8

Documents:

```text
MinIO
PDF
Markdown
DOCX
images
logs

```

## Phase 9

Dashboard:

```text
React
knowledge graph
memory explorer
agent timeline
skills
projects

```

## Phase 10

Self-improvement:

```text
deduplication
contradiction detection
memory decay
knowledge evolution
skill evolution

```

---

# 57. Testing

Create tests for:

### Memory

```text
store
retrieve
update
deduplicate
expire
supersede

```

### Graph

```text
create relationship
traverse relationship
find dependencies
find related technology

```

### Agent continuity

Test:

```text
Claude session
 ↓
handoff
 ↓
Codex session

```

Verify Codex receives Claude's work.

### Retrieval

Test:

```text
semantic
keyword
graph
hybrid
temporal
project scoped
repository scoped

```

### Security

Test secret redaction.

---

# 58. Final UX

The ultimate user experience should be:

```text
Developer:
"Codex, implement the payment webhook."

Codex:
[queries Second Brain]

Second Brain:
"You have an unfinished payment implementation from Claude Code.
Claude already implemented PaymentService and PaymentController.
Webhook handling remains incomplete.
Previous attempt failed because Stripe signature validation used
the parsed request body instead of the raw payload."

Codex:
"Understood. I'll continue from that state."

```

No manual context transfer.

No copy/paste.

No repeating previous work.

No dependency on one AI provider.

---

# 59. Golden Rule

The Second Brain must remember:

> What I know.

> What I have built.

> What I tried.

> What failed.

> What worked.

> Why I made decisions.

> What my agents did.

> What previous agents left unfinished.

> What I am currently working on.

> What I am learning.

> How my projects are connected.

> How my engineering practices evolve.

And it must continuously become better as I use it.

---

# 60. Deliverables

Produce a complete implementation with:

```text
second-brain/
├── backend/
├── workers/
├── mcp-server/
├── cli/
├── dashboard/
├── ingestion/
├── repository-indexer/
├── skill-engine/
├── docker/
├── kubernetes/
├── docs/
├── examples/
└── tests/

```

Include:

```text
README.md
ARCHITECTURE.md
DEVELOPMENT.md
MCP.md
MEMORY_MODEL.md
KNOWLEDGE_GRAPH.md
SKILLS.md
AGENT_HANDOFF.md
SECURITY.md
DEPLOYMENT.md
docker-compose.yml
.env.example

```

Provide:

1. Architecture diagrams.
2. Database schemas.
3. Neo4j graph model.
4. Qdrant collection design.
5. MCP tool definitions.
6. MCP resource definitions.
7. Memory lifecycle.
8. Agent handoff protocol.
9. Skill protocol.
10. REST API specification.
11. Docker Compose deployment.
12. Kubernetes deployment.
13. CLI.
14. Dashboard.
15. Unit tests.
16. Integration tests.
17. End-to-end tests.
18. Example Claude Code integration.
19. Example Codex integration.
20. Example generic MCP agent integration.

## Critical Requirement

Do not build a generic "chatbot with RAG".

Build a **persistent, structured, evolving external developer brain**.

The brain is the long-term system of record.

Agents are temporary clients.

Claude Code may disappear tomorrow.

Codex may be replaced next year.

Qwen may be replaced by another model.

The Second Brain must remain intact and allow every future AI agent to continue exactly where the previous agent stopped.