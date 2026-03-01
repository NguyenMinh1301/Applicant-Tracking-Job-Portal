# Architecture Decision Record: 001 - Adopt Modular Monolith

## Context and Problem Statement

The system requires a scalable architecture that balances domain complexity with team velocity.

## Decision

Adopt a Modular Monolith architecture instead of Microservices.

## Justification

1.  **Domain Cohesion:** Features (Job, Subscription) operate within distinct Bounded Contexts but share the same deployment unit, avoiding distributed transaction overhead.
2.  **Operational Simplicity:** Deploying a single artifact reduces infrastructure complexity during early project phases.
3.  **Future-Proofing:** Code is organized by domain (`src/main/java/com/vietrecruit/feature/*`). If scaling requires it, bounded contexts can easily be extracted into independent microservices.

## Consequences

- Enforces strict module boundaries at the package level.
- Requires disciplined dependency management to prevent tight coupling between features.
