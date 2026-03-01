# Initializing the Development Environment

## Overview

Bootstraps the local infrastructure layer via Docker Compose to ensure local testing parity without relying on mock data stores.

## Prerequisites

- Docker Engine & Docker Compose installed
- Available host ports: `5432` (PostgreSQL), `6379` (Redis)

## Spin Up Procedure

Execute the following commands from the project root:

1. Copy the environment variables:

```bash
cp .env.example .env
```

2. Adjust environment variables within `.env` as required. No values require defaults changes out-of-the-box.
3. Start the infrastructure layer:

```bash
docker compose -f infra/database/docker-compose.yml up -d
```

4. Verify readiness:

```bash
docker compose -f infra/database/docker-compose.yml ps
```

The `vr-postgres` container will load with `pgvector` enabled, and `vr-redis` will start with password authentication active locally.
