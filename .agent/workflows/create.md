---
description: Ingest configuration context, generate implementation, enforce standards, and self-validate.
---

# /create - Implementation and Validation Mode

$ARGUMENTS

---

## 🔴 CRITICAL RULES

1. **Context Ingestion First** - Must read docker-compose.yml, pom.xml, application.yml, and .env.example before generating code.
2. **Strict Adherence** - Implementation must map directly to the active plan or brainstorm output.
3. **Standard Enforcement** - Mandate SOLID principles, DRY methodology, and high-performance algorithms.
4. **Self-Correction** - Autonomous detection and resolution of compilation/logic flaws before final output.

---

## Task

Execute the creation protocol with the following context:

```
CONTEXT:
- Target Module: $ARGUMENTS
- Mode: IMPLEMENTATION AND VALIDATION
- Output: Source code files and execution report.

EXECUTION STEPS:
1. Parse attached infrastructure and dependency configurations.
2. Generate Java/Spring Boot code fulfilling the target module requirements.
3. Verify against architecture boundaries (e.g., Domain layer must not leak Infrastructure annotations).
4. Simulate execution. Identify syntax, logic, or performance anomalies.
5. Apply auto-correction.
6. Generate standardized summary report.
```

---

## Expected Output

| Deliverable | Location |
|-------------|----------|
| Source Code | Target directories (src/main/java/...) |
| Execution Report | Terminal |

---

## After Creation

```
[OK] Code generation and self-validation complete.
[WARN] 1 anomaly auto-corrected during simulation phase.

Next steps:
- Review generated logic.
- Run `/test` to verify domain accuracy and integration boundaries.
```

---

## Usage

```
/create UserRegistrationService according to PLAN-user-auth.md
/create Kafka consumer for indexing order events
```