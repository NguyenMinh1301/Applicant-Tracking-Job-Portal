# Changelog

All notable changes to VietRecruit are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

## [Unreleased]

## [2026-03]

### Added

#### Elasticsearch & Redis Caching (PR #41, #42)
- Implemented Elasticsearch-based full-text search services for jobs, candidates, and companies with custom Vietnamese analyzers (ICU tokenizer, edge-ngram, synonyms)
- Implemented Elasticsearch index initialization, Debezium CDC sync consumers (`JobSyncConsumer`, `CandidateSyncConsumer`, `CompanySyncConsumer`), and a population health indicator
- Added Elasticsearch startup data bootstrap (`ElasticsearchDataBootstrap`) to populate indices from JPA on cold start
- Integrated Debezium PostgreSQL connector tracking `jobs`, `candidates`, `companies`, and `users` tables
- Implemented Redis caching infrastructure via `RedisCacheManager` with per-cache TTLs for jobs, companies, categories, locations, and subscriptions
- Added Kafka-driven cache invalidation topic and consumer (`CacheInvalidationConsumer`) using Redis SCAN — no `@CacheEvict` in service layer
- Added job ranking fields (`view_count`, `apply_count`) to the job entity and database schema

#### AI Features (PR #36, #38, #39)
- Integrated Spring AI and pgvector dependencies for AI-powered capabilities
- Implemented core AI inference, embedding, and vector store services (`EmbeddingService`, `RagService`, `AgentService`)
- Implemented AI-powered job recommendation service for candidates (`GET /candidates/me/job-recommendations`)
- Implemented automated candidate screening via AI with endpoints to trigger and retrieve screening results
- Implemented AI-driven CV improvement advisor (`CvImprovementService`)
- Implemented job description generator with tone control (`POST /jobs/generate-description`)
- Implemented AI interview question generator with entity, repository, and database migration
- Implemented salary benchmarking estimator with controller and service (`SalaryBenchmarkService`)
- Implemented knowledge document ingestion and retrieval services with admin controller
- Implemented Redis-backed agent memory store (`AgentMemoryStore`)
- Added AI-specific error codes, exception handlers, API constants, and Resilience4j rate limiter and circuit breaker configurations for OpenAI calls
- Added database migrations for vector store tables, AI score constraints, and knowledge documents
- Added pgvector and AI API keys to environment configuration

#### Invitation System
- Implemented invitation service and controller (`POST /invitations`)
- Added support for user registration by invitation
- Added database migration for the `invitations` table

#### Candidate Module (PR #33)
- Implemented candidate profile update endpoints with resume upload support
- Implemented candidate entity, repository, MapStruct mapper, and DTOs
- Added auto-creation of candidate profile on user registration
- Implemented CV metadata storage with database migration
- Integrated Cloudflare R2 storage service abstraction (`StorageService`) for file uploads
- Added magic-byte file validation utility to enforce upload security
- Added R2 circuit breaker, retry, and timeout resilience patterns

#### Job Module (PR #29)
- Implemented full job lifecycle management: `DRAFT → PUBLISHED → CLOSED`
- Implemented employer and public job endpoints (`JobController`)
- Implemented `JobService` with quota enforcement, job specifications, and Elasticsearch-ready query support
- Implemented Job DTOs, MapStruct mappers, and `JobStatus` enum
- Added LIKE wildcard sanitization in JPQL job specifications to prevent injection
- Resolved N+1 performance bottleneck in application batch queries

#### Company, Department, Location, Category Modules (PR #31)
- Implemented `Company` module with entity, repository, service, controller, DTOs, and mapper
- Implemented `Department` module with entity, repository, service, controller, DTOs, and mapper
- Implemented `Location` module with entity, service, and controller
- Implemented `Category` module with entity, service, and controller
- Enhanced Swagger pagination documentation using `@ParameterObject`
- Extracted `resolveCompanyId` into `BaseController` to eliminate duplication

#### Subscription Module (PR #20)
- Implemented subscription and job quota JPA entities and repositories
- Implemented quota guard and subscription management business logic
- Exposed employer subscription and plan management endpoints
- Added database migrations for subscription plans, employer subscriptions, quotas, and permissions
- Implemented system admin account seeder and subscription expiry scheduler
- Resolved quota increment race condition with optimistic locking

#### Payment Module (PR #24, #26, #27)
- Implemented PayOS checkout service, webhook handler, and controllers
- Implemented payment transaction entities, repositories, DTOs, and history endpoints
- Implemented timing-safe in-house PayOS webhook signature verifier
- Applied Resilience4j rate limiting to the webhook endpoint
- Added scheduled jobs for payment recovery and reconciliation
- Added payment failure columns and indices for operational hardening
- Added implicit PostgreSQL enum casts for payment and subscription statuses
- Added database migrations for payment transactions

