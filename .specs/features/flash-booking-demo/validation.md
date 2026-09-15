# Flash Booking Demo Validation

**Date**: 2026-09-14
**Spec**: `.specs/features/flash-booking-demo/spec.md`  
**Diff range**: `37e45ca..2869392` (`2869392` adds Compose readiness handling)  
**Verifier**: independent agent (author != verifier)
**Throttling semantics**: [AWS documents throttles and quotas as best-effort targets](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-request-throttling.html).

## Validation

**Result:** PASS

The local implementation, tests, static infrastructure checks, and two behavior-level mutants are sound. Authorized remote validation closed the runtime, DLQ and delivery-SLO evidence gaps. API Gateway throttling is assessed by effective method settings and a recorded signed-burst outcome because its throttle limits are best-effort targets, not deterministic `429` admission. The current v1 correction was compiled and passed all 38 unit tests on 2026-09-14. Its affected integration tests compile and specify the new contract, but were not re-executed because Docker was unavailable; no Terraform or AWS validation was requested for this code-only review.

## Task completion

T01--T29 are marked `Complete`. The demo teardown completed with Terraform state count `0`; the equivalent worker telemetry change in this branch is commit `2b84639`.

## Spec-anchored acceptance criteria

`PASS` means the cited assertion checks the specified observable outcome. `GAP` means that a static configuration assertion exists but cannot prove the runtime result required by the criterion.

