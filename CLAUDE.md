# BE-VietRecruit — Claude Code Configuration

## Project

VietRecruit Backend — Spring Boot 3.4.2 ATS and job portal.
Modular Monolith, Domain-Driven Design.
Java 21, PostgreSQL 16 + pgvector, Redis 7, Elasticsearch 8.x, Apache Kafka.

See `.claude/rules/STRUCT.md` for the authoritative architecture map, module roster, and infrastructure details.

---

## Key Commands

```bash
# Build (no tests)
./mvnw clean package -DskipTests

# Compile only
./mvnw clean compile -q

# Run all unit tests (exclude integration)
./mvnw test -Dtest='!com.vietrecruit.ApplicationTests'

# Run single test class
./mvnw test -Dtest=ClassName

# Run integration test (requires Docker)
./mvnw test -Dtest=com.vietrecruit.ApplicationTests

# Auto-format code (Google AOSP)
./mvnw spotless:apply

# Check format without modifying
./mvnw spotless:check

# Run locally (dev profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Start local infrastructure
docker compose up -d

# Stop infrastructure
docker compose down

# make shortcuts
make run     # compose up -d + spring-boot:run
make stop    # compose down
make clean   # mvnw clean + compose down -v
```

---

## Environment

- Local config: `.env` (copy from `.env.example`)
- Dev profile: `src/main/resources/application.dev.yaml`
- Prod profile: `src/main/resources/application.prod.yaml`
- Required local services: PostgreSQL 16, Redis 7, Kafka + Zookeeper, Maildev
- Start all: `docker compose up -d`

Infrastructure compose is split across `include:` references in root `docker-compose.yml`:
- `infra/database/` — PostgreSQL, Redis
- `infra/application/` — Kafka, Debezium, Elasticsearch, Logstash, Kibana
- `infra/monitoring/` — Prometheus, Grafana (commented out by default)

Key env vars: `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `JWT_SECRET`, `OPENAI_API_KEY`, `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, `R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`, `RESEND_API_KEY`, `GOOGLE_CLIENT_ID`, `GITHUB_CLIENT_ID`, `ELASTICSEARCH_URI`

---

## Agent System

AgentKit submodule at `.claude/`. 2-layer system.

**Core (AgentKit submodule)**: agents/, skills/, workflows/ — stack-agnostic

**Project overlay (this repo)**:
- `.claude/rules/STRUCT.md` — authoritative project map (load before any implementation)
- `.claude/rules/COMMENT_STYLE.md` — Java comment and Javadoc enforcement
- `.claude/rules/AGENT.md` — routing protocol and code generation standards (P0 priority)
- `.claude/rules/BASE.md` — communication style

**Workflow pipeline**:
```
/brainstorm → /plan → /create → /test → /review → /enhance → /debug → /docs → /commit
```

**Before implementing anything**, read:
1. `.claude/rules/STRUCT.md` — project structure ground truth
2. `.claude/rules/AGENT.md` — execution protocol and standards
3. `.claude/skills/SKILLS.md` — skill index

Skill index: `.claude/skills/SKILLS.md`
Agent index: `.claude/agents/AGENTS.md`

---

## Architecture Constraints

### Module boundaries

- Controllers MUST NOT access Repository beans directly — always through Service layer
- Cross-module access: import Service interface only, never Entity or Repository from another module
- `common/` packages are the only legitimate cross-cutting dependency
- Feature modules must not import from sibling feature modules

### Transaction rules

- `@Transactional` belongs on Service impl methods, not Controller or Repository
- `REQUIRES_NEW` restricted to: `SubscriptionActivationService` (PayOS webhook isolation) and `PaymentReconciliationExecutor` (per-payment reconcile)
- Never annotate interface methods with `@Transactional`
- Document all `REQUIRES_NEW` usages with inline comment explaining isolation rationale

### Testing rules

