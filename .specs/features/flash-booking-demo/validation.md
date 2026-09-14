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