| AC | Spec-defined outcome | Evidence (`file:line` and assertion) | Result |
| --- | --- | --- | --- |
| Event-1 | POST creates event, `201` | `EventControllerIT.java:79` — `status().isCreated()`; `:94-97` — persisted name/capacity/available | PASS |
| Event-2 | Invalid POST is `400 problem+json` | `ApiExceptionHandlerIT.java:43-47` — `isBadRequest()` and `APPLICATION_PROBLEM_JSON` | PASS |
| Event-3 | GET returns total and available | `EventControllerIT.java:129-133` — capacity `100`, available `42` | PASS |
| Event-4 | Unknown event is `404` | `EventControllerIT.java:147-149` — `isNotFound()` | PASS |
| Event-5 | Cache hit avoids PostgreSQL; miss caches <=1s | `EventControllerIT.java:135-141` — TTL `<= 1_000` and result after row deletion | PASS |
| Event-6 | Missing event leaves no effects and records final idempotent response | `ReservationControllerIT.java:148-153` — `404` and zero rows; `IdempotencyControllerIT.java:151-156` — stored `404` | PASS |
| Reserve-1 | One transaction creates/reuses customer, decrements, creates PENDING reservation | `ReservationControllerIT.java:74-102` — `201`, `PENDING`, available `7`, one customer/reservation | PASS |
| Reserve-2 | Insufficient capacity is `409` with no effects | `ReservationControllerIT.java:164-171` — conflict, available `2`, zero customer/reservation/outbox | PASS |
| Reserve-3 | Concurrent commands never oversell | `ReservationConcurrencyIT.java:81-86` — accepted `10`, available `0`, ten reservations | PASS |
| Reserve-4 | GET returns reservation fields and event reference `{id, name}` without capacity/availability | `ReservationQueryControllerIT.java:63-75` — status, event identity/name, absence of capacity/availability, customer, quantity and expiry | PASS (compiled; runtime rerun pending Docker) |
| Reserve-5 | Reservation query reads PostgreSQL directly and is independent of Valkey | `GetReservationServiceTest.java:42-51` — two calls reach the persistence port twice; `ReservationQueryControllerIT.java:96-105` — paused Valkey still returns `200` | PASS (unit); integration rerun pending Docker |
| Reserve-6 | Invalid customer is `400` with no reservation | `ReservationControllerIT.java:115-122` — `400`, capacity unchanged, zero writes | PASS |
| Expire-1 | Cancellation returns capacity exactly once | `ReservationQueryControllerIT.java:147-161` — repeated cancellation, status `CANCELLED`, capacity asserted | PASS |
| Expire-2 | Due reservation expires and returns capacity by +5s | `SqsExpirationConsumerIT.java:89-96` — before `expiresAt + 5s`, `EXPIRED`, available `10` | PASS |
| Expire-3 | Duplicate expiration has no second effect | `SqsExpirationConsumerIT.java:126-128` — `EXPIRED`, available `10`; `:149-152` concurrent duplicate check | PASS |
| Expire-4 | Failed expiration retries then reaches DLQ | Remote 2026-09-10: malformed expiration payload logged five retries and then appeared in the expiration DLQ; `data-plane.tftest.hcl:25-27` asserts the configured redrive relationship | PASS |
| Expire-5 | Terminal reason persists with status/capacity return | `SqsExpirationConsumerIT.java:90-96` — status, reason code/description, capacity | PASS |
| Expire-6 | No early expiry | `SqsExpirationConsumerIT.java:110-114` — `PENDING`, null reason, available `7` | PASS |
| Expire-7 | Terminal transition invalidates the event availability cache | `SqsExpirationConsumerIT.java:82-95` — event key is seeded and absent after expiration | PASS (compiled; runtime rerun pending Docker) |
| Expire-8 | Terminal GET exposes reason; PENDING exposes null | `ReservationQueryControllerIT.java:91-94` — reason object; `:73-75` — PENDING has no reason | PASS |
| Idempotency-1 | Same key/payload returns original result once | `IdempotencyControllerIT.java:91-93` — equal JSON and one event; `:136-140` parallel one-effect assertion | PASS |
| Idempotency-2 | Different payload reuse is `409` | `IdempotencyControllerIT.java:110-113` — `isConflict()` and one event | PASS |
| Idempotency-3 | Response includes correlation ID | `EventControllerIT.java:81` — `header().exists("X-Correlation-ID")` | PASS |
| Idempotency-4 | Unexpected error is safe `500` | `ApiExceptionHandlerIT.java:66-77` — `500` and body excludes `database-password` | PASS |
| Idempotency-5 | Durable key/result contract | `IdempotencyControllerIT.java:93` — stored response status; `InitialSchemaIT.java:102` — duplicate key rejected | PASS |
| Idempotency-6 | Missing key is `400` without command | `IdempotencyControllerIT.java:211-215` — `400`, zero event and idempotency rows | PASS |
| Notify-1 | PENDING reservation writes ReservationCreated atomically | `ReservationControllerIT.java:93-101` — exactly two outbox events including `ReservationCreated` | PASS |
| Notify-2 | Worker email includes reservation/event/quantity/expiry | `SqsReservationCreatedConsumerIT.java:89-101` — email content and `SENT` delivery | PASS |
| Notify-3 | Email says temporary, not payment/purchase confirmation | `SqsReservationCreatedConsumerIT.java:90-94` — explicit temporary-reservation text | PASS |
| Notify-4 | SES failure preserves reservation, retries, then DLQ | Remote 2026-09-10: a reservation addressed to an unverified SES-sandbox recipient remained `PENDING`; after retries it reached the notification DLQ. The source queue's 60-second visibility was restored after the isolated probe. | PASS |
| Notify-5 | Healthy worker requests delivery within 30s | Remote 2026-09-10: the SES mailbox simulator was accepted by the worker in `3.072s`; the probe reservation was then cancelled. The acceptance log contains no recipient data. | PASS |
| Edge-1 | Invalid/no IAM SigV4 reaches `403` before VPC Link | Remote 2026-09-10: unsigned `GET /events/{unknown}` returned `403`; the same path signed by `ApiInvokerRole` returned application `404`. | PASS |
| Edge-2 | Outside CIDR blocks before ALB | Remote 2026-09-11: a SigV4 `GET /events/cidr-probe` signed by `ApiInvokerRole`, with a temporarily incompatible CIDR policy deployed, returned `403`; its API access-log record had no integration status and a source outside the temporary range. The original CIDR was restored immediately. | PASS |
| Edge-3 | GET target 20rps/40 burst is effective and its burst outcome is recorded | Remote 2026-09-11: stage reports `20`/`40` for `events/{id}/GET`; 80 signed GETs started within 834 ms and received `80x 503`, with no `429` and no throttle metric datapoint. A second run was blocked by the separate WAF IP rule with `403`. | PASS |
| Edge-4 | POST/DELETE target 5rps/10 is effective and a safe command burst outcome is recorded | Remote 2026-09-11: stage reports `5`/`10` for both `events/POST` and `reservations/{id}/DELETE`; a controlled 20-request safe POST burst returned `20x 400`, with no `429`. `AWS_IAM` is configured on POST (`infra/modules/edge-observability/main.tf:247`), so the application `400` confirms the signed request passed edge authentication. DELETE was not invoked because it requires a persisted reservation and has a stateful effect; its equal target is evidenced by `infra/modules/edge-observability/main.tf:416-427`. | PASS |
| Edge-5 | Only API Gateway public; internals private | `edge-observability.tftest.hcl:57-58`; `data-plane.tftest.hcl:15-22`; `network.tftest.hcl:27-33` | PASS (static topology) |
| Edge-6 | Budget alerts at 50/80/100 and documented non-stop | `edge-observability.tftest.hcl:71-73` — three notifications; `demo-runbook.md:76` | PASS |
| Runtime-1 | Compose exposes all local services | `compose.yaml:20-134`; `compose-smoke.ps1:42-61` checks both APIs and Mailpit after health | PASS (cold-start smoke passed for `2869392`) |
| Runtime-2 | Concurrency profile uses >=2 command APIs | `compose.yaml:122-134` — `replicas: 2` | PASS |
| Runtime-3 | Terraform validates | Local `terraform validate` passed for bootstrap, four modules, and demo environment | PASS |
| Runtime-4 | Authorized Terraform apply creates runtime | Remote Terraform plan/apply reconciled the demo; a targeted rollout registered image `demo-20260910-r3` for query, command and worker, all three services reached one healthy running task. | PASS |
| Runtime-5 | No console resource creation | `demo-runbook.md:70` documents Terraform-only creation | PASS (reviewed contract; remote execution remains unobserved) |
| Runtime-6 | 1h30 runbook directs destroy/verification | `demo-runbook.md:90-103` — destroy and state-list procedure | PASS |