- Integration tests use Testcontainers — never H2 as PostgreSQL substitute
- Target: 85% line coverage, 80% branch coverage on Service impl classes
- Test class naming: `{ClassName}Test` (unit), `{ClassName}IT` (integration)
- WireMock available for external API mocking (PayOS, Resend, OpenAI)
- Awaitility available for async assertion patterns

### Cache rules

- No `@CacheEvict` in service layer — all eviction is Kafka-driven via `cache.invalidation` topic
- Cache key pattern: `vietrecruit:{domain}:{id}` for entries, `vietrecruit:{domain}:list` for lists
- Never add new Spring Cache annotations without updating `CacheNames` constants
- All 8 named caches have fixed TTLs — do not add `@Cacheable` without a corresponding `CacheNames` constant

### Elasticsearch rules

- Index operations go through `*SearchServiceImpl` (low-level REST client), not Spring Data ES
- Never write directly to ES indices — all writes go through CDC consumers (Debezium → Kafka → SyncConsumer)
- Index names must use prefix `vietrecruit_`
- `ElasticsearchIndexInitializer` creates indices idempotently on startup — never create indices in application code

### AI module rules

- CV text extraction uses Apache Tika via `TikaDocumentReader` only
- Agent memory key pattern: `ai:mem:{userId}:{sessionId}` (TTL 1hr, max 10 messages)
- Embedding cache key pattern: `ai:emb:{sha256(text)}` (TTL 24hr)
- Vector dimensions: 1536 (PgVectorStore HNSW index — cannot change without migration)
- AI knowledge chunks: 100–800 tokens; valid categories in `application.yaml` under `ai.knowledge.supported-categories`

### Security rules

- All endpoints require JWT unless explicitly `@PermitAll` in `SecurityConfig`
- OAuth2 cookie serialization: Jackson + Spring Security modules, URL-safe Base64; `HttpOnly`, `Secure`, `SameSite=Lax`
- PayOS webhook: verify HMAC signature against `PAYOS_CHECKSUM_KEY` before processing any event

---

## Feature Modules

| Module | Responsibility |
|--------|----------------|
| `ai` | AI features: matching, screening, CV advice, JD generation, interview questions, salary benchmark |
| `application` | ATS pipeline: applications, interviews, scorecards, offers |
| `auth` | Authentication: login, register, OAuth2, JWT refresh, password reset, email verification |
| `candidate` | Candidate profiles, resume management, Elasticsearch search |
| `category` | Job category reference data |
| `company` | Employer profiles, branding, Elasticsearch search |
| `department` | Internal employer department management |
| `invitation` | Team invitation flows |
| `job` | Job postings, visibility controls, quota management, Elasticsearch search |
| `location` | Location reference data |
| `notification` | Async email and in-app alerts via Kafka + Resend API |
| `payment` | PayOS integration, webhook verification, payment reconciliation |
| `subscription` | Subscription plans, billing cycles, job posting quotas |
| `user` | User account management, RBAC |

---

## Code Formatting

Spotless with Google Java Format (AOSP):
- 4-space tabs
- Import order: `java`, `jakarta`, `org`, `com`
- Unused imports removed automatically
- Trailing whitespace trimmed, final newline enforced
- Enforced at `compile` phase via Spotless Maven plugin

Run `./mvnw spotless:apply` before committing.

---

## CI/CD

- **Jenkins**: `Jenkinsfile` — builds Docker image, pushes to Docker Hub, deploys to VPS
- **GitHub Actions**: `.github/workflows/` — unit tests on push to `main`/`release`
- **Production Docker**: `Dockerfile` (multi-stage build)
- **Production compose**: `docker-compose.prod.yml` (includes ELK, Prometheus, Grafana, app service)

---

## Hooks (Active)

Configured in `.claude/settings.local.json`:
- **PostToolUse** (Write/Edit/MultiEdit): runs `.claude/hooks/post-edit-format.sh` → `./mvnw spotless:apply -q` on `.java` files
- **PreToolUse** (git commit): runs `.claude/hooks/pre-commit-lint.sh` → blocks commit on comment style violations (decorative dividers, TODO without VR-NNN, System.out.print)
