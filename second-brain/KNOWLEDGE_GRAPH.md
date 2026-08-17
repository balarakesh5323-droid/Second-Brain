# Knowledge Graph

## Overview

The knowledge graph in Neo4j captures relationships between projects, repositories, technologies, agents, memories, and decisions.

## Node Types

| Label | Properties |
|-------|-----------|
| Project | name, description, path |
| Repository | name, url, path, defaultBranch, primaryLanguage |
| Technology | name, category, version, description |
| Agent | name, type, model, capabilities |
| Memory | content, type, scope, status, confidence |
| Decision | title, description, rationale, status |
| Skill | name, description, version, confidence |

## Relationship Types

| Relationship | From → To | Description |
|-------------|-----------|-------------|
| BELONGS_TO | Repository → Project | Repository belongs to project |
| USES | Project → Technology | Project uses technology |
| ABOUT_PROJECT | Memory → Project | Memory is about a project |
| ABOUT_REPOSITORY | Memory → Repository | Memory is about a repository |
| CREATED_BY | Decision → Agent | Decision made by agent |
| WORKED_ON | Agent → Repository | Agent worked on repository |

## Querying

### Find Related Nodes
```cypher
MATCH (n:Project {id: $id})-[r*1..2]-(m)
RETURN DISTINCT m, length(r) as depth
ORDER BY depth LIMIT 50
```

### Shortest Path
```cypher
MATCH path = shortestPath(
  (a:Technology {id: $fromId})-[*..6]-(b:Technology {id: $toId})
)
RETURN [n IN nodes(path) | n] as nodes,
       [r IN relationships(path) | type(r)] as relationships
```

### Search by Property
```cypher
MATCH (n:Technology)
WHERE n.name CONTAINS $value
RETURN n LIMIT $limit
```

## Sync from PostgreSQL

The `GraphSyncService` synchronizes PostgreSQL entities to Neo4j:
- Projects → Project nodes
- Repositories → Repository nodes + BELONGS_TO relationships
- Agents → Agent nodes
- Technologies → Technology nodes
- Memories → Memory nodes + ABOUT_PROJECT/ABOUT_REPOSITORY relationships