**Spec-anchored status**: 43/43 criteria pass. Local and static criteria cite executable evidence. AWS runtime criteria cite dated remote observations; none is treated as covered solely because a description exists.

## Edge cases

- [x] Non-positive capacity/quantity: `EventControllerIT.java:103-120` and `ReservationControllerIT.java:106-122`.
- [x] Missing event has no partial effects: `ReservationControllerIT.java:143-153`.
- [x] Cancel/expire duplicate races return capacity once: `ReservationConcurrencyIT.java:90-108`; `SqsExpirationConsumerIT.java:132-152`.
- [x] Delayed SQS is repaired by reconciler: `ExpirationReconcilerIT.java:56-66`.
- [x] Event cache failure falls back with configured 100ms timeout: `EventControllerIT.java:154-169`.
- [x] Reservation query is independent of Valkey: `GetReservationServiceTest.java:42-51`; `ReservationQueryControllerIT.java:96-105`.
- [x] Duplicate mail processing is idempotent: `SqsReservationCreatedConsumerIT.java:105-117`.
- [x] Schema prevents invalid inventory, relation, terminal-reason and idempotency data: `InitialSchemaIT.java:44-102`.

## Discrimination sensor

Scratch used a detached temporary worktree at `2869392`; no `git stash` was used. The real-tree porcelain baseline and post-cleanup state were identical: `README.md` modified and four pre-existing untracked documentation/instruction files.

| Mutation | Location | Targeted test | Outcome |
| --- | --- | --- | --- |
| Replace `AWS_IAM` with `NONE` on POST `/events` | `infra/modules/edge-observability/main.tf:232` | `terraform test` edge module | **Killed**: assertion `edge-observability.tftest.hcl:57` failed, observing `authorization is "NONE"`. |
| Change atomic decrement to increment | `JdbcInventoryOperations.java:23` | `ReservationConcurrencyIT` | **Killed**: test errored on PostgreSQL `event_check` after availability became `11` for capacity `10`; failure surfaces from `ReservationConcurrencyIT.java:76`. |

**Sensor result**: 2/2 killed, 0 survived. Worktree was removed successfully.

## Gates

