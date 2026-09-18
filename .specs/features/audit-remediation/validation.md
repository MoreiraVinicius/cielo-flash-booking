# Audit Remediation Validation

**Date**: 2026-09-17
**Spec**: `.specs/features/audit-remediation/spec.md`
**Diff range**: remediation history from `c0a2eaf` through this validation
**Verifier**: independent sub-agent (author != verifier)

---

## Verdict

**PASS (LOCAL); REMOTE CI PENDING.** The unit, PostgreSQL/Testcontainers, and Terraform gates pass, and the request-limit mutation is killed. The Docker Desktop engine is healthy; the earlier Testcontainers failure was caused by the restricted execution context lacking access to the user's Docker named pipe and profile. The full gate was rerun through the accessible `desktop-linux` context. The only remaining evidence gap is an execution of the GitHub Actions workflow after push/PR.

## Task Completion

| Task | Status | Verification note |
| --- | --- | --- |
| T01 | Verified locally | PostgreSQL-backed integration suite passed. |
| T02 | Verified locally | Unit tests now assert replay header and unexpected-error log correlation. |
| T03 | Verified locally | Scheduler is unit-verified; lease and cleanup ran against PostgreSQL. |
| T04 | Verified locally | `mvn verify` passed. |
| T05 | Verified locally | Format, module tests, and root validation passed. |
| T06 | Partial | Workflow/docs are locally verified; no GitHub Actions execution is available locally. |

## Spec-Anchored Acceptance Criteria

| Requirement | Expected outcome | Evidence | Result |
| --- | --- | --- | --- |
| REM-01.1 | Persisted reservation timestamps use PostgreSQL time | `JdbcReservationPersistenceAdapter.java:32-34`; `CreateReservationServiceTest.java:43`; 63-test Testcontainers gate | ✅ PostgreSQL integration gate passed |
| REM-01.2 | Expiry scan uses stable statement timestamp | `JdbcReservationPersistenceAdapter.java:57-65`; 63-test Testcontainers gate | ✅ PostgreSQL integration gate passed |
| REM-01.3 | Reused email retains the original customer identity | `JdbcReservationPersistenceAdapter.java:69-85`; `ReservationControllerIT.java:143-167`; 63-test Testcontainers gate | ✅ PostgreSQL integration gate passed |
| REM-01.4 | Keys over 128 return 400 before DB and DB enforces 128 | `IdempotencyCommand.java:13-20`; `V2__bound_idempotency_key.sql:1-3`; `IdempotencyControllerIT.java:239-248`; `InitialSchemaIT.java:100-104`; 63-test Testcontainers gate | ✅ PostgreSQL integration gate passed |
| REM-02.1 | Bodies over 64 KiB, including unknown length, return 413 before controller | `RequestPayloadLimitFilter.java:39-48`; `RequestPayloadLimitFilterTest.java:14-27` asserts 413 | ✅ Unit-verified (unknown-length branch) |
| REM-02.2 | Replay body and response header carry current correlation ID | `ProblemResponseFactory.java:52-60`; `IdempotencyControllerIT.java:195-197` asserts body and `X-Correlation-ID` header | ✅ PostgreSQL integration gate passed |
| REM-02.3 | Unexpected error logs throwable + correlation ID without exposing internals | `ApiExceptionHandler.java:73-85`; `ApiExceptionHandlerIT.java:84-104` captures the throwable and correlation ID while asserting the response excludes its message | ✅ Unit-verified |
| REM-02.4 | Healthy miss bypasses outage bulkhead; cache success clears failures | `GetEventService.java:42-76`; `CacheFailureCircuit.java:21-33`; `GetEventServiceTest.java:72-90` | ✅ Unit-verified |
| REM-03.1 | Independent scheduled jobs can use separate threads | `application.yml:12-15`; `SchedulingConfigurationTest.java:22-41` schedules two blocking tasks and observes both start | ✅ Unit-verified |
| REM-03.2 | Reconciler defaults to 1,000 rows every 500 ms | `application.yml:40-44`; `ExpirationReconciler.java:26-29`; `ExpirationReconcilerTest.java:16-27` asserts batch 1000 | ⚠️ Fixed-delay value is static evidence, not a scheduler assertion |
| REM-03.3 | Provider call occurs outside transaction and delivery state uses short transitions | `ReservationEmailService.java:29-62`; `JdbcNotificationDeliveryStore.java:23-47`; `ReservationEmailServiceTest.java:34-50`; 63-test Testcontainers gate | ✅ Unit and PostgreSQL integration gates passed |
| REM-03.4 | Bounded terminal cleanup is referentially safe | `OperationalDataCleaner.java:25-31`; `JdbcNotificationDeliveryStore.java:86-100`; `JdbcOutboxEventStore.java:55-71`; `OperationalDataCleanerTest.java:23-24`; 63-test Testcontainers gate | ✅ PostgreSQL integration gate passed |
| REM-04.1 | Event persistence is JDBC only; no JPA dependency | `pom.xml:45-50`; `EventPersistenceAdapter.java:12-48` | ✅ `mvn verify` passed |
| REM-04.2 | SQL owns transitions; unused mutable transition methods are absent | `Reservation.java:7-76`; `ReservationTest.java:40-45` | ✅ Unit-verified |
| REM-04.3 | Image build runs tests before packaging | `Dockerfile:10` runs `./mvnw -q package` | ✅ Static contract; Docker build not run locally |
| REM-05.1 | Demo declares S3 backend and runbook supports it | `infra/environments/demo/versions.tf:1-5`; `docs/demo-runbook.md:69-86` | ✅ `terraform init -backend=false` + validate passed |
| REM-05.2 | Runbook sequences ECR, immutable image push, then services | `docs/demo-runbook.md:87-109` | ✅ Static/runbook review |
| REM-05.3 | ECS health checks and API scaling above one | `infra/modules/compute/main.tf:221-320,419-470`; `compute.tftest.hcl:43-48` | ✅ Terraform module test passed |
| REM-05.4 | Relevant alarms target configured SNS topic | `infra/modules/compute/main.tf:471-488`; `data-plane/main.tf:111-171`; `edge-observability/main.tf:491-541`; module test assertions in `*.tftest.hcl:38-53,103` | ✅ Terraform module tests passed |
| REM-05.5 | No unpublished API Gateway Throttle metric | `edge-observability/main.tf:516,560`; `edge-observability.tftest.hcl:103` asserts `4XXError` | ✅ Terraform module test passed |
| REM-06.1 | GitHub Actions runs Java and Terraform gates | `.github/workflows/ci.yml:1-54` | ⚠️ Workflow exists; no remote CI execution evidence |
| REM-06.2 | README/runbook separate evidence and do not treat 503 bursts as capacity success | `README.md:174-190`; `docs/demo-runbook.md:69-109`; `scripts/validate-readme.ps1:1-31` | ✅ `scripts/validate-readme.ps1` passed |

