# Agent Handoff

## Overview

The handoff protocol enables seamless context transfer between AI agents. When one agent finishes work, it creates a handoff document that the next agent can retrieve to continue exactly where the previous agent stopped.

## Handoff Schema

```json
{
  "id": "uuid",
  "session_id": "uuid",
  "agent_id": "uuid",
  "repository_id": "uuid",
  "project_id": "uuid",
  "task": "Implement payment webhook",
  "completed_items": "Created PaymentService\nImplemented PaymentController",
  "in_progress_items": "Webhook signature validation",
  "blocked_items": "Waiting for Stripe API credentials",
  "changed_files": "src/PaymentService.java\nsrc/PaymentController.java",
  "next_steps": "Add unit tests\nDeploy to staging",
  "decisions": "Use Stripe for payment processing",
  "known_issues": "Signature validation using parsed body instead of raw payload"
}
```

## Protocol

```
Agent A (e.g., Claude Code)
    ↓
brain_start_session(agent_name="claude-code", task="...")
    ↓
[Agent works, records events, stores memories]
    ↓
brain_create_handoff(session_id, task, completed, next_steps, ...)
    ↓
brain_end_session(session_id, summary)
    ↓
Second Brain stores handoff
    ↓
Agent B (e.g., Codex)
    ↓
brain_get_handoff(repository_id)
    ↓
[Agent B receives full context of Agent A's work]
    ↓
Agent B continues from exact state
```

## MCP Tools

| Tool | Direction | Purpose |
|------|-----------|---------|
| `brain_create_handoff` | Agent → Brain | Store handoff document |
| `brain_get_handoff` | Brain → Agent | Retrieve latest handoff |

## REST API

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/handoffs` | POST | Create handoff |
| `/api/v1/handoffs/repository/{id}/latest` | GET | Get latest for repo |
| `/api/v1/handoffs/session/{id}` | GET | Get by session |

## Design Principles

- **Agent-independent**: Any MCP-capable agent can use the protocol
- **Repository-scoped**: Handoffs are tied to repositories
- **Accumulative**: Each handoff captures the full state
- **Structured**: Fixed schema ensures consistency