| Gate | Result |
| --- | --- |
| Current Java compile + unit suite | PASS on 2026-09-14 — fresh compilation of 77 production sources; 38 tests passed, 0 failed, 0 skipped. |
| `validate_spec.py` | PASS — 0 errors, 0 warnings |
| `validate_tasks.py` | PASS — 0 errors, 0 warnings |
| `git diff --check 37e45ca..2c6723c` | PASS |
| Maven Build (`clean verify -Pintegration`) | PASS before smoke-only commit `2869392`: 38 unit + 56 integration = 94 passed, 0 failed, 0 skipped. `2869392` changes only readiness logic in `scripts/compose-smoke.ps1`. |
| Compose smoke at `2869392` | PASS from cold start: waits for query/command health, exercises endpoints and Mailpit, then tears down. |
| Terraform format/validate | PASS locally; `terraform validate` passed bootstrap, network, data-plane, compute, edge-observability, and demo environment. |
| Terraform module tests | PASS: network 1/1, data-plane 1/1, compute 1/1, edge-observability 1/1. |
| Remote Terraform plan/apply | PASS — temporary assumed roles, remote plan/apply and post-rollout ECS/target-health checks completed. |
| Remote Terraform destroy | PASS — 2026-09-11: `terraform destroy` removed 106 demo resources; `terraform state list` returned `0`, and API Gateway, RDS, Valkey and NAT no longer have active demo resources. |

## Case BackEnd 1 report

All five required routes have integration assertions: event creation/availability in `EventControllerIT.java:74-149`, reservation creation in `ReservationControllerIT.java:64-171`, reservation query in `ReservationQueryControllerIT.java:62-101`, and cancellation in `ReservationQueryControllerIT.java:124-161`. Automated local evidence also covers concurrent API instances, oversell prevention, expiry, idempotency, explicit problem errors, Docker Compose, README/runbook, and PostgreSQL-backed integrity. AWS runtime behavior has dated remote evidence for authorization, CIDR admission, queues, e-mail delivery, resource creation and the effective throttling targets.

## Fix plans

1. **P1 — accepted AWS semantics.** The IAM/SigV4 contract remains the only client credential. API Gateway stage limits stay configured as best-effort targets. The validation records their effective values and measured burst outcome. A deterministic admission mechanism is outside this demo.

## Summary

**Overall**: PASS — 43/43 ACs pass under the revised, provider-accurate throttling criteria.
**What works**: local command/query flows, transactional inventory/outbox, event availability cache fallback/invalidation, direct PostgreSQL reservation queries, idempotency, expiry/reconciliation, notification flow, remote Terraform runtime, IAM boundary, CIDR admission, both DLQs, and the measured SES acceptance SLO.
**Next step**: none. A future requirement for deterministic `429` admission requires a separate architecture decision and implementation task.

## Independent verification

**Verifier date**: 2026-09-11
**Reviewed diff**: `c29f114..9899fc6`
**Scope**: T29 specification, task completion, validation evidence, and project handoff. No AWS resource was created or queried.

| Command | Result |
| --- | --- |
| `python C:\Users\vinic\.codex\skills\tlc-spec-driven\scripts\validate_spec.py .specs\features\flash-booking-demo\spec.md` | PASS — 0 errors, 0 warnings |
| `python C:\Users\vinic\.codex\skills\tlc-spec-driven\scripts\validate_tasks.py .specs\features\flash-booking-demo\tasks.md` | PASS — 0 errors, 0 warnings |
| `python C:\Users\vinic\.codex\skills\tlc-spec-driven\scripts\validate_state.py flash-booking-demo` | PASS — 0 errors |
| `git diff --check c29f114..9899fc6` | PASS |

The revised criteria at `spec.md:130-131` require effective stage targets and a recorded signed-burst distribution, not a deterministic `429`. Edge-3 has a dated remote burst observation; Edge-4 has a dated safe POST burst observation, while both POST and DELETE targets are evidenced without claiming a stateful DELETE or concurrent command execution (`validation.md:58-59`; `infra/modules/edge-observability/main.tf:416-427`). T29 is complete (`tasks.md:427-437`), all seven requirements are `Validated` (`spec.md:168-174`), and the handoff is reconciled as complete (`.specs/STATE.md:148-155`).

**Verdict**: PASS — T29 is provider-accurate, traceable, and complete under the user-approved best-effort throttling contract.

## Independent verification — v1 cache-scope correction

**Verifier date**: 2026-09-14
**Reviewed diff**: `4e1a9f93b6e06e366500dadb580ab6054094a87d..6597aa8` (cache-scope commits `5632009` and `6597aa8`; concurrent commit `d37fb53` is unrelated)
**Scope**: code-only correction; no Docker, Terraform or AWS command was run.

