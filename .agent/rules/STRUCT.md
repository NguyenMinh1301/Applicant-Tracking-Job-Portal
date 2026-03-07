---
trigger: model_decision
description: I only turn it on when I'm active or working on a VietRecruit or VietRecruit-BE project.
---

# Project Structure and Architecture

This document provides a comprehensive overview of the `VietRecruit` project structure, key technical components, and defined architectural patterns. Use this as a map to understand how the codebase and infrastructure are organized.

## 1. Top-Level Directory Structure

```text
VietRecruit/
├── .agent/                     # AI Agent configuration, skills, and rules
├── infra/                      # Legacy/alternative infrastructure configs
├── elk/                        # ELK Stack configuration (Elasticsearch, Logstash, Kibana)
├── debezium/                   # Debezium configurations for CDC (Change Data Capture)
├── monitor/                    # Prometheus monitoring configurations
├── grafana-storage/            # Persistent storage volume for Grafana
├── database/                   # Database initialization scripts (e.g. init-script.sql)
├── docs/                       # Project Documentation (Architecture, APIs, rules)
├── postman/                    # API Collections for testing
│   └── vietrecruit-api-collection.json
├── src/                        # Main Application Source Code (Java/Spring Boot)
│   ├── main/
│   │   ├── java/com/vietrecruit/
│   │   │   ├── common/         # Shared utilities and base classes
│   │   │   ├── feature/        # Feature modules (Domain-Driven Design)
│   │   │   └── Application.java# Spring Boot Entry point
│   │   └── resources/
│   │       ├── db/migration/   # Flyway SQL migrations
│   │       ├── resilience/     # Resilience4j YAML configurations
│   │       ├── templates/email # Freemarker/Thymeleaf email templates
│   │       ├── application.yaml# Main application properties
│   │       ├── application.dev.yaml  # Dev profile configuration
│   │       ├── application.prod.yaml # Prod profile configuration
│   │       └── logback-spring.xml    # ELK / Logging configuration
│   └── test/                   # Unit and Integration test suites
├── .env                        # Local development environment variables
├── .env.example                # Template for environment variables
├── .env.prod                   # Production environment variables
├── docker-compose.yml          # Local development infrastructure setup
├── docker-compose.prod.yml     # Production infrastructure deployment setup
├── Dockerfile                  # Production application container image definition
├── Jenkinsfile                 # Jenkins CI/CD pipeline definition
├── Makefile                    # Make commands for quick developer actions
└── pom.xml                     # Maven dependencies and build configuration
```

## 2. Source Code Architecture (`src/main/java`)

The project follows a **Modular Monolith** approach with a **Layered Architecture** inside each vertical feature module.

### A. The `common` Module
This acts as the cross-cutting concern layer used by all features.
- `base`: Contains abstract `BaseController` and `BaseEntity` ensuring uniform audit fields (createdAt, updatedAt) and resilience handling.
- `config`: Global configurations for Spring Security, Kafka, Redis, and Swagger/OpenAPI.
- `exception`: Contains `GlobalExceptionHandler` to translate all exceptions into a standard `ApiException` and uniform `ApiResponse`.
- `init`: Application initializers (e.g., seeding admin users on first startup).
- `response`: Standardized API wrappers (`ApiResponse`, `PageResponse`) to ensure all endpoints respond consistently.
- `security`: JWT filters, Authentication managers, and OAuth2 setups (Google, GitHub).

### B. The `feature` Modules (Domain-Driven Design)
The domain is split into distinct business capabilities. Examples include `job`, `payment`, `subscription`, and `auth`. Each feature is strictly encapsulated:
- `controller/`: REST API endpoints. Extends `BaseController` for common resilience patterns and standardized error handling.
- `service/`: Interfaces and `impl/` implementations for pure business logic.
- `repository/`: Spring Data JPA interfaces representing the data access layer.
- `entity/`: JPA annotated domain models mirroring the relational schema.
- `mapper/`: MapStruct interfaces to seamlessly convert between Entities, internal Models, and DTOs.
- `dto/`: Request/Response objects used exclusively in the controller layer.

## 3. Infrastructure & Observability

- **Database Initialization:** Flyway is used to manage schema versions. The aggregate output of all Flyway migrations is generated into `database/init-script.sql` for easy Docker volume initialization.
- **Docker Compose:**
  - `docker-compose.yml`: Spins up the local development dependencies (PostgreSQL + pgvector, Redis, Zookeeper, Kafka, Maildev).
  - `docker-compose.prod.yml`: Expands to full production infrastructure including the ELK stack, Prometheus, Grafana, and the app service itself.
- **Observability Stack:** 
  - **Logging**: Logback streams logs into Logstash (via TCP/UDP), which are stored in Elasticsearch and visualized in Kibana (`elk/` directory).
  - **Metrics**: Spring Boot Actuator exposes metrics to Prometheus (`monitor/`). Grafana consumes these metrics for dashboards (`grafana-storage/`).
- **Resilience:** Integrates **Resilience4j** mapped via YAML (`src/main/resources/resilience`). Protects external calls (e.g. PayOS) using Circuit Breakers, Rate Limiters, and Retries.

## 4. Technology Stack & Tools

-   **Language**: Java 21
-   **Framework**: Spring Boot 3.4.2
-   **Database**: PostgreSQL 16 with `pgvector` for AI vector embeddings (e.g., resume matching).
-   **Schema Migrations**: Flyway.
-   **Caching / Session**: Redis.
-   **Messaging / Events**: Apache Kafka (used for async jobs like email sending & webhooks).
-   **Object Mapping**: MapStruct & Lombok.
-   **Resilience**: Resilience4j.
-   **Security**: Spring Security 6 with OAuth2 (Google, Github) & JWT.
-   **Payment Gateway**: PayOS Integration with webhook signature verification.
-   **Build Tool**: Maven (`pom.xml`).
-   **CI/CD**: `Jenkinsfile` for automated build, test, and deployment pipelines. GitHub Actions (`.github/workflows`) are also used.
-   **API Documentation**: Provided via Swagger/OpenAPI (locally) and documented Postman collections (`postman/`).
