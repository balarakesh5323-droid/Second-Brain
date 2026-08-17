# Memory Model

## Overview

Every memory in the Second Brain follows a canonical schema with lifecycle management, provenance tracking, and multi-store indexing.

## Memory Schema

```json
{
  "id": "uuid",
  "content": "Use PostgreSQL for persistent authentication data",
  "type": "DECLARATIVE",
  "scope": "PROJECT",
  "status": "CONFIRMED",
  "confidence": 0.94,
  "importance": 0.88,
  "observationCount": 8,
  "project_id": "uuid",
  "repository_id": "uuid",
  "tags": ["postgresql", "authentication", "architecture"],
  "source": {
    "type": "agent_session",
    "agent": "claude-code",
    "session_id": "uuid",
    "file": "src/main/java/AuthService.java",
    "commit": "abc123"
  },
  "first_seen_at": "2026-01-15T10:00:00Z",
  "last_seen_at": "2026-08-17T14:30:00Z"
}
```

## Memory Types

| Type | Description |
|------|-------------|
| DECLARATIVE | Facts and knowledge ("We use PostgreSQL") |
| PROCEDURAL | How-to knowledge ("How to deploy") |
| EPISODIC | Event-based ("What happened yesterday") |
| SEMANTIC | Conceptual relationships ("X depends on Y") |
| EPILOGICAL | Reasoning and logic ("Why we chose X") |

## Memory Scope

| Scope | Description |
|-------|-------------|
| GLOBAL | Applies everywhere |
| PROJECT | Scoped to a specific project |
| REPOSITORY | Scoped to a specific repository |

## Memory Lifecycle

```
NEW → OBSERVED → CONFIRMED → FREQUENTLY_USED → STABLE
                  ↓              ↓                ↓
              DEPRECATED ← ← ← ←                ↓
                  ↓                              ↓
              ARCHIVED ← ← ← ← ← ← ← ← ← ← ←

SUPERSEDED (when merged with another memory)
```

### Status Transitions
- **NEW**: Just created, low observation count
- **OBSERVED**: Observed 3+ times
- **CONFIRMED**: Frequently observed and recently accessed
- **FREQUENTLY_USED**: Very high observation count with recent access
- **STABLE**: Mature, reliable knowledge
- **DEPRECATED**: Low confidence, not accessed recently
- **ARCHIVED**: Very old, low confidence, low observation count
- **SUPERSEDED**: Merged into another memory (deduplication)

## Memory Decay

Memories that are not accessed decay over time:
- **90 days** without access: confidence reduced by 0.1 per cycle
- **Confidence < 0.3**: Marked as DEPRECATED
- **365 days** since last access + DEPRECATED + observation count ≤ 2 + confidence < 0.2: ARCHIVED

## Deduplication

Near-duplicate memories are detected using Jaccard token similarity:
- **Threshold**: 0.85 token overlap
- **Action**: Merge tags, boost observation count, mark duplicate as SUPERSEDED

## Contradiction Detection

Memories with opposing conclusions are flagged:
- Signal word pairs: use/avoid, should/should not, postgresql/mysql, etc.
- Requires shared context (30%+ token overlap)
- Lower-confidence memory marked as DEPRECATED

## Multi-Store Indexing

Every memory is indexed in multiple stores:
- **PostgreSQL**: Structured metadata, queries
- **Qdrant**: Vector embedding for semantic search
- **Neo4j**: Knowledge graph relationships
- **Redis**: Hot memory for fast access