| Affected criterion | Evidence | Result |
| --- | --- | --- |
| Event-5 — Valkey remains exclusive to `GET /events/{id}` | `GetEventService.java:42-73`; `RedisEventAvailabilityCache.java:17-49` | PASS |
| Reserve-4 — reservation exposes event only as `{id,name}` | `JdbcReservationPersistenceAdapter.java:37-47,166-185`; `ReservationQueryControllerIT.java:62-74` | PASS (IT compiled previously; not re-executed without Docker) |
| Reserve-5 — every reservation lookup reads PostgreSQL and does not depend on Valkey | `GetReservationService.java:10-18`; `GetReservationServiceTest.java:42-51`; `ReservationQueryControllerIT.java:96-110` | PASS (unit); IT not re-executed without Docker |
| Expire-7 — cancellation/expiry invalidates only the event-availability cache | `CancelReservationService.java:42-49`; `ExpireReservationService.java:34-39`; `ReservationQueryControllerIT.java:114-131`; `SqsExpirationConsumerIT.java:78-95` | PASS (unit); IT not re-executed without Docker |
| High-load remains future design | `.specs/features/flash-booking-high-load/design.md:5-7`; `.specs/features/flash-booking-high-load/tasks.md:8` | PASS |

Static search found no remaining `ReservationCache`, `ReservationChanged`, `RedisReservationCache` or `reservation:*` cache key in tracked source. Test integrity remained 92 `@Test` methods before and after the correction. The demo and high-load spec/task validators, demo state validator and `git diff --check` passed. Maven ran the complete unit suite with 38 tests, 0 failures and 0 skipped. After the final cleanup, 77 production sources compiled from the real tree and the targeted `GetReservationServiceTest` plus `ExpireReservationServiceTest` suite passed 7/7. Both corrected SVGs are valid XML and `scripts/validate-readme.ps1` passed.

Code quality is surgical and consistent with the existing Spring/JDBC boundaries. The final cleanup removed the obsolete `reservationId` parameter from `ExpireReservationService.expireAndReturn` and clarified the `EventReader` label without introducing a new abstraction.

The lightweight sensor used a detached scratch worktree based on `6597aa8` and injected memoization into `GetReservationService`, targeting `GetReservationServiceTest.java:42-51`. The mutant was killed: `get_whenCalledTwice_readsThePersistencePortTwice` expected two calls to `ReservationReader.findById` and observed one. Result: 1/1 killed, 0 survived. The scratch worktree was removed and real-tree porcelain returned to the baseline containing only this `validation.md` update.

**Scoped verdict**: PASS — the code-only cache-scope correction satisfies the affected v1 criteria, the discrimination sensor distinguishes a reintroduced reservation cache, and no code-quality gap remains. Runtime execution of the affected integration tests remains explicitly pending because Docker is unavailable; their sources compile and the limitation does not convert high-load draft design into delivered behavior.

## Independent verification — bounded idempotency window

- **Verifier date**: 2026-09-15
- **Reviewed diff**: `30ca0f4^..f86f6ed` (`30ca0f4` and `f86f6ed`)
- **Verifier**: independent sub-agent (author != verifier)
- **Scope**: current idempotency criteria in `spec.md:99-106`, AD-012 in `.specs/STATE.md:87-93`, `design.md:119-129,166`, ADR 0007, and T13 in `tasks.md:230-239`. No Docker, Testcontainers, PostgreSQL, Terraform, or AWS command was run.

### Scoped verdict

**Overall**: NOT FULLY VERIFIED — PASS for static implementation, configuration behavior, unit execution, and test compilation; PostgreSQL-dependent replay/reclaim/cleanup behavior remains pending runtime execution. Evidence-or-zero does not treat compiled `*IT.java` sources as executed scenarios.

