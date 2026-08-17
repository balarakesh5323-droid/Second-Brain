# Skills

## Overview

Skills are reusable knowledge patterns extracted from agent sessions and project experience. They represent proven approaches to common tasks.

## Skill Schema

```json
{
  "id": "uuid",
  "name": "Spring Boot REST API",
  "description": "Creating REST APIs with Spring Boot",
  "version": "1",
  "confidence": 0.85,
  "triggers": ["spring", "rest", "api", "controller"],
  "knowledge": ["spring-boot", "rest-api", "jpa"],
  "scope": "global",
  "usageCount": 12,
  "last_used_at": "2026-08-17T10:00:00Z"
}
```

## Skill Lifecycle

1. **Creation**: Skills are created manually or extracted from patterns
2. **Triggering**: When a query matches skill triggers, the skill is suggested
3. **Usage**: Skills are used and their usage count increases
4. **Evolution**: Weekly worker updates confidence and expands triggers

## Skill Evolution Worker

Runs weekly and:
- Boosts confidence for frequently used skills (usageCount ≥ 3)
- Decays confidence for unused skills
- Expands triggers based on recent memory keywords
- Only adds triggers related to existing ones

## MCP Tools

- `brain_create_task` — Can reference skills
- Knowledge graph tracks skill usage and relationships
