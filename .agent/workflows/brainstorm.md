---
description: Synthesize user keywords into logical domains and propose architectural solutions. No code execution.
---

# /brainstorm - Ideation and Deduction Mode

$ARGUMENTS

---

## 🔴 CRITICAL RULES

1. **NO CODE WRITING** - This phase is strictly for conceptualization, logic mapping, and solution discovery.
2. **Context Aggregation** - Extract core domain entities and business requirements from user input.
3. **Multi-Option Output** - Propose at least two distinct architectural or structural approaches.
4. **Constraint Awareness** - Factor in Java backend capabilities and typical enterprise constraints.

---

## Task

Execute the brainstorm protocol with the following context:

```
CONTEXT:
- User Input: $ARGUMENTS
- Mode: BRAINSTORM ONLY (No code generation)
- Output: Terminal report detailing logical structures and proposed solutions.

EXECUTION STEPS:
1. Keyword Synthesis: Isolate primary business/technical parameters.
2. Logical Deduction: Map parameters to system behaviors, defining Bounded Contexts.
3. Solution Discovery: Evaluate appropriate design patterns, data models, or algorithms.
4. Proposal Formulation: Detail trade-offs (Performance vs. Complexity) for each approach.
```

---

## Expected Output

| Deliverable | Location |
|-------------|----------|
| Keyword Synthesis | Terminal |
| Domain Logic Map | Terminal |
| Solution Proposals | Terminal |

---

## After Brainstorming

```
[OK] Brainstorm complete.

Next steps:
- Select a preferred solution approach.
- Run `/plan` to generate an execution roadmap based on the selected approach.
```

---

## Usage

```
/brainstorm Implement a scalable notification system for multiple channels
/brainstorm Distributed caching strategy for high-read e-commerce catalog
```