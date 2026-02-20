---
trigger: always_on
description: Core execution rules and constraints for all AI agents operating within this workspace.
---

## CRITICAL: AGENT & SKILL PROTOCOL (START HERE)

> **MANDATORY:** You MUST read the appropriate agent file and its skills BEFORE performing any implementation. This is the highest priority rule.

### 1. Direct Smart Agent Protocol (Query -> Index -> Skill)

To optimize token usage and ensure precise context loading, follow this STRICT protocol for every user request:

1.  **Analyze Query**: Determine the core intent (e.g., "Designing an API" -> API Patterns).
2.  **Consult Index**: Read **`.agent/skills/SKILLS.md`** first. This file acts as the master index.
3.  **Select Skill**: Identify the specific skill directory from the index that matches the request.
4.  **Load Context**: Read the `SKILL.md` (or equivalent) file WITHIN that specific skill directory.
    *   **Constraint:** DO NOT read the entire skill directory. Only read the targeted documentation.

- **Rule Priority:** P0 (GEMINI.md) > P1 (STRUCT.md) > P2 (BASE.md). All rules are binding.

# RULES.md - AI Execution Protocol

> This file defines the absolute constraints, validation requirements, and operational boundaries for all AI agents. It must be read and adhered to continuously during any task execution.

---

> **MANDATORY:** You MUST strictly follow the phased execution model below. Failure to do so is a violation of the workspace protocol.

### 1. Phased Execution Dependency

The AI must NEVER execute code generation (`/create`, `/enhance`, `/debug`) without first referencing a validated plan (`/plan`) or having explicit, documented context (`/brainstorm`).

**Valid Execution Flow:**
1. **Ideation:** `/brainstorm` (Synthesize requirements, propose solutions).
2. **Planning:** `/plan` (Define tasks, anticipate edge cases).
3. **Implementation:** `/create`, `/enhance`, `/debug` (Generate code based strictly on the plan).
4. **Verification:** `/test` (Validate the implementation).
5. **Snapshot:** `/commit` (Structure the changes).

### 2. Mandatory Context Ingestion (Pre-Code Mandate)

The AI MUST locate, parse, and comprehend all environment, configuration, and dependency files PRIOR to generating or modifying any code. 

* **Target Files:** `pom.xml`, `docker-compose.yml`, `application.yml`, `application.properties`, `.env.example`, and any relevant library configurations.
* **Zero-Assumption Constraint:** Do not hallucinate library versions, framework configurations, or infrastructure connections. All implementation details must strictly align with the parsed configuration state. 
* **Dependency Validation:** If a requisite dependency or library is missing from `pom.xml` or equivalent, the AI must explicitly halt code generation and propose its addition before utilizing its classes.

### 3. The "No-Code" Constraint

If operating under `/brainstorm` or `/plan` modes, the AI is **STRICTLY PROHIBITED** from generating executable code or scripts. These modes are exclusively for conceptualization, architectural design, and task sequencing.

---

## TIER 1: CODE GENERATION STANDARDS

When operating in implementation modes (`/create`, `/enhance`, `/debug`), all generated code MUST adhere to the following standards:

### 1. Architectural Integrity

* **Clean Architecture:** Strict adherence to Domain, Application, Infrastructure, and Presentation layers. Dependencies point inwards.
* **Domain-Driven Design (DDD):** Code must reflect the ubiquitous language. Logic resides in Entities, Value Objects, and Domain Services.
* **Infrastructure Isolation:** Framework-specific annotations (e.g., Spring Boot `@Entity`, `@Table`) MUST NOT leak into the Domain layer.

### 2. Code Quality & Maintainability

* **SOLID Principles:** All classes and methods must adhere to SOLID principles.
* **DRY (Don't Repeat Yourself):** Abstract repetitive logic into reusable components or utility functions.
* **Atomic Methods:** Methods should perform a single, identifiable task.
* **Self-Documenting Code:** Use descriptive variable and method names. Comments should explain *why*, not *what*.

### 3. Performance & Resilience

* **Asynchronous Processing:** Utilize non-blocking I/O (e.g., `CompletableFuture`, WebFlux) for long-running operations.
* **Database Optimization:** Proactively avoid N+1 query problems (use `JOIN FETCH`, Entity Graphs). Ensure appropriate indexing (including `pgvector` for AI-related queries).
* **Resilience Patterns:** Implement Circuit Breakers and Rate Limiters (e.g., via Resilience4j) for all external API and microservice calls.

---

## TIER 2: TESTING & VALIDATION PROTOCOL

All implemented logic must be verifiable. The AI must enforce testing rigor.

### 1. Mandatory Coverage Constraints

* **Line Coverage:** Minimum 85% required for all new or modified domain logic.
* **Branch Coverage:** Minimum 80% required.

### 2. Testing Methodology

* **Unit Tests:** Must isolate dependencies using mocking frameworks (e.g., Mockito). Focus on validating internal logic.
* **Integration Tests:** Must utilize **Testcontainers** to spin up realistic infrastructure (PostgreSQL, Redis, Kafka). In-memory databases (like H2) are prohibited for integration testing unless explicitly requested.
* **Contract Testing:** API boundaries must be validated against defined OpenAPI contracts.

---

## TIER 3: DEBUGGING & RCA PROTOCOL

When operating in `/debug` mode, the AI must follow a structured approach to problem resolution.

### 1. Root Cause Analysis (RCA) Required

Before suggesting any code patch, the AI MUST provide an RCA detailing:
1. **The Error:** The specific exception or anomaly.
2. **The Location:** The exact file and line number (if determinable).
3. **The Cause:** The logical or structural reason for the failure.

### 2. Patch Constraints

* **Targeted Fixes:** Patches must strictly address the identified root cause.
* **Zero Regression:** The patch must not alter the intended behavior of adjacent logic or break existing tests.
* **Contextual Awareness:** The AI must consider the broader system state (e.g., database locks, network latency) if the error implies environmental issues.

---