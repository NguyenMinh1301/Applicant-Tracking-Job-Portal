# Documentation Guide for VietRecruit

## Overview

This document outlines the standards, structure, and guidelines for writing and maintaining technical documentation within the `docs/` directory of the VietRecruit project. All documentation must strictly adhere to these standards to ensure clarity, maintainability, and professional quality.

## Diátaxis Framework

All documentation generated or written for this project must follow the [Diátaxis framework](https://diataxis.fr/), mapping each document to one of four specific quadrants:

- **Tutorials:** Learning-oriented. Step-by-step practical lessons for beginners.
- **How-To Guides:** Goal-oriented. Sequential steps to solve a specific problem for practitioners (e.g., `runbooks/`).
- **Reference:** Information-oriented. Factual, highly structured API or system data for quick lookup (e.g., `api/`).
- **Explanation:** Understanding-oriented. Architectural concepts and the "why" behind the system (e.g., `adr/`, `architecture/`, `features/`).

## Directory Structure

The `docs/` directory is organized systematically. Place new documents in the appropriate subfolder:

```text
docs/
├── adr/               # Architecture Decision Records (Why decisions were made)
├── api/               # API references, error handling, and payload schemas
├── architecture/      # High-level system design, C4 diagrams, and resilience strategies
├── features/          # Deep dives into specific domain modules (e.g., auth, job)
└── runbooks/          # Operational how-to guides (e.g., local development setup)
```

## Writing Standards (Strict Enforcement)

1.  **Professional Style Standard:**
    - **Language:** Formal Technical English. Zero fluff. No conversational fillers (e.g., avoid "Let's dive in", "This is just a quick guide").
    - **Active Voice & Second Person:** Use "you" instead of "we", "I", or "the user". Write in the active voice (e.g., "The service validates input" NOT "The input is validated by the service").
    - **Terse & Direct:** Put key information first.
    - **Conditional Precedence:** Put conditions _before_ instructions (e.g., "If using Docker, run `x`", NOT "Run `x` if using Docker").

2.  **Formatting & Hierarchy:**
    - Use sentence-case for headings.
    - Use **bold** for UI elements or strong emphasis.
    - Use `inline-code` for variables, parameters, file paths, and class names.
    - Create a mandatory **Overview** section at the top of each document.

3.  **Code & Diagrams:**
    - **Code Context:** Do not hallucinate features. Ingest or reference actual configuration/source files before drafting.
    - **Code Snippets:** Embed critical code snippets directly into the docs (e.g., YAML configurations, complex entity relationships). Ensure examples are minimal and practical.
    - **Diagrams:** Use [MermaidJS](https://mermaid.js.org/) syntax for all sequence, state, and flow diagrams.

## Contribution Workflow

When executing the documentation generation protocol (e.g., using the `/docs` agent workflow):

1.  **Context Ingestion:** Read the relevant source code, configuration, or `/plan` file to extract factual behavior.
2.  **Audience & Type Selection:** Identify the target audience and assign the strict Diátaxis type.
3.  **Structural Drafting:** Chunk information into small, manageable units. Include necessary diagrams and code snippets.
4.  **Formatting Verification:** Apply technical writing syntax rules before finalizing the document.