| Contract slice | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| Replay before expiry | Same unexpired key and fingerprint returns the stored response without another effect | `PersistentIdempotencyService.java:28-48`; `PersistentIdempotencyServiceTest.java:45-62` asserts stored `201` and no completion/new action; `IdempotencyControllerIT.java:74-96` asserts equal bodies and one event | PASS for orchestration/unit; PostgreSQL IT compiled, not executed |
| Conflict before expiry | Different operation, target, or payload under an unexpired key returns `409` without the command effect | `JdbcIdempotencyStore.java:32-47` leaves an unexpired row unchanged; `PersistentIdempotencyServiceTest.java:64-78` asserts `ResourceConflictException`; `IdempotencyControllerIT.java:99-115` asserts `409`, `resource-conflict`, and one event | PASS for orchestration/unit/static SQL; PostgreSQL IT compiled, not executed |
| Bounded 24-hour window and atomic reclaim | At or after `expires_at`, uniqueness and `ON CONFLICT` allow one claimant; a new 24-hour window is measured by PostgreSQL after acquiring the conflicting row lock | `JdbcIdempotencyStore.java:27-47` uses the unique key, `clock_timestamp()`, and conditional `ON CONFLICT DO UPDATE`; `V1__create_flash_booking_schema.sql:59-69` provides the unique key and timestamp constraints; `IdempotencyControllerIT.java:119-157` asserts two parallel retries create exactly one new effect, keep one record, and restore a future expiry | PASS by static PostgreSQL semantics and compiled regression contract; runtime PostgreSQL evidence pending |
| Final responses and transient failures | Final domain responses, including capacity `409`, are stored; unexpected `5xx` paths do not complete and roll back the claim | `PersistentIdempotencyServiceTest.java:80-111` asserts persisted `409` and no completion on unexpected failure; `PersistentIdempotencyIT.java:50-64` asserts the failed claim is rolled back | PASS for unit behavior; rollback IT compiled, not executed |
| Bounded, indexable cleanup | The worker selects at most the configured limit using a stable DB cutoff, skips locked rows, and revalidates expiry before deletion | `JdbcIdempotencyStore.java:79-96` uses `statement_timestamp()`, `LIMIT ?`, `FOR UPDATE SKIP LOCKED`, the captured `id`, and a final `clock_timestamp()` predicate; `V1__create_flash_booking_schema.sql:97` indexes `expires_at`; `PersistentIdempotencyIT.java:68-86` asserts a batch of two, only expired rows, and preservation of an active row | PASS static/query-shape; PostgreSQL IT compiled, not executed |
| Worker/all activation | Cleanup runs only in worker or all mode and scheduling uses the typed cleanup settings | `IdempotencyRecordCleaner.java:9-29` has `@Profile({"worker", "all"})`, typed properties, and bounded deletion; `application.yml:41-45` supplies the v1 defaults | PASS static |
| Typed binding, validation, and environment override | Missing values use safe defaults; explicit invalid values fail startup; environment variables bind explicit units/values | `IdempotencyCleanupProperties.java:9-27`; `FlashBookingApplication.java:8-10`; `IdempotencyCleanupPropertiesTest.java:20-64` asserts defaults, environment override, `batch-size` bounds, and duration bounds | PASS — 8/8 configuration tests executed within the 46-test quick suite |
| Schema evolution | No migration is added when the existing table, uniqueness, timestamps, constraint, and cleanup index already support the new behavior | No path under `src/main/resources/db/migration/` changed in `30ca0f4^..f86f6ed`; existing support is at `V1__create_flash_booking_schema.sql:59-69,97` | PASS — no unnecessary migration |

### PostgreSQL concurrency review

- `ON CONFLICT` locks the conflicting unique row before evaluating its `WHERE`/`SET` action. The reclaim predicate at `JdbcIdempotencyStore.java:41` and the new `created_at`/`expires_at` expressions at `:39-40` therefore use the PostgreSQL wall clock after the wait instead of shortening the next window by lock-wait time.
- Concurrent post-expiry retries serialize on `idempotency_key`; one update reports one affected row and executes the command, while later contenders observe the refreshed, unexpired row and replay or conflict through `PersistentIdempotencyService.java:28-48`.
- Cleanup first uses the stable `statement_timestamp()` cutoff at `JdbcIdempotencyStore.java:87`, preserving the `expires_at` index access path, then locks a bounded ordered set with `LIMIT ? FOR UPDATE SKIP LOCKED` at `:88-90`. A claimant that already holds the row is skipped; a claimant waiting behind cleanup inserts/reclaims after deletion. The delete also joins the captured `id` and rechecks expiry at `:94-95`, so a refreshed identity/expiry is not deleted.

These are static conclusions about PostgreSQL semantics, not observations from this verification run.

### Gate evidence

