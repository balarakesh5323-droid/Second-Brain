# Security

## Overview

Second Brain is designed for self-hosted deployment. All data stays on your infrastructure.

## Authentication

### API Key (Default)
```bash
curl -H "X-API-Key: your-api-key" http://localhost:8080/api/v1/memory
```

### OAuth2/JWT (Optional)
Configure in `application.yml`:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://your-auth-server.com
```

## Content Redaction

The system automatically redacts sensitive patterns:
- API keys and tokens
- Passwords and secrets
- Private keys
- Connection strings with credentials

## Network Security

- CORS configured per environment
- Actuator endpoints restricted
- Docker network isolation between services
- No external data transmission

## Data Privacy

- **Self-hosted**: All data on your infrastructure
- **No telemetry**: No data sent to external services
- **Local embeddings**: Embedding generation is local (placeholder, upgradeable)
- **Encrypted storage**: PostgreSQL and Redis support encryption at rest
