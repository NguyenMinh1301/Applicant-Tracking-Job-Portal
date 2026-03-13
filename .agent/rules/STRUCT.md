---
trigger: model_decision
description: I only turn it on when I'm active or working on a VietRecruit or VietRecruit-BE project.
---

# Project Structure and Architecture

This document provides a comprehensive overview of the `VietRecruit` project structure, key technical components, and defined architectural patterns. Use this as a map to understand how the codebase and infrastructure are organized.

## 0. Application Purpose

**VietRecruit** is an end-to-end Applicant Tracking System (ATS) and specialized job portal. The platform serves two primary user bases:
1. **Candidates**: Can build their CVs/profiles, search for relevant opportunities, and apply directly to published jobs.
2. **Employers (HR/Company Admins)**: Can manage their company's branding, configure internal departments, purchase subscription plans (processed via PayOS integration) to acquire job posting quotas, and publish open roles.

The ATS subsystem tracks candidates across a complete recruitment pipeline: `Application received` -> `Interview scheduling (with cross-interviewer scorecards)` -> `Offer generation and acceptance`. 

The system relies on a **Modular Monolith** architecture with Domain-Driven Design (DDD) principles to ensure each feature encapsulates its own logic and database concerns, enabling strict access control, resilience against failure, and clear separation of business domains.

## 1. Top-Level Directory Structure

