# Flash Sale Window Tasks

## Execution Protocol

Implement these tasks with the `tlc-spec-driven` skill and its Execute flow. Each completed task requires its gate, status update and one atomic local commit. No remote deployment, push or AWS action is authorized.

**Design:** `.specs/features/flash-sale-window/design.md`
**Status:** Complete

## Test Coverage Matrix

> Generated from `AGENTS.md`, `README.md`, `.github/workflows/ci.yml`, `pom.xml`, and existing unit/integration tests. Domain rules map one-to-one to acceptance criteria; JDBC, migration and HTTP contracts run against PostgreSQL/Testcontainers.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Event domain and creation service | unit | Every valid and invalid temporal branch | `src/test/java/com/cielo/flashbooking/domain/event/*Test.java`, `event/application/*Test.java` | `./mvnw.cmd test` |
| JDBC inventory and schema | integration | Database checks and start/end boundaries with real PostgreSQL | `src/test/java/com/cielo/flashbooking/**/*IT.java` | `./mvnw.cmd verify -Pintegration` |
| HTTP event/reservation contract | integration | Happy, invalid, before-start, end-boundary and replay outcomes | `src/test/java/com/cielo/flashbooking/event/controller/*IT.java`, `reservation/controller/*IT.java` | `./mvnw.cmd verify -Pintegration` |
| Cache adapter | integration | Persisted window fields survive cache miss and hit | `src/test/java/com/cielo/flashbooking/event/controller/*IT.java` | `./mvnw.cmd verify -Pintegration` |
| Documentation and Postman | review | All five endpoint examples remain coherent with the changed event contract | `README.md`, `docs/`, `postman/` | `powershell -File scripts/validate-readme.ps1` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Domain and service task | `./mvnw.cmd test` |
| Full | JDBC, migration, HTTP or cache task | `./mvnw.cmd clean verify -Pintegration` |
| Build | Documentation and feature closure | `./mvnw.cmd clean verify -Pintegration` |

## Execution Plan

```text
Phase 1: T01 -> T02
Phase 2: T02 -> T03 -> T04
Phase 3: T04 -> T05 -> T06
```

## Task Breakdown

### T01: Specify the sale-window contract

**Status:** Complete

**What:** Record the feature specification, context and design, then align the existing demo/high-load specifications with the shared event-window contract.
**Where:** `.specs/features/flash-sale-window/`
**Depends on:** None
**Requirement:** WINDOW-01 to WINDOW-10
**Tests:** review
**Gate:** Build
**Commit:** `docs(spec): define flash sale window contract`

### T02: Persist valid event windows

**Status:** Complete

**What:** Add the backwards-compatible event columns/checks and make event creation/persistence use PostgreSQL creation time and preserve the optional values.
**Where:** `src/main/resources/db/migration/`
**Depends on:** T01
**Requirement:** WINDOW-01, WINDOW-04, WINDOW-05, WINDOW-07
**Tests:** unit + integration
**Gate:** Full
**Commit:** `feat(event): persist flash sale windows`

### T03: Expose and cache the event window

**Status:** Complete

**What:** Extend request, response, domain and cache mappings so event reads and writes expose the optional window consistently.
**Where:** `src/main/java/com/cielo/flashbooking/event/`
**Depends on:** T02
**Requirement:** WINDOW-01, WINDOW-02, WINDOW-03, WINDOW-04, WINDOW-05, WINDOW-07, WINDOW-08
**Tests:** unit + integration
**Gate:** Full
**Commit:** `feat(event): expose flash sale windows`

### T04: Guard reservations by the sale window

**Status:** Complete

**What:** Add start/end predicates to the atomic inventory decrement and prove the result has no partial effects or changed availability outside the window.
**Where:** `src/main/java/com/cielo/flashbooking/adapter/out/persistence/inventory/`
**Depends on:** T03
**Requirement:** WINDOW-02, WINDOW-03, WINDOW-06, WINDOW-09, WINDOW-10
**Tests:** integration
**Gate:** Full
**Commit:** `feat(reservation): enforce flash sale window`

### T05: Update runnable clients and documentation

**Status:** Complete

**What:** Update README, runbook, Postman collection, smoke flow and architecture documentation for the event-window contract.
**Where:** `README.md`
**Depends on:** T04
**Requirement:** WINDOW-01, WINDOW-03, WINDOW-04, WINDOW-07
**Tests:** review
**Gate:** Build
**Commit:** `docs: document flash sale windows`

### T06: Verify the feature

**Status:** Complete

**What:** Reconcile traceability, run the feature validation and record a discrimination sensor against the window predicates.
**Where:** `.specs/features/flash-sale-window/validation.md`
**Depends on:** T05
**Requirement:** WINDOW-01 to WINDOW-10
**Tests:** unit + integration
**Gate:** Build
**Commit:** `test: validate flash sale window`

## Phase Execution Map

```text
Phase 1: T01 -> T02
Phase 2: T02 -> T03 -> T04
Phase 3: T04 -> T05 -> T06
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
| T01 | Specifications | review | review | ✅ OK |
| T02 | Schema/domain/persistence | unit + integration | unit + integration | ✅ OK |
| T03 | Domain/HTTP/cache | unit + integration | unit + integration | ✅ OK |
| T04 | JDBC reservation authorization | integration | integration | ✅ OK |
| T05 | Documentation/clients | review | review | ✅ OK |
| T06 | Validation | unit + integration | unit + integration | ✅ OK |
