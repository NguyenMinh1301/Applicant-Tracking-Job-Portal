---
description: Optimize performance, resolve bottlenecks, and implement resilience without altering domain logic.
---

# /enhance - Optimization and Resilience Mode

$ARGUMENTS

---

## 🔴 CRITICAL RULES

1. **Domain Immutability** - Business logic and API contracts must remain functionally identical.
2. **Targeted Optimization** - Focus strictly on I/O latency, database query optimization, and memory management.
3. **Resilience Integration** - Apply patterns (Circuit Breaker, Rate Limiting) where external dependencies exist.

---

## Task

Execute the enhancement protocol with the following context:

```
CONTEXT:
- Target Component: $ARGUMENTS
- Mode: REFACTORING AND OPTIMIZATION
- Output: Refactored source code and performance metric delta.

EXECUTION STEPS:
1. Parse target codebase and associated configurations (pom.xml, application.yml).
2. Identify bottlenecks (e.g., N+1 JPA queries, synchronous blocking I/O).
3. Implement structural enhancements (e.g., Entity Graphs, CompletableFuture, Resilience4j).
4. Validate that existing unit tests and contracts remain unbroken.
```

---

## Expected Output

| Deliverable | Location |
|-------------|----------|
| Refactored Code | Target directories |
| Enhancement Log | Terminal |

---

## After Enhancement

```
[OK] Enhancement applied successfully.
[INFO] Resolved 2 N+1 query bottlenecks. Implemented Circuit Breaker on external API call.

Next steps:
- Run `/test` to ensure no regression was introduced.
```

---

## Usage

```
/enhance OrderRepository to resolve N+1 fetching issues
/enhance PaymentGatewayClient to include retry and circuit breaker logic
```