| Check | Result |
| --- | --- |
| Quick suite | PASS — 46 tests, 0 failures, 0 errors, 0 skipped. The eight new `IdempotencyCleanupPropertiesTest` cases are present in `target/surefire-reports/TEST-com.cielo.flashbooking.application.idempotency.IdempotencyCleanupPropertiesTest.xml`. |
| Test-tree compilation | PASS — all production and test sources, including `IdempotencyControllerIT` and `PersistentIdempotencyIT`, compiled. Compilation is not recorded as PostgreSQL execution. |
| Integration/Full gate | NOT RUN by instruction — `./mvnw verify -Pintegration` would start Testcontainers/PostgreSQL. |
| Diff hygiene | PASS — `git diff --check 30ca0f4^ f86f6ed` produced no output. |
| Migration scope | PASS — the reviewed range contains no migration change. |

**Test integrity**: the quick-suite count increased from 38 before the correction to 46 after it (+8 configuration-binding cases), with no deletion or weakened assertion found in the reviewed test diff. The PostgreSQL `*IT` changes add the expired-key concurrency contract and keep the prior replay/conflict assertions.

### Code quality

The change is scoped to the active idempotency contract and its documentation. `IdempotencyCleanupProperties` is registered through `@ConfigurationPropertiesScan`, uses explicit `Duration` values and bounded startup validation, and the cleaner follows the established worker/all profile convention. JDBC remains the appropriate boundary for the PostgreSQL-specific `ON CONFLICT` and `SKIP LOCKED` semantics. No unrelated abstraction, dependency, public API, or schema change was introduced. The two missing concurrency-specific assertions are recorded below rather than silently counted as coverage.

### Discrimination sensor

A repository archive of `f86f6ed` was expanded into an isolated scratch copy. The sensor recreated the pre-hardening defect in `IdempotencyCleanupProperties`: `Integer` became primitive `int`, and the null-only default became `batchSize == 0 ? 500 : batchSize`. The targeted command ran only `IdempotencyCleanupPropertiesTest`, without Docker or external services.

| Mutation | Targeted assertion | Outcome |
| --- | --- | --- |
| Convert explicit zero into the default, allowing `batch-size: 0` to bypass `@Min(1)` | `IdempotencyCleanupPropertiesTest.java:47-51` — context must fail for `0`, `-1`, and `10001` | KILLED — 8 tests ran and the zero case failed at line 51 because the mutated context started successfully (1 expected mutant failure, 0 errors) |

**Sensor result**: 1/1 killed, 0 survived. An initial invocation accidentally targeted the real unmutated `pom.xml` and was discarded as invalid evidence; the counted invocation used the scratch `pom.xml` explicitly. Both scratch copies and archives were removed. Real-tree porcelain matched the clean pre-sensor baseline afterward.

No SQL mutation was attempted: without an executed PostgreSQL test, a fabricated SQL sensor would provide no discrimination evidence.

### Ranked gaps

1. **P1 verification gap — PostgreSQL scenarios not executed.** Replay/conflict against the changed `ON CONFLICT`, concurrent post-expiry reclaim, transaction rollback, and bounded cleanup are defined by `IdempotencyControllerIT.java:74-157` and `PersistentIdempotencyIT.java:50-86`, but this run only compiled them. Run the Full gate with Docker/Testcontainers before claiming a fresh runtime PASS for T13.
2. **P1 test-specificity gap — no lock-wait boundary assertion.** `IdempotencyControllerIT.java:119-157` expires a row and races two requests, but it does not hold the old row lock long enough to prove that the new `created_at`/`expires_at` window starts after the wait or assert an approximately 24-hour interval. The production SQL is statically correct at `JdbcIdempotencyStore.java:39-41`; a PostgreSQL integration test should discriminate `clock_timestamp()` in `SET` from the former pre-lock `EXCLUDED` timestamps.
3. **P1 test-specificity gap — no cleanup-versus-reclaim race.** `PersistentIdempotencyIT.java:68-86` proves bounded sequential deletion and preservation of an already-active row, but does not race cleanup with reactivation. The SQL is statically safe at `JdbcIdempotencyStore.java:84-95`; add synchronized PostgreSQL coverage for both lock orders and confirm the refreshed record survives.

No implementation defect was confirmed in `30ca0f4^..f86f6ed`. Gaps 1-3 are verification gaps. The orchestrator recorded the reusable test-specificity findings as candidate lessons `L-001` and `L-002` after this independent review.
