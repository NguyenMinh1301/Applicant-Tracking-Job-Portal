# Architecture Decision Record: 002 - Infrastructure Selection

## Context and Problem Statement

The system requires robust persistence, asynchronous communication, and low-latency caching capabilities.

## Decision

Select PostgreSQL, Kafka, and Redis as the core infrastructure components.

## Decision Date

Inferred from git history:

- PostgreSQL & Redis: Core persistence adopted at project inception.
- Kafka: Adopted via commit `a447bd3` for event-driven workflows.
- pgvector: Adopted via commit `35420b2` for AI vector embeddings.

## Justification

1.  **PostgreSQL (with pgvector):** Provides ACID compliance and relational integrity. `pgvector` enables vector similarity search for AI-driven candidate matching directly within the database. JSONB supports unstructured data storage.
2.  **Kafka:** Decouples modules via asynchronous messaging. Essential for system-wide event broadcasting (e.g., Job Published events) without synchronous blocking.
3.  **Redis:** Delivers sub-millisecond response times for caching, rate limiting (Resilience4j), and volatile session state.

## Consequences

- Requires Docker Compose (`infra/database/docker-compose.yml`) for local environment parity.
- Increases baseline infrastructure resource requirements.
