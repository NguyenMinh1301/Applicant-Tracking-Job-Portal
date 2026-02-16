---
description: Trace telemetry, identify root causes, and generate patches to resolve system anomalies.
---

# /debug - Diagnostic and Remediation Mode

$ARGUMENTS

---

## 🔴 CRITICAL RULES

1. **Root Cause Analysis (RCA)** - Remediation must address the source, not the symptom.
2. **Telemetry Ingestion** - Require stack traces, logs, or metric anomalies to initiate tracing.
3. **Regression Prevention** - Patches must strictly resolve the anomaly without impacting adjacent logic.

---

## Task

Execute the diagnostic protocol with the following context:

```
CONTEXT:
- Error Context: $ARGUMENTS
- Mode: TRACING AND PATCHING
- Output: RCA report and patched source code.

EXECUTION STEPS:
1. Parse error logs and cross-reference with configuration (application.yml, .env.example).
2. Isolate the failure point within the codebase.
3. Determine logical flaw, misconfiguration, or unhandled state.
4. Generate precise code patch to rectify the state.
5. Provide RCA documentation.
```

---

## Expected Output

| Deliverable | Location |
|-------------|----------|
| RCA Report | Terminal |
| Patched Code | Target directories |

---

## After Debugging

```
[OK] Vulnerability traced and patched.
[RCA] NullPointerException at ProductService.java:45 due to unhandled empty Optional from Repository.

Next steps:
- Run `/test` to verify the patch resolves the issue.
- Run `/commit` to stage the fix.
```

---

## Usage

```
/debug java.sql.SQLException: connection timeout during heavy load
/debug NullPointerException in TokenFilter validation logic
```