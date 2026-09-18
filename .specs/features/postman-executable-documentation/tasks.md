# Postman Executable Documentation Tasks

## Execution Protocol

Implement these tasks with the `tlc-spec-driven` skill and its Execute flow. Each completed task requires its gate, status update and one atomic local commit. No remote deployment, push or AWS action is authorized.

**Status:** In Progress

## Test Coverage Matrix

> Generated from `AGENTS.md`, `README.md`, `postman/`, `.github/workflows/ci.yml` and the existing Postman collection. Guidelines found: `AGENTS.md`, `README.md`, `.github/workflows/ci.yml`.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Postman collection | static contract validation | Every spec scenario has a named request and exact `pm` status/content-type assertion where defined | `postman/*.postman_collection.json` | `powershell -File scripts/validate-postman.ps1` |
| Postman environments | static contract validation | Required variables exist; local values are routable; AWS secrets and endpoint are empty | `postman/*.postman_environment.json` | `powershell -File scripts/validate-postman.ps1` |
| Postman guide | documentation review | Import, runtime selection, AWS prerequisites and execution order are explicit | `postman/README.md` | `powershell -File scripts/validate-postman.ps1` |
| Validator | executable documentation test | Reject each structural violation declared in POSTMAN-07 | `scripts/validate-postman.ps1` | `powershell -File scripts/validate-postman.ps1` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Collection, environment or guide task | `powershell -File scripts/validate-postman.ps1` |
| Full | Validator task | `powershell -File scripts/validate-postman.ps1` |
| Build | Feature closure | `powershell -File scripts/validate-postman.ps1` |

## Execution Plan

```text
Phase 1: T01 -> T02 -> T03 -> T04
Phase 2: T04 -> T05 -> T06
```

## Task Breakdown

### T01: Define the executable Postman contract

**Status:** Complete

**What:** Record the Local/AWS collection contract, safe environment rules and traceability.
**Where:** `.specs/features/postman-executable-documentation/spec.md`
**Depends on:** None
**Requirement:** POSTMAN-01 to POSTMAN-07
**Tests:** static contract validation
**Gate:** `python C:\Users\vinic\.codex\skills\tlc-spec-driven\scripts\validate_spec.py .specs/features/postman-executable-documentation/spec.md` and `python C:\Users\vinic\.codex\skills\tlc-spec-driven\scripts\validate_tasks.py .specs/features/postman-executable-documentation/tasks.md`
**Commit:** `docs(postman): define executable collection contract`

### T02: Build the documented Postman collection

**Status:** Complete

**What:** Replace the AWS-only collection with a safe, documented collection containing Local and AWS flows and spec-derived Postman assertions.
**Where:** `postman/flash-booking-aws.postman_collection.json`
**Depends on:** T01
**Requirement:** POSTMAN-01, POSTMAN-02, POSTMAN-03, POSTMAN-04, POSTMAN-05, POSTMAN-06
**Tests:** static contract validation
**Gate:** `powershell -NoProfile -Command "Get-Content -LiteralPath 'postman/flash-booking-aws.postman_collection.json' -Raw | ConvertFrom-Json | Out-Null"`
**Commit:** `test(postman): add executable local and aws flows`

### T03: Add the Local Postman environment

**Status:** Pending

**What:** Add importable Local environment values for the split command/query APIs and non-personal test customer.
**Where:** `postman/flash-booking.local.postman_environment.json`
**Depends on:** T02
**Requirement:** POSTMAN-01, POSTMAN-02, POSTMAN-03
**Tests:** static contract validation
**Gate:** Quick
**Commit:** `test(postman): add local environment`

### T04: Add the safe AWS Postman environment

**Status:** Pending

**What:** Add importable AWS variables with blank endpoint, temporary credentials and verified-recipient placeholders.
**Where:** `postman/flash-booking.aws.postman_environment.json`
**Depends on:** T03
**Requirement:** POSTMAN-04, POSTMAN-05
**Tests:** static contract validation
**Gate:** Quick
**Commit:** `test(postman): add safe aws environment`

### T05: Document Postman execution

**Status:** Pending

**What:** Explain import, runtime selection, request order, expected results and AWS SigV4 setup.
**Where:** `postman/README.md`
**Depends on:** T04
**Requirement:** POSTMAN-01, POSTMAN-04, POSTMAN-06
**Tests:** static contract validation
**Gate:** Quick
**Commit:** `docs(postman): document local and aws execution`

### T06: Add structural Postman validation

**Status:** Pending

**What:** Add an executable repository check for the collection and both environments, then record feature validation evidence.
**Where:** `scripts/validate-postman.ps1`
**Depends on:** T05
**Requirement:** POSTMAN-05, POSTMAN-06, POSTMAN-07
**Tests:** static contract validation
**Gate:** Full
**Commit:** `test(postman): validate executable documentation`

## Phase Execution Map

```text
Phase 1: T01 -> T02 -> T03 -> T04
Phase 2: T04 -> T05 -> T06
```

## Diagram-Definition Cross-Check

| Task | Depends On | Diagram Shows | Status |
| --- | --- | --- | --- |
| T01 | None | None | ✅ Match |
| T02 | T01 | T01 -> T02 | ✅ Match |
| T03 | T02 | T02 -> T03 | ✅ Match |
| T04 | T03 | T03 -> T04 | ✅ Match |
| T05 | T04 | T04 -> T05 | ✅ Match |
| T06 | T05 | T05 -> T06 | ✅ Match |

## Test Co-location Validation

| Task | Code Layer Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T01 | Specification | static contract validation | static contract validation | ✅ OK |
| T02 | Collection | static contract validation | static contract validation | ✅ OK |
| T03 | Local environment | static contract validation | static contract validation | ✅ OK |
| T04 | AWS environment | static contract validation | static contract validation | ✅ OK |
| T05 | Postman guide | documentation review | static contract validation | ✅ OK |
| T06 | Validator | executable documentation test | static contract validation | ✅ OK |