```text
VietRecruit/
├── .agent/                     # AI Agent configuration, skills, workflows, and rules
├── .github/                    # GitHub Actions workflows for CI/CD
├── assets/                     # Static project assets (images, system diagrams)
├── database/                   # Database scripts and aggregate Flyway init scripts
├── debezium/                   # Debezium configurations for CDC (Change Data Capture)
├── docs/                       # Project Documentation (Architecture, APIs, rules)
├── elk/                        # ELK Stack configuration (Elasticsearch, Logstash, Kibana)
├── grafana-storage/            # Persistent storage volume for Grafana
├── infra/                      # Legacy/alternative infrastructure configs
├── monitor/                    # Prometheus monitoring configurations
├── postman/                    # API Collections (Postman) for testing
├── scripts/                    # Utility scripts for development and deployment
├── src/                        # Main Application Source Code (Java/Spring Boot)
│   ├── main/
│   │   ├── java/com/vietrecruit/
│   │   │   ├── common/         # Shared horizontal utilities and base classes
│   │   │   │   ├── base/       # Abstract Controller and Entity enforcing common behaviors
│   │   │   │   ├── config/     # Global configurations (Web, Security, Kafka, Redis, OpenAPI)
│   │   │   │   ├── enums/      # Global enums (ApiErrorCode, ApiSuccessCode, UserRole)
│   │   │   │   ├── exception/  # Global Exception Handler returning standard API responses
│   │   │   │   ├── init/       # Application seeders (e.g. creating default admin)
│   │   │   │   ├── response/   # Standardized wrapper classes (ApiResponse, PageResponse)
│   │   │   │   ├── security/   # JWT utilities, Auth Managers, OAuth2 flows
│   │   │   │   ├── storage/    # Multi-provider storage framework (e.g. Cloudflare R2)
│   │   │   │   └── util/       # Application-wide utility helper classes
│   │   │   ├── feature/        # Vertical feature modules (Domain-Driven Design)
│   │   │   │   ├── application/# ATS: Applications, Interviews, Scorecards, Offers
│   │   │   │   ├── auth/       # Authentication (Login, Register, Password Reset, Refresh)
│   │   │   │   ├── candidate/  # Candidate profiles and resume management
│   │   │   │   ├── category/   # Job categories reference system
│   │   │   │   ├── company/    # Employer company profiles and branding
│   │   │   │   ├── department/ # Internal employer departments
│   │   │   │   ├── job/        # Job postings, visibility controls, and search attributes
│   │   │   │   ├── location/   # Job locations reference system
│   │   │   │   ├── notification/# Asynchronous email & in-app alerts (via Kafka)
│   │   │   │   ├── payment/    # PayOS integration and webhook verification
│   │   │   │   ├── subscription/# Employer subscription plans, billing, and posting quotas
│   │   │   │   └── user/       # General user account records and management
│   │   │   └── Application.java# Spring Boot application entry point
│   │   └── resources/
│   │       ├── db/migration/   # Flyway SQL migrations determining database schema
│   │       ├── resilience/     # Resilience4j YAML files for Circuit Breaker/Rate Limiting
│   │       ├── templates/email # Thymeleaf/Freemarker HTML email templates
│   │       ├── application.yaml# Core application configuration
│   │       ├── application.dev.yaml  # Dev profile (local backing services)
│   │       ├── application.prod.yaml # Prod profile (managed remote backing services)
│   │       └── logback-spring.xml    # Advanced ELK / Async Logging configuration
│   └── test/                   # Unit tests and Testcontainers integration suites
├── .env                        # Local development environment variables
├── .env.example                # Template for environment variables
├── .env.prod                   # Production environment variables
├── docker-compose.yml          # Local infrastructure (Postgres, Redis, Kafka, Zookeeper)
├── docker-compose.prod.yml     # Production infrastructure including observability stack
├── Dockerfile                  # Production-ready multi-stage Docker build
├── Jenkinsfile                 # Jenkins CI/CD declarative pipeline
├── Makefile                    # Make command shortcuts for developers
└── pom.xml                     # Maven definitions and dependency lock
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
The domain is split into strict business capabilities containing distinct layers. Features represent isolated system components with limited cross-coupling.
Inside each module (e.g., `job`, `subscription`, `payment`):
- `controller/`: REST API endpoints. Extends `BaseController` for common resilience patterns and standardized error handling.
- `service/`: Interfaces and `impl/` implementations for pure business logic and transaction boundaries.
- `repository/`: Spring Data JPA interfaces. Includes `Specification` files targeting specific database-level search capabilities.
- `entity/`: JPA annotated domain models mirroring the relational schema.
- `mapper/`: MapStruct interfaces used to cleanly uncouple Entities from external representation DTOs.
- `dto/`: Immutable Request/Response objects isolated to controller interaction payload contracts.

## 3. Infrastructure & Observability

- **Database Initialization:** Flyway manages schema versions internally. `database/init-script.sql` holds aggregate setups for easy Docker volume provisioning if necessary.
- **Docker Compose:**
  - `docker-compose.yml`: Spins up the local development dependencies (PostgreSQL 16 + pgvector, Redis, Zookeeper, Kafka, Maildev).
  - `docker-compose.prod.yml`: Expands to full production infrastructure including the ELK stack, Prometheus, Grafana, and the app service itself.
- **Observability Stack:** 
  - **Logging**: Logback logs to standard output and streams to Logstash. Logstash parses and forwards to Elasticsearch. Analysts can monitor via Kibana.
  - **Metrics**: Spring Boot Actuator pushes data via Micrometer to Prometheus (`monitor/`). Grafana consumes Prometheus data for real-time dashboards (`grafana-storage/`).
- **Resilience**: Integrates **Resilience4j** mapped via YAML (`src/main/resources/resilience`). Exposes system endpoints via `@RateLimiter`, and external calls (e.g. to PayOS) via `@CircuitBreaker` and `@Retry`.

## 4. Technology Stack & Tools

-   **Language**: Java 21
-   **Framework**: Spring Boot 3.4.2
-   **Database**: PostgreSQL 16 equipped with `pgvector` for AI similarity calculations.
-   **Schema Migrations**: Flyway.
-   **Caching / Session**: Redis.
-   **Messaging / Events**: Apache Kafka (async operations such as triggering emails and webhooks).
-   **Object Mapping**: MapStruct & Lombok.
-   **Resilience**: Resilience4j.
-   **Security**: Spring Security 6 utilizing stateless JWTs, mapped to OAuth2 identity providers (Google, Github).
-   **Payment Gateway**: PayOS native API integration with webhook signature verification.
-   **Build Tool**: Maven (`pom.xml`).
-   **CI/CD**: `Jenkinsfile` combined with GitHub Actions (`.github/workflows`) for robust automated rollouts.
-   **Testing**: Testcontainers alongside JUnit/Mockito for rigorous integration scenarios.
-   **API Documentation**: Provided via Swagger/OpenAPI (locally) and documented Postman collections (`postman/`).
