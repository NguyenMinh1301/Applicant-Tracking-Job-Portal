# Project Structure and Architecture

This document provides an overview of the `VietRecruit` project structure, key components, and architectural patterns.

## 1. Directory Structure

```
VietRecruit/
├── infra/                      # Infrastructure configurations (Docker compose)
│   ├── application/            # Application-level services (Application, Kafka, etc.)
│   ├── database/               # Database images (PostgreSQL, Redis, etc.)
│   └── monitoring/             # Observability stack (Prometheus, Grafana, Redis exporter, etc.)
├── docs/                       # Documentation (API, Architecture, etc.)
├── postman/                    # API Collections
│   └── vietrecruit-api-collection.json
├── src/
│   ├── main/
│   │   ├── java/com/vietrecruit/
│   │   │   ├── common/                 # Shared utilities and base classes
│   │   │   │   ├── base/               # Base controllers, entities
│   │   │   │   ├── config/             # Global configurations (Security, Kafka)
│   │   │   │   ├── exception/          # Global exception handling
│   │   │   │   ├── response/           # Standardized API response wrappers
│   │   │   │   └── ApiConstants.java   # API endpoint constants
│   │   │   ├── feature/                # Feature modules (Domain-Driven Design)
│   │   │   │   └── example/            # Example module
│   │   │   │       ├── controller/     # REST Endpoints
│   │   │   │       ├── dto/            # Data Transfer Objects
│   │   │   │       ├── entity/         # JPA Entities
│   │   │   │       ├── mapper/         # MapStruct Mappers
│   │   │   │       ├── repository/     # Spring Data Repositories
│   │   │   │       └── service/        # Business Logic Interfaces & Impl
│   │   │   └── Application.java        # Entry point
│   │   └── resources/
│   │       ├── db/migration/           # Flyway SQL migrations
│   │       ├── resilience/             # Resilience4j configurations
│   │       │   ├── circuitbreaker/
│   │       │   ├── ratelimiter/
│   │       │   └── retry/
│   │       ├── application.yaml        # Main configuration
│   │       └── logback-spring.xml      # Logging configuration
│   └── test/                           # Unit and Integration tests
└── pom.xml                             # Maven dependencies
```

## 2. Architecture Overview

The project follows a **Modular Monolith** approach with a **Layered Architecture** within each feature module.

### Core Layers
1.  **Presentation Layer (`controller`)**: Handles HTTP requests, validation, and maps responses. Extends `BaseController` for common resilience patterns.
2.  **Service Layer (`service`)**: Contains business logic. defined by interfaces with implementations in `impl` sub-packages.
3.  **Data Access Layer (`repository`)**: Interfaces with the database using Spring Data JPA.
4.  **Domain Layer (`entity`)**: Represents the persistent data model.

### Key Components

-   **Common Module**: Contains reusable definitions like `ApiResponse`, `ApiException`, and `GlobalExceptionHandler` to ensure consistency across all features.
-   **Mapper**: Maps between entities and DTOs using **MapStruct**.
-   **ApiConstants**: Contains API endpoint constants.
-   **Resilience**: Implements Circuit Breaker, Rate Limiter, and Retry patterns using **Resilience4j**, configured via YAML files in `src/main/resources/resilience`.
-   **Infrastructure**: Uses **Docker Compose** to manage dependencies like PostgreSQL, Kafka, Redis, and the ELK stack.
-   **API Documentation**: Managed via Postman collections in the `postman/` directory.

## 3. Technology Stack

-   **Language**: Java 21
-   **Framework**: Spring Boot 3.4.2
-   **Database**: PostgreSQL
-   **Caching**: Redis
-   **Vector DB**: Qdrant
-   **Migration**: Flyway
-   **Messaging**: Kafka
-   **Search**: Elasticsearch
-   **Resilience**: Resilience4j
-   **Build Tool**: Maven
-   **Logging**: ELK Stack
