# System Overview

## Containter Level Architecture

This diagram illustrates the core containers defined in the `infra/` directory and their interactions with the application.

```mermaid
C4Container
    title Container Diagram for VietRecruit

    Person(employer, "Employer", "Posts jobs and manages subscriptions.")
    Person(candidate, "Candidate", "Searches and applies for jobs.")

    System_Boundary(vr_system, "VietRecruit System") {
        Container(app, "Spring Boot Application", "Java 21, Spring Boot 3.4.2", "Handles domain logic, APIs, and business rules.")
        ContainerDb(postgres, "PostgreSQL + pgvector", "PostgreSQL 16", "Stores relational data and AI vector embeddings.")
        ContainerDb(redis, "Redis", "Redis 7 Alpine", "Handles distributed caching, session state, and rate limit counters.")
    }

    Rel(employer, app, "Uses", "HTTPS/REST")
    Rel(candidate, app, "Uses", "HTTPS/REST")

    Rel(app, postgres, "Reads/Writes", "JDBC")
    Rel(app, redis, "Reads/Writes", "RESP")
```

## Infrastructure Components

- **vr-postgres:** Primary relational database holding domain entities.
- **vr-redis:** Primary ephemeral store for circuit breakers, rate limiters, and system performance optimizations.