**Spec-anchored status**: all locally executable acceptance behavior, including PostgreSQL/Testcontainers outcomes, is verified. Remote CI execution remains unavailable on this host.

## Discrimination Sensor

Baseline real worktree porcelain was `?? .tmp/`.

| Mutation | File:line | Command and result | Killed? |
| --- | --- | --- | --- |
| Permit one byte over the limit by changing `body.length > maximumRequestBodySize` to `body.length > maximumRequestBodySize + 1` | scratch `RequestPayloadLimitFilter.java:44` | In isolated worktree, `mvn -Dtest=RequestPayloadLimitFilterTest test -q` failed at `RequestPayloadLimitFilterTest.java:27`: expected 413, got 200 | ✅ Killed |

Scratch worktree: `C:\Users\vinic\AppData\Local\Temp\cielo-audit-remediation-sensor` (removed after run). The real worktree porcelain remained `?? .tmp/`; the sensor did not alter production files.

## Gate Check

| Gate | Result |
| --- | --- |
| `mvn verify -q` | ✅ 50 tests, 0 failures, 0 errors, 0 skipped |
| `mvn clean verify -Pintegration` | ✅ 113 tests total: 50 unit, 63 integration, 0 failures, 0 errors, 0 skipped. Docker Desktop 29.7.2 / Testcontainers 1.21.0 connected successfully. |
| `terraform fmt -check -recursive` | ✅ Passed |
| `terraform test` for network, compute, data-plane, edge-observability | ✅ 1/1 passed in each module |
| `terraform -chdir=infra/environments/demo init -backend=false -input=false && validate` | ✅ Passed |
| `terraform -chdir=infra/bootstrap init -backend=false -input=false && validate` | ✅ Passed |

## Edge Cases

- [x] Unknown body length is counted; the mutant sensor confirmed the 413 assertion.
- [x] Two simultaneous workers / valid DB lease: exercised by the PostgreSQL/Testcontainers integration suite.
- [x] Ambiguous provider result remains at-least-once: `ReservationEmailService.java:55-62` releases retry; limitation documented in `README.md:78-88`.
- [x] SNS subscription confirmation is documented as external: `docs/demo-runbook.md:94`.

## Ranked Gaps

1. **Minor — CI workflow has no execution evidence.** REM-06.1 must run on GitHub Actions after push/PR.

## Code Quality

| Check | Status |
| --- | --- |
| Scope is constrained to remediation | ✅ |
| Existing architecture and contracts preserved | ✅ |
| Java persistence stack simplified to JDBC | ✅ |
| Static and unit gates pass | ✅ |
| Integration claims limited to actual evidence | ✅ |
| All locally executable acceptance criteria fully proven | ✅ |

## Summary

The remediation has a passing local unit/Terraform/PostgreSQL baseline plus a killed behavioral mutant. It is **locally verified**; GitHub Actions must still execute after push/PR before claiming end-to-end delivery-pipeline evidence. The Docker issue was an execution-context permission mismatch, and the resulting dated fixture was corrected to use PostgreSQL time so cancellation is tested before its real deadline.