#### Authentication & Security (PR #12, #13)
- Implemented stateless JWT (HS512) authentication with Redis token caching
- Implemented refresh token rotation and explicit error codes
- Implemented email verification flow with numeric 8-digit OTP generation and Redis caching
- Implemented OAuth2 social login flows (Google, GitHub) with cookie-based authorization request repository and one-time code exchange
- Implemented change-password and reset-password endpoints with high-entropy token generation
- Enforced strict JWT issuer and audience verification
- Segregated client (`/api/v1/users`) and admin (`/api/v1/admin`) API namespaces with namespace-level security enforcement
- Implemented client-facing self-service profile endpoints (`GET/PUT /users/me`)
- Replaced shared user CRUD with admin-scoped management layer
- Added email verification filter optimized to evaluate JWT claims directly

#### Notification Module (PR #14)
- Implemented Kafka-based email notification publisher and consumer (`NotificationEmailConsumer`)
- Integrated Resend SDK as the email delivery provider
- Implemented email template resolver supporting multiple notification types
- Added Resilience4j retry and circuit breaker configurations for email delivery
- Redesigned email templates to align with brand guidelines

#### OpenAPI Documentation (PR #15)
- Integrated Swagger/OpenAPI documentation for authentication and user management endpoints
- Configured public endpoint exclusions in security for Swagger UI

### Changed

- Refactored AI module from `common` to `feature` package and reorganized into specialized sub-packages (`shared`, `knowledge`, `ingestion`, `matching`, `screening`, `cv`, `jd`, `interview`, `salary`)
- Decoupled PayOS payment checkout from subscription services and controllers
- Extracted `PaymentStatus`, `SubscriptionStatus`, `BillingCycle`, `JobStatus`, `EmailSenderAlias` as standalone enums from inline definitions
- Extracted API error and success code enums (`ApiErrorCode`, `ApiSuccessCode`) into `common/enums`
- Split `UserMapper` into client and admin mapping methods
- Refactored Jenkins pipeline to use `DOCKER_REPO` variable instead of hardcoded image name
- Updated service memory configurations and added Logstash host to Docker Compose health checks
- Bumped project version from `0.0.1` → `1.0.0` → `1.0.2` → `1.0.5` → `1.0.7` → `1.0.8` → `1.1.1`
- Migrated agent configuration from `.agent/` directory to `.claude/rules` submodule

### Fixed

- Fixed `EmailVerificationFilterTest` compilation error by properly mocking `Claims`
- Fixed duplicate scorecard uniqueness constraint and enforced safe cascade deletions
- Enforced `RESTRICT ON DELETE` constraints for core application tracking tables
- Fixed PostgreSQL native enum type casting errors in subscription and payment queries
- Fixed CI deployment failure caused by destructive `docker rmi latest` command in Jenkins pipeline
- Fixed stale enum imports in multiple test classes causing CI failures
- Fixed `AuthControllerTest` by mocking `authService.register` return value

---

## [2026-02]

### Added

- Initialized project with Spring Boot 3.4.2 / Java 21 and Maven build tooling
- Added multi-stage Dockerfile and `.dockerignore` for production image builds
- Configured `docker-compose.yml` for local infrastructure (PostgreSQL 16 + pgvector, Redis, Zookeeper, Kafka, Maildev)
- Configured `docker-compose.prod.yml` for production infrastructure (ELK stack, Prometheus, Grafana, Kafka-UI, Debezium)
- Implemented modular Docker Compose layout using `include` directives
- Configured ELK stack (Elasticsearch, Logstash, Kibana) with Logstash pipeline for log ingestion
- Configured Prometheus scrape targets and Grafana storage volume
- Configured Debezium PostgreSQL connector and initialization script
- Added Kafka producer, consumer factories, and CDC record filter (`KafkaConfig`)
- Configured Resilience4j circuit breakers, rate limiters, and retry policies
- Configured Logback with async appenders for structured log streaming to Logstash
- Added initial Flyway schema migrations for core types, users, companies, recruitment entities, RBAC, refresh tokens, interviews, scorecards, and offers
- Implemented `User` entity, repository, DTOs, MapStruct mapper, service, and CRUD controller
- Implemented standardized `ApiResponse` and `PageResponse` wrappers
- Implemented `GlobalExceptionHandler` for uniform error responses
- Implemented `BaseController` with API constants
- Added Postman collection for user module
- Configured Spring Security chain to permit Actuator endpoints
- Added GitHub Actions workflow for running unit tests on push to `main`/`release`
- Added basic Jenkins declarative pipeline with Docker build, push, and deploy stages
- Added environment variable templates (`.env.example`)
- Configured application properties and profiles (`application.yaml`, `application.dev.yaml`, `application.prod.yaml`)
- Set default application timezone to `Asia/Ho_Chi_Minh`
- Added Testcontainers configuration and `application-test.yml` for integration tests
- Added `logback-test.xml` for test-scoped console logging

### Changed

- Migrated vector store from Qdrant to pgvector with a Flyway migration
- Modularized infrastructure Docker Compose with `include` directives
- Added `spring-ai`, `spring-kafka`, `elasticsearch`, and mail dependencies to `pom.xml`

### Removed

- Removed Qdrant service, volume, and scrape targets from all compose and monitoring configurations
- Removed legacy monolithic Flyway migration scripts replaced by granular per-domain migrations
