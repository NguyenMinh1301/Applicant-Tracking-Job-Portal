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

## Usage

```
/commit Parse current workspace diffs and generate atomic commits
/commit Structure commits for the newly added authentication module
```