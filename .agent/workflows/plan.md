---
description: Construct sequential execution roadmap, simulate edge cases, and define mitigations. No code execution.
---

# /plan - Strategic Execution Planning Mode

$ARGUMENTS

---

## 🔴 CRITICAL RULES

1. **NO CODE WRITING** - This phase strictly produces sequential task lists and structural blueprints.
2. **Dependency Ordering** - Tasks must be structured as a Primary List, sorted by topological execution order.
3. **Preemptive Simulation** - Edge case simulation is mandatory for all primary tasks.
4. **Traceability** - Map tasks directly back to brainstorm outcomes or user keywords.

---

## Task

Execute the planning protocol with the following context:

```
CONTEXT:
- Target Objective: $ARGUMENTS
- Mode: PLANNING ONLY (No code generation)
- Output: docs/PLAN-{task-slug}.md

EXECUTION STEPS:
1. Strategic Breakdown: Translate the objective into modular backend phases.
2. Primary Task List: Enumerate actionable engineering steps (e.g., Schema design, API definition, Service layer).
3. Edge Case Simulation: Identify potential I/O failures, race conditions, or data anomalies per task.
4. Mitigation Strategy: Define technical countermeasures for each simulated edge case.
```

---

## Expected Output

| Deliverable | Location |
|-------------|----------|
| Execution Plan | docs/PLAN-{task-slug}.md |
| Edge Case Matrix | Inside plan file |

---

## After Planning

```
[OK] Plan generated: docs/PLAN-{task-slug}.md

Next steps:
- Review task hierarchy and mitigations.
- Run `/create` to initiate codebase implementation.
```

---

## Usage

```
/plan Migrate monolithic user authentication to JWT microservice
/plan Integrate pgvector for semantic search capabilities
```