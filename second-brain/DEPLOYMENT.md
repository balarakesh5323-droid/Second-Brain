# Deployment

## Docker Compose (Recommended)

### Quick Start
```bash
cd second-brain
docker-compose up -d
```

### Services
| Service | Port | Purpose |
|---------|------|---------|
| api | 8080 | REST API + MCP Server |
| dashboard | 3000 | Web UI |
| postgres | 5432 | Database |
| redis | 6379 | Cache |
| qdrant | 6333/6334 | Vector store |
| neo4j | 7474 | Knowledge graph |
| minio | 9000/9001 | Document storage |

### Health Check
```bash
curl http://localhost:8080/api/v1/health/doctor
```

## CLI

### Build
```bash
./gradlew :cli:bootJar
```

### Run
```bash
java -jar cli/build/libs/brain-cli.jar search "authentication"
java -jar cli/build/libs/brain-cli.jar status
java -jar cli/build/libs/brain-cli.jar remember "Use Redis for caching"
```

### Available Commands
| Command | Description |
|---------|-------------|
| `brain search <query>` | Search memories |
| `brain ask <question>` | Ask natural language question |
| `brain remember <content>` | Store a memory |
| `brain projects` | List projects |
| `brain tasks` | List open tasks |
| `brain decisions` | List recent decisions |
| `brain context <query>` | Assemble context |
| `brain status` | Health check |
| `brain handoff <repo-id>` | Get latest handoff |

## Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/secondbrain
SPRING_DATASOURCE_USERNAME=secondbrain
SPRING_DATASOURCE_PASSWORD=secret

# Redis
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

# Qdrant
QDRANT_HOST=localhost
QDRANT_GRPC_PORT=6334

# Neo4j
SPRING_NEO4J_URI=bolt://localhost:7687
SPRING_NEO4J_USERNAME=neo4j
SPRING_NEO4J_PASSWORD=password

# MinIO
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
```

## Production Considerations

- Use PostgreSQL instead of H2
- Enable SSL for all connections
- Set strong passwords for all services
- Configure backup for PostgreSQL and MinIO
- Monitor with Prometheus/Grafana (planned)
- Use Kubernetes for scaling (manifests available)
