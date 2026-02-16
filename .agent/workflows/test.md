---
description: Ingest context to map coverage and generate comprehensive automated test suites.
---

# /test - Verification and Coverage Mode

$ARGUMENTS

---

## 🔴 CRITICAL RULES

1. **Context Dependency** - Read docker-compose.yml, pom.xml, and application.yml to determine testing environment (e.g., Testcontainers setup).
2. **Boundary Isolation** - Unit tests must mock external dependencies. Integration tests must use realistic infrastructure.
3. **Comprehensive Coverage** - Target positive, negative, and extreme boundary scenarios.

---

## Task

Execute the test generation protocol with the following context:

```
CONTEXT:
- Target Subject: $ARGUMENTS
- Mode: TEST SUITE GENERATION
- Output: Test classes and coverage estimation.

EXECUTION STEPS:
1. Ingest target class and infrastructure context.
2. Map logic paths to determine required test cases.
3. Generate JUnit 5 / Mockito code for isolated unit tests.
4. Generate Integration tests (using Testcontainers if databases/brokers are present in docker-compose).
5. Ensure tests execute independently without side-effects.
```

---

## Expected Output

| Deliverable | Location |
|-------------|----------|
| Unit Test Files | src/test/java/... |
| Integration Tests | src/test/java/... |
| Coverage Report | Terminal |

---

## After Testing

```
[OK] Test suite generated.

Next steps:
- Execute Maven/Gradle test task.
- If failures occur, run `/debug` with the provided stack trace.
```

---

## Usage

```
/test ProductController and related application services
/test Kafka event consumer using embedded broker
```