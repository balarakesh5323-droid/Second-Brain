# Second Brain — Agent Skill Guide

You are an AI agent connected to a **Second Brain** — a persistent, structured knowledge base that survives across sessions and agents. Use it to remember everything, never repeat work, and always know what previous agents did.

## Quick Start

At the start of every session, run these tools in order:

```
1. brain_get_continuity_state → Instant 1-shot snapshot of uncommitted diffs, previous agent state, and active tasks
2. brain_get_attempts         → Review previous trials, failed approaches, and lessons learned (DO NOT repeat errors)
3. brain_get_context          → Load Graph-RAG context, AST call chains, and architectural decisions
4. brain_get_open_tasks       → See pending prioritized tasks
```

During your active work:

```
• When a strategy or test fails  → brain_record_attempt (status="FAILURE", errorMessage="...", lessonLearned="...")
• When an approach succeeds      → brain_record_attempt (status="SUCCESS", ...)
• When finding symbol definition → brain_search (collection="code_symbols", query="...")
• When checking architecture     → brain_search (collection="documentation", query="...")
```

At the end of every session, run:

```
1. brain_create_handoff   → Pass your work to the next agent (Claude Code ↔ Codex ↔ Cursor)
2. brain_record_decision  → Record any architectural decisions made
3. brain_end_session      → Close your session
```

---

## MCP Tools Reference

### Session Management

#### `brain_start_session`
Start a session. Always do this first.
```
Parameters:
  agent_name (required): Your name, e.g. "claude-code", "codex", "cursor"
  task (required):       What you're working on
  repository_id:         UUID of the repository
  project_id:            UUID of the project
```

#### `brain_end_session`
End your session with a summary of what you did.
```
Parameters:
  session_id (required): Your session UUID
  summary (required):    What you accomplished
```

---

### Context & Search

#### `brain_get_context` ⭐ MOST IMPORTANT
Assemble full context for any query. Searches memories, graph, events, decisions, tasks, and handoffs. Returns structured JSON.
```
Parameters:
  query (required):     Natural language question or topic
  project_id:           Scope to a project
  repository_id:        Scope to a repository
```
Returns: `relevant_context`, `architecture`, `decisions`, `open_tasks`, `recent_changes`, `known_problems`

#### `brain_search`
Search memories by keyword.
```
Parameters:
  query (required):  Search term
  collection:        Specific collection to search
  limit:             Max results (default 10)
```

#### `brain_ask`
Ask a natural language question. Same as search but semantically richer.
```
Parameters:
  question (required): Your question in plain English
```

---

### Memory

#### `brain_store_memory`
Store something you learned. Be specific.
```
Parameters:
  content (required):   What you learned
  type (required):      DECLARATIVE | PROCEDURAL | EPISODIC | SEMANTIC | EPILOGICAL
  scope:                GLOBAL | PROJECT | REPOSITORY
  project_id:           Scope to a project
  repository_id:        Scope to a repository
  tags:                 Array of tags
```

**When to store memories:**
- You discovered how something works → `DECLARATIVE`
- You learned how to do something → `PROCEDURAL`
- Something happened that matters → `EPISODIC`
- You found a relationship between things → `SEMANTIC`
- You made or understood a decision → `EPILOGICAL`

**Be specific.** Bad: "Docker is used." Good: "Docker Compose orchestrates 7 services: api, dashboard, postgres, redis, qdrant, neo4j, minio. API runs on port 8080."

---

### Agent Handoff

#### `brain_create_handoff`
Create a handoff document for the next agent. **Always do this before ending your session.**
```
Parameters:
  session_id (required):     Your session UUID
  task (required):           What you were working on
  completed_items:           What you finished
  in_progress_items:         What's partially done
  blocked_items:             What you couldn't finish and why
  changed_files:             Files you modified
  next_steps:                What the next agent should do
  decisions:                 Architectural decisions you made
  known_issues:              Bugs or problems you discovered
```

#### `brain_get_handoff`
Get the latest handoff for a repository. **Always do this at the start of every session.**
```
Parameters:
  repository_id (required): Repository UUID
```

---

### Knowledge Graph

#### `brain_knowledge_graph`
Query the Neo4j knowledge graph.
```
Parameters:
  label (required):  Node type: Project, Repository, Technology, Agent, Memory
  id:                Specific node ID (omit to list all of that label)
  depth:             Traversal depth (default 2)
```

#### `brain_projects`
List all projects.

---

### Tasks & Decisions

#### `brain_create_task`
Create a task.
```
Parameters:
  title (required):     Task title
  description:          Details
  project_id:           Project UUID
  repository_id:        Repository UUID
  priority:             1 (highest) to 5 (lowest)
```

#### `brain_get_open_tasks`
List all open tasks, optionally filtered by project.
```
Parameters:
  project_id:  Filter by project
```

#### `brain_record_decision`
Record an architectural decision with rationale. **Always record decisions.**
```
Parameters:
  title (required):      Decision title
  description (required): What was decided
  rationale:             Why this decision
  project_id:            Project UUID
  repository_id:         Repository UUID
  tags:                  Array of tags
```

---

### Events & Activity

#### `brain_record_event`
Record something that happened during your session.
```
Parameters:
  session_id (required): Your session UUID
  event_type (required): CODE_CHANGE | BUG_FIX | FEATURE | REFACTOR | TEST | DOCUMENTATION | DEPLOYMENT | DISCOVERY | ERROR | DECISION
  description (required): What happened
  file_path:             Related file
  status:                success | failure | partial
```

#### `brain_get_recent_activity`
See what other agents did recently.
```
Parameters:
  limit:  Max results (default 20)
```

---

### System

#### `brain_doctor`
Check health of all services. Run if something seems wrong.
```
Parameters: (none)
```

#### `brain_evaluate_quality`
Run retrieval quality evaluation against test dataset.
```
Parameters: (none)
```

---

## Decision Rules

### When to store a memory
- You figured out how something works → **store it**
- You made a decision → **store it + record_decision**
- You found a bug → **store it** (so the next agent knows)
- You learned a preference → **store it**
- You're about to repeat something you already did → **search first, don't store duplicates**

### When NOT to store a memory
- It's obvious/trivial ("the server runs on port 8080" — already in config)
- It's temporary ("I'm currently fixing a bug" — use events instead)
- It's a duplicate of something already stored — **search first**

### When to record a decision
- Choosing between technologies (PostgreSQL vs MySQL)
- Architectural patterns (monolith vs microservices)
- API design choices (REST vs GraphQL)
- Trade-offs (performance vs simplicity)

### When to create a handoff
- **Always.** Every session ends with a handoff.
- Be specific about what's done vs what's not
- Include file paths of changes
- Mention any blockers with reasons

### When to search before acting
- Before implementing a feature that might already exist
- Before choosing a technology (check if there's a decision about it)
- Before fixing a bug (check if someone already found the cause)
- Before starting any significant work

---

## Memory Types Guide

| Type | Use When | Example |
|------|----------|---------|
| `DECLARATIVE` | Facts about the system | "Auth-service uses PostgreSQL for user data" |
| `PROCEDURAL` | How to do something | "Deploy by running: docker-compose up -d" |
| `EPISODIC` | Events that happened | "Claude broke the build on 2026-08-15, fixed by reverting commit abc123" |
| `SEMANTIC` | Relationships | "PaymentService depends on StripeService and WebhookHandler" |
| `EPILOGICAL` | Reasoning/decisions | "We chose PostgreSQL over MySQL because of JSONB support" |

---

## Example Session

### Starting (picking up from previous agent)

```
1. brain_start_session(agent_name="codex", task="Implement payment webhook")
2. brain_get_handoff(repository_id="abc-123")
   → Receives: "Claude implemented PaymentService and PaymentController.
     Webhook handling remains incomplete. Previous attempt failed because
     Stripe signature validation used parsed body instead of raw payload."
3. brain_get_context(query="payment webhook implementation", project_id="xyz")
   → Receives: Relevant memories, decisions, recent changes
4. brain_get_open_tasks(project_id="xyz")
   → Receives: "Implement webhook handler" (priority: 1)
```

### During work

```
5. brain_record_event(event_type="CODE_CHANGE", description="Implemented WebhookController with raw payload validation")
6. brain_record_event(event_type="BUG_FIX", description="Fixed Stripe signature validation to use raw body")
7. brain_store_memory(content="Stripe webhook signature validation requires raw request body, not parsed JSON", type="PROCEDURAL", tags=["stripe", "webhook", "gotcha"])
8. brain_record_decision(title="Use raw body for Stripe webhooks", description="Always pass raw HttpServletRequest body to Stripe SDK for signature validation", rationale="Parsed JSON changes the byte sequence and breaks HMAC validation")
```

### Ending (handoff to next agent)

```
9. brain_create_handoff(
     session_id="...",
     task="Implement payment webhook",
     completed_items="WebhookController implemented, Stripe signature validation fixed, unit tests passing",
     in_progress_items="Integration test with Stripe test mode",
     blocked_items="",
     changed_files="src/WebhookController.java, src/StripeService.java, src/test/WebhookControllerTest.java",
     next_steps="Run integration test with Stripe test webhook, deploy to staging",
     decisions="Use raw body for Stripe signature validation",
     known_issues="None"
   )
10. brain_end_session(session_id="...", summary="Completed payment webhook implementation. All tests passing.")
```

---

## Common Patterns

### Before writing any code
```
brain_get_context(query="what I'm about to implement")
brain_search(query="similar feature")
brain_get_open_tasks()
```

### When confused about architecture
```
brain_knowledge_graph(label="Technology")
brain_record_decision(title="...", description="...", rationale="...")
```

### When finding a bug
```
brain_store_memory(content="Bug: ...", type="EPISODIC", tags=["bug"])
brain_record_event(event_type="BUG_FIX", description="...")
```

### When learning a gotcha
```
brain_store_memory(content="Gotcha: ...", type="PROCEDURAL", tags=["gotcha", "important"])
```

---

## Scope Rules

- **GLOBAL**: Applies to all projects (e.g., "Always use Lombok for DTOs")
- **PROJECT**: Scoped to one project (e.g., "Automorium uses Stripe for payments")
- **REPOSITORY**: Scoped to one repo (e.g., "automorium_backend uses Java 21")

When storing, pick the narrowest scope that applies. Don't pollute global with project-specific knowledge.
