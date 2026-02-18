---
description: Analyze Git history, parse diffs, and structure atomic version control snapshots. No push permitted.
---

# /commit - Atomic Version Control Mode

$ARGUMENTS

---

## 🔴 CRITICAL RULES

1. **NO PUSH EXECUTION** - This mode strictly structures and outputs commit commands. User executes them manually.
2. **Historical Alignment** - Read the last 20 commits to extract and mimic repository formatting conventions.
3. **Atomic Grouping** - Strictly isolate changes. Maximum 2 to 8 files per commit. No monolithic commits.
4. **Standardization** - Apply Conventional Commits (feat:, fix:, refactor:, etc.) if historical alignment is inconclusive.

---

## Task

Execute the version control protocol with the following context:

```
CONTEXT:
- Target Diffs: $ARGUMENTS
- Mode: COMMIT STRUCTURING ONLY
- Output: Executable git commands and structural report.

EXECUTION STEPS:
1. Execute `git log -n 20` logic to parse existing standards.
2. Analyze current file changes.
3. Segment files into logical, atomic groupings (1-4 files max).
4. Generate precise `git add` and `git commit -m "..."` sequences.
5. Output sequences to the user.
```

---

## Expected Output

| Deliverable | Location |
|-------------|----------|
| Commit Commands | Terminal (Code block) |
| Grouping Report | Terminal |

---

## After Committing

```
[OK] Atomic commits structured based on historical convention.

Next steps:
- Copy and execute the provided git commands in your terminal.
- Push to remote repository manually.
```

---
# Example

```
<scope>(<file, task name or module changes>): <details>
```
Example commit:
- feat(user): implement user service logic
- chore(logo): update project logo
- infra(monitor): update prometheus targets and fix logstash connectivity

Note Conventional Commits specification:
* `feat` A new feature.
* `fix` A bug fix.
* `refactor` A code change that neither fixes a bug nor adds a feature.
* `perf` A code change that improves performance.
* `test` Adding missing tests or correcting existing tests.
* `chore` Changes to the build process or auxiliary tools and libraries.
* `infra` Update project infrastructure (change Dockerfile, DockerCompose, move folder, or affect the code flow/logic).
* `style` Edit something without affecting the code structure or workflow. Adjust file and code locations.

---

---

## Usage

```
/commit Parse current workspace diffs and generate atomic commits
/commit Structure commits for the newly added authentication module
```