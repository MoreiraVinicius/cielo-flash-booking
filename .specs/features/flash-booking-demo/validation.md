# Flash Booking Demo Validation

**Date**: 2026-09-15
**Spec**: `.specs/features/flash-booking-demo/spec.md`  
**Reviewed commits**: `db604d0` (contrato e documentação) e `2809840` (implementação e testes), inspecionados separadamente porque commits visuais estão intercalados
**Verifier**: independent agent (author != verifier)
**Throttling semantics**: [AWS documents throttles and quotas as best-effort targets](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-request-throttling.html).

## Validation

**Result:** PASS no escopo da regra de prazo de cancelamento

A regra vigente de DEMO-03 passa nos testes unitários e nos quatro cenários PostgreSQL de `ReservationDeadlineIT`. O PostgreSQL descartável disponível era 17.5. O target oficial PostgreSQL 16/Testcontainers e o Full gate completo permanecem pendentes e não são alegados por esta verificação. As evidências anteriores dos demais critérios continuam registradas abaixo; este passe atualiza apenas o escopo dos commits `db604d0` e `2809840`.

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
| Expire-1 | Antes do prazo, DELETE retorna `200`, `CANCELLED` e `CANCELLED_BY_REQUEST`; no prazo/depois, retorna `200`, `EXPIRED` e `RESERVATION_DEADLINE_REACHED`; a decisão ocorre após o lock e a capacidade volta uma vez | `ReservationDeadlineIT.java:106-113` — `status().isOk()`, `jsonPath("$.status").value("CANCELLED")` e código/descrição; `:121-138` — `isOk()`, `EXPIRED`, motivo, repetição e worker sem segundo efeito; `:146-157` — lock atravessa o prazo e `assertTerminalState(..., "EXPIRED", ...)`; `:213-226` — estado, motivo e `available == 10` | PASS |
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

**Spec-anchored status**: PASS para a regra de prazo de Expire-1. Os demais critérios mantêm a evidência canônica anterior; não foram reexecutados neste passe.

## Edge cases

- [x] Non-positive capacity/quantity: `EventControllerIT.java:103-120` and `ReservationControllerIT.java:106-122`.
- [x] Missing event has no partial effects: `ReservationControllerIT.java:143-153`.
- [x] DELETE antes/depois do prazo, espera por lock e disputa com worker preservam o terminal correto e devolvem capacidade uma vez: `ReservationDeadlineIT.java:102-178`; assertions persistidas em `:212-226`.
- [x] Delayed SQS is repaired by reconciler: `ExpirationReconcilerIT.java:56-66`.
- [x] Event cache failure falls back with configured 100ms timeout: `EventControllerIT.java:154-169`.
- [x] Reservation query is independent of Valkey: `GetReservationServiceTest.java:42-51`; `ReservationQueryControllerIT.java:96-105`.
- [x] Duplicate mail processing is idempotent: `SqsReservationCreatedConsumerIT.java:105-117`.
- [x] Schema prevents invalid inventory, relation, terminal-reason and idempotency data: `InitialSchemaIT.java:44-102`.

## Discrimination sensor

O sensor atual usou um worktree temporário detached em `56a75b5`; nenhum `git stash` foi usado. O porcelain real imediatamente antes e depois foi idêntico: `?? .tmp/`. Esse diretório preexistente contém o PostgreSQL descartável usado pelos testes.

| Mutation | Location | Targeted test | Outcome |
| --- | --- | --- | --- |
| Replace `AWS_IAM` with `NONE` on POST `/events` | `infra/modules/edge-observability/main.tf:232` | `terraform test` edge module | **Killed**: assertion `edge-observability.tftest.hcl:57` failed, observing `authorization is "NONE"`. |
| Change atomic decrement to increment | `JdbcInventoryOperations.java:23` | `ReservationConcurrencyIT` | **Killed**: test errored on PostgreSQL `event_check` after availability became `11` for capacity `10`; failure surfaces from `ReservationConcurrencyIT.java:76`. |
| Trocar `clock_timestamp()` por `statement_timestamp()` na observação pós-lock | `JdbcReservationPersistenceAdapter.java:128` | `ReservationDeadlineIT.cancel_whenLockWaitCrossesDeadline_usesDatabaseTimeAfterTheLock` | **Killed**: `ReservationDeadlineIT.java:157` esperava `EXPIRED`; o mutante persistiu `CANCELLED`, observado pela assertion em `:215`. |

**Sensor result**: 1/1 mutante da regra de prazo morto, 0 sobreviveu; 3/3 no inventário desta tabela. O worktree foi removido.

## Gates

| Gate | Result |
| --- | --- |
| Current unit suite | PASS em 2026-09-15 — 47 testes, 0 falhas, 0 erros, 0 ignorados, com Java 21.0.12.1. |
| `ReservationDeadlineIT` no PostgreSQL descartável | PASS em PostgreSQL 17.5 — 4 testes, 0 falhas, 0 erros, 0 ignorados. |
| Target oficial PostgreSQL 16/Testcontainers | PENDING — Docker está indisponível; este passe não alega execução no target oficial. |
| Full gate `./mvnw clean verify -Pintegration` | PENDING — não executado; o passe atual rodou o gate unitário e somente os quatro ITs da regra de prazo. |
| `validate_spec.py` | PASS — 0 errors, 0 warnings |
| `validate_tasks.py` | PASS — 0 errors, 0 warnings |
| `validate_state.py flash-booking-demo` | PASS — 0 errors. |
| `scripts/validate-readme.ps1` | PASS — 5 endpoints, 13 images, 41 referências locais e 3 cenários de desempenho. |
| `git diff --check db604d0^ db604d0` e `git diff --check 2809840^ 2809840` | PASS — commits avaliados explicitamente, sem usar range contínuo. |
| `git diff --check 37e45ca..2c6723c` | PASS |
| Maven Build (`clean verify -Pintegration`) | PASS before smoke-only commit `2869392`: 38 unit + 56 integration = 94 passed, 0 failed, 0 skipped. `2869392` changes only readiness logic in `scripts/compose-smoke.ps1`. |
| Compose smoke at `2869392` | PASS from cold start: waits for query/command health, exercises endpoints and Mailpit, then tears down. |
| Terraform format/validate | PASS locally; `terraform validate` passed bootstrap, network, data-plane, compute, edge-observability, and demo environment. |
| Terraform module tests | PASS: network 1/1, data-plane 1/1, compute 1/1, edge-observability 1/1. |
| Remote Terraform plan/apply | PASS — temporary assumed roles, remote plan/apply and post-rollout ECS/target-health checks completed. |
| Remote Terraform destroy | PASS — 2026-09-11: `terraform destroy` removed 106 demo resources; `terraform state list` returned `0`, and API Gateway, RDS, Valkey and NAT no longer have active demo resources. |

## Case BackEnd 1 report

All five required routes have integration assertions: event creation/availability in `EventControllerIT.java:74-149`, reservation creation in `ReservationControllerIT.java:64-171`, reservation query in `ReservationQueryControllerIT.java:62-101`, and cancellation in `ReservationDeadlineIT.java:102-178`. Automated local evidence also covers concurrent API instances, oversell prevention, expiry, idempotency, explicit problem errors, Docker Compose, README/runbook, and PostgreSQL-backed integrity. AWS runtime behavior has dated remote evidence for authorization, CIDR admission, queues, e-mail delivery, resource creation and the effective throttling targets.

## Fix plans

1. **P1 — accepted AWS semantics.** The IAM/SigV4 contract remains the only client credential. API Gateway stage limits stay configured as best-effort targets. The validation records their effective values and measured burst outcome. A deterministic admission mechanism is outside this demo.

## Summary

**Overall**: PASS no escopo da regra de prazo de cancelamento; esta execução não substitui um Full gate atual de toda a feature.
**What works**: DELETE antes do prazo termina em CANCELLED; no prazo ou depois termina em EXPIRED; a decisão usa o relógio PostgreSQL após o lock; repetição e disputa com o worker devolvem capacidade uma vez.
**Next step**: executar PostgreSQL 16 via Testcontainers e `./mvnw clean verify -Pintegration` quando Docker estiver disponível.

## Verificação atual da regra de prazo

**Escopo**: commits `db604d0` e `2809840`, inspecionados individualmente. Os commits visuais entre eles não pertencem a esta validação.
**Veredito do escopo**: PASS.

| Fatia do contrato | Resultado definido pela spec | Evidência `file:line` + assertion expression | Resultado |
| --- | --- | --- | --- |
| DELETE antes de `expiresAt` | HTTP `200`, `CANCELLED`, `CANCELLED_BY_REQUEST` e capacidade devolvida uma vez | `ReservationDeadlineIT.java:106-113` — `status().isOk()`, `jsonPath("$.status").value("CANCELLED")`, `jsonPath("$.closureReason.code").value("CANCELLED_BY_REQUEST")`; `:213-226` — `isEqualTo(status/reasonCode/reasonDescription)` e `isEqualTo(10)` | PASS |
| Fronteira exata em `expiresAt` | O terminal é `EXPIRED` com `RESERVATION_DEADLINE_REACHED` | `ReservationTest.java:58-64` — `reservation.cancel(EXPIRES_AT)`, `status() == EXPIRED` e motivo exato; `JdbcReservationPersistenceAdapter.java:133-144` — somente `observed_at < expires_at` escolhe CANCELLED, e o complemento escolhe EXPIRED | PASS |
| DELETE depois de `expiresAt` | HTTP `200`, `EXPIRED`, motivo de prazo; retries e worker não repetem o efeito | `ReservationDeadlineIT.java:121-138` — `status().isOk()`, `jsonPath("$.status").value("EXPIRED")`, motivo exato, respostas repetidas `EXPIRED`, `expire(...).isFalse()` e uma única `evict(eventId)`; `:213-226` — estado/motivo persistidos e `available == 10` | PASS |
| Espera por lock atravessa o prazo | O instante PostgreSQL depois do lock prevalece e produz `EXPIRED` | `ReservationDeadlineIT.java:146-157` — segura `FOR UPDATE`, espera a sessão bloquear e o relógio alcançar o prazo, então `assertTerminalState(..., "EXPIRED", ...)`; `JdbcReservationPersistenceAdapter.java:122-149` — `FOR UPDATE` precede o CTE que avalia `clock_timestamp()` | PASS |
| DELETE concorre com worker | O estado final é `EXPIRED` e a capacidade volta exatamente uma vez | `ReservationDeadlineIT.java:161-178` — duas operações partem do mesmo latch e `assertTerminalState(..., "EXPIRED", ...)`; `:213-226` — motivo exato e `available == 10` | PASS |
| Atomicidade e encerramento já efetivado | Estado/motivo e incremento pertencem à mesma transação; perdedor não incrementa nem invalida novamente | `CancelReservationService.java:30-44` — `@Transactional`, release condicional, `inventoryOperations.increment(...)` e evento; `CancelReservationServiceTest.java:43-56` — `verify(inventory, never()).increment(...)` e `verify(publisher, never()).publishEvent(...)` quando já terminal | PASS |

O inventário de testes cresce de 96 para 101 anotações `@Test` no commit `2809840` (+5). A inspeção do diff não encontrou exclusão, skip ou enfraquecimento de assertion. A implementação mantém a decisão temporal no adaptador JDBC, usa lock pessimista por uma exigência concreta de consistência e deixa a transação curta no serviço Spring.

### Gate e sensor deste escopo

- Unitário: Maven 3.9.10 com Java 21, alvo `test`; 47/47 PASS.
- PostgreSQL descartável 17.5: Maven 3.9.10 com `-Dtest.postgres.url=jdbc:postgresql://127.0.0.1:55433/flash_booking_test -Dtest.postgres.username=postgres -Dtest.postgres.password= -Dit.test=ReservationDeadlineIT -Pintegration verify`; 4/4 PASS.
- Sensor: 1/1 morto ao substituir o instante pós-lock por `statement_timestamp()`; a assertion `ReservationDeadlineIT.java:157` observou `CANCELLED` em vez de `EXPIRED`.
- Isolamento: worktree removido; porcelain real permaneceu `?? .tmp/` antes e depois.
- Estrutura e diff: `validate_spec.py`, `validate_tasks.py` e os dois `git diff --check` explícitos passam.

### Gaps ranqueados

1. **P1 — target oficial pendente.** PostgreSQL 16/Testcontainers não foi executado porque Docker está indisponível.
2. **P1 — Full gate pendente.** `./mvnw clean verify -Pintegration` não foi executado neste passe; o PASS se limita à regra de prazo comprovada pelos gates acima.

Nenhum defeito de implementação foi confirmado no escopo revisado.

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
- **Scope**: current idempotency criteria in `spec.md:99-106`, AD-012 in `.specs/STATE.md:87-93`, `design.md:119-129,166`, and T13 in `tasks.md:230-239`. No Docker, Testcontainers, PostgreSQL, Terraform, or AWS command was run.

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

## Independent verification — DEMO-09 / T31 operational dashboard

- **Verifier date:** 2026-09-20
- **Reviewed commit:** `2504e3d98e8b3f5eaa67e1a9924b6bbd2ac15886`
- **Verifier:** independent sub-agent (author != verifier)
- **Scope:** DEMO-09, T31, the CloudWatch dashboard resource and its Terraform module test. The AWS checks were read-only; no apply or infrastructure mutation was performed.

### Verdict

**Overall:** PASS — all 6 DEMO-09 acceptance criteria match the versioned dashboard and the dashboard published as `flash-booking-demo-demo` in `sa-east-1`.

T31 is marked `Complete` at `tasks.md:454`. The reviewed diff changes only the dashboard implementation, its structural test, and the corresponding spec/design/state artifacts. No unrelated production behavior was added.

### Spec-anchored acceptance criteria

| Criterion | Spec-defined outcome | Evidence (`file:line`, assertion, or dated remote observation) | Result |
| --- | --- | --- | --- |
| DEMO-09 AC1 | Separate charts show API Gateway volume/errors and p50/p95/p99 latency. | `main.tf:569` defines the request/error chart with `Count`, `4XXError` and `5XXError`; `main.tf:588` defines `Latency` p50/p95/p99 and `IntegrationLatency` p95. `edge-observability.tftest.hcl:117` asserts the required titles are a subset of the generated dashboard titles. Remote 2026-09-20 returned both charts and exactly those metrics/statistics. | PASS |
| DEMO-09 AC2 | Desired/running task count and separate CPU/memory series exist for query, command and worker. | `main.tf:653`, `main.tf:675` and `main.tf:697` define the three charts and all three services. `edge-observability.tftest.hcl:144` asserts the ECS service dimension in the generated JSON. Remote 2026-09-20 returned `RunningTaskCount`/`DesiredTaskCount` plus CPU and memory series for `query-api`, `command-api` and `worker`. | PASS |
| DEMO-09 AC3 | Query and command target groups expose healthy/unhealthy targets and target latency with published ALB dimensions. | `main.tf:607` and `main.tf:626` define health and latency with `TargetGroup + LoadBalancer`; `edge-observability.tftest.hcl:143` asserts the target-group suffix in the generated dashboard. Remote 2026-09-20 returned the actual query and command target-group suffixes and the internal ALB suffix. `aws cloudwatch list-metrics` also returned `HealthyHostCount` and `UnHealthyHostCount` for the query target group. | PASS |
| DEMO-09 AC4 | PostgreSQL, Valkey, SQS queues and DLQs expose the stated capacity and activity signals. | `main.tf:727`, `main.tf:748`, `main.tf:765`, `main.tf:787`, `main.tf:805` and `main.tf:825` define the RDS, Valkey and SQS charts. `edge-observability.tftest.hcl:117` asserts all six chart titles and `edge-observability.tftest.hcl:145` asserts the SQS `QueueName` dimension. Remote 2026-09-20 returned RDS CPU/connections/free memory/free storage, Valkey CPU/memory/connections/hits/misses, queue depth/age and both DLQs; `list-metrics` confirmed the Valkey dimensions are currently published. | PASS |
| DEMO-09 AC5 | Two Logs Insights widgets investigate API 4xx/5xx and errors from all three ECS services using existing groups. | `main.tf:854` and `main.tf:865` define the two table queries. `edge-observability.tftest.hcl:150` asserts the API access group and all three ECS log groups are present. Remote 2026-09-20 returned both queries with `/apigateway/flash-booking-demo/access`, `/ecs/flash-booking-demo/query-api`, `/ecs/flash-booking-demo/command-api` and `/ecs/flash-booking-demo/worker`. | PASS |
| DEMO-09 AC6 | Only existing AWS telemetry is used; every metric widget uses 60 seconds; no custom metric, new alarm or retention change is introduced. | Remote 2026-09-20 returned 13/13 metric widgets with `period = 60` and only `AWS/ApiGateway`, `AWS/ApplicationELB`, `AWS/ECS`, `ECS/ContainerInsights`, `AWS/RDS`, `AWS/ElastiCache` and `AWS/SQS`. A before/after static count for `2504e3d` remained 3 alarms, 0 log metric filters, 1 log group and one 7-day retention declaration; the unchanged retention is at `main.tf:353`. | PASS |

**Spec-anchored status:** 6/6 ACs match the precise outcomes in `spec.md:165` through `spec.md:170`; no spec-precision gap was found.

### Remote dashboard observation

The read-only check used account `581645023528` through the assumed `FlashBookingDemoProvisioner` role in `sa-east-1` on 2026-09-20.

| Check | Observed result |
| --- | --- |
| Dashboard identity | `flash-booking-demo-demo` |
| Widget inventory | 19 total: 4 text, 13 metric, 2 log |
| Required titles | All 15 non-section titles from `edge-observability.tftest.hcl:118` through `edge-observability.tftest.hcl:134` present |
| Metric period | 13/13 metric widgets use 60 seconds |
| Metric namespaces | Only seven existing AWS namespaces listed in AC6 |
| Log sources | Existing API access group and all three ECS service groups |
| Reconciliation evidence | `post-dashboard.plan`, generated 2026-09-20 19:54:12 with Terraform 1.16.1, records `module.edge_observability.aws_cloudwatch_dashboard.demo` as `no-op`; its only non-no-op resource is the pre-existing API Gateway policy canonicalization update |

### Gate evidence

| Gate | Result |
| --- | --- |
| `TF_CLI_CONFIG_FILE=NUL terraform fmt -check -recursive infra` | PASS, exit 0 |
| `TF_CLI_CONFIG_FILE=NUL terraform test` in `infra/modules/edge-observability` | PASS — 1 run passed, 0 failed |
| `TF_CLI_CONFIG_FILE=NUL terraform validate` in `infra/environments/demo` | PASS — configuration valid |
| `validate_spec.py .specs/features/flash-booking-demo/spec.md` | PASS — 0 errors, 0 warnings |
| `validate_tasks.py .specs/features/flash-booking-demo/tasks.md --strict` | PASS — 0 errors, 0 warnings |
| `git diff --check 2504e3d^ 2504e3d` | PASS |

**Test integrity:** the edge module keeps one Terraform test run and grows from 8 to 12 `assert` blocks (+4). No assertion was removed or weakened in the reviewed commit.

### Discrimination sensor

The sensor copied only `infra/modules/edge-observability` to a unique directory under the system temporary folder; the real worktree was never modified. In the scratch copy, the `Recent application errors` widget type was changed from `log` to `text`, removing one required log investigation.

| Mutation | Targeted assertion | Outcome |
| --- | --- | --- |
| Change the second failure-investigation widget from `log` to `text` | `edge-observability.tftest.hcl:108` — requires exactly 4 text, 13 metric and 2 log widgets | KILLED — `terraform test` failed with 0 passed and 1 failed, reporting the exact structural assertion |

**Sensor result:** 1/1 mutation killed, 0 survived. The temporary copy was removed. Real-tree porcelain after cleanup exactly matched the baseline: `docs/intellij-debug.md` deleted, `scripts/compose-smoke.ps1` modified, and the pre-existing `.tmp/`, `docs/research/`, `docs/rodar-localmente.md` and `scripts/generate-interview-audio.ps1` untracked; none belongs to T31.

### Code quality and gaps

| Check | Result |
| --- | --- |
| Minimum, surgical change | PASS — one existing dashboard resource was expanded; no new abstraction or telemetry resource |
| Existing Terraform style | PASS — stable resource-name conventions and AWS metric dimensions are reused |
| Tests map to DEMO-09 | PASS — structure, required coverage, critical dimensions and log sources are asserted; the remote read confirms the precise metric/stat values |
| Unclaimed tests or scope creep | PASS — the four new assertions belong directly to DEMO-09/T31 |
| Documented project guidance | PASS — `.specs/STATE.md` AD-023 and the repository Terraform conventions were followed |

No implementation defect, surviving mutant, or spec-precision gap was found. There is no validation signal to distill into a new lesson.

### Requirement traceability update

| Requirement | Previous status | Verified status |
| --- | --- | --- |
| DEMO-09 | Implemented | Validated |

**Final verdict:** PASS — T31 is ready. The orchestrator should update `spec.md` traceability and `.specs/STATE.md` handoff in its closing commit; this verifier intentionally changed only `validation.md`.

## Independent verification — DEMO-10 / T32 business dashboard

- **Verifier date:** 2026-09-21
- **Reviewed commit:** `96caff9c1429cfef9f785cd2aa1a86e0879f4b8a`
- **Verifier:** independent sub-agent (author != verifier)
- **Scope:** DEMO-10, T32, the `flash-booking-demo-negocio` CloudWatch resource and its Terraform structural test. No apply, deployment, or AWS resource change was performed.

### Verdict

**Overall:** PASS — all 8 DEMO-10 acceptance criteria match the versioned dashboard and the remote state reconciled by the read-only Terraform plan in `sa-east-1`.

T32 is complete at `tasks.md:467-478`. The reviewed commit adds one CloudWatch dashboard, its structural assertions, and the corresponding specification, design and state updates. No application, alarm, custom metric, log-query or log-retention resource was introduced.

### Spec-anchored acceptance criteria

| Criterion | Spec-defined outcome | Evidence (`file:line`, assertion, or dated remote-plan observation) | Result |
| --- | --- | --- | --- |
| DEMO-10 AC1 | All visible titles, explanatory text and labels are pt-BR. | `main.tf:888` contains the explanatory text; `main.tf:896-1114` defines the business titles and labels. `edge-observability.tftest.hcl:171-225` permits only the approved pt-BR title and label catalog. The 2026-09-21 plan payload lists 11 pt-BR titles, the three pt-BR section texts, and 25 distinct pt-BR metric labels. | PASS |
| DEMO-10 AC2 | The selected period summarizes published events, completed event lookups, accepted reservation responses and completed cancellations. | Four five-minute single-value cards are defined at `main.tf:896-961`; each subtracts `4XXError` and `5XXError` from its API Gateway `Count`. Their required titles are asserted at `edge-observability.tftest.hcl:171-187`. | PASS |
| DEMO-10 AC3 | The journey compares event lookups, reservation attempts, reservation lookups and cancellation requests over time. | `main.tf:976-991` defines the four route-and-method series; `edge-observability.tftest.hcl:244-245` asserts the route-level `Resource` and `Method` dimensions. The reconciled payload contains `/events/{id} GET`, `/events/{id}/reservations POST`, `/reservations/{id} GET`, and `/reservations/{id} DELETE`. | PASS |
| DEMO-10 AC4 | Reservation results separate accepted responses, rule/input non-completions and technical failures. | `main.tf:997-1011` defines accepted as `Count - 4XXError - 5XXError`, alongside the separate 4xx and 5xx series. The title and pt-BR label set are enforced by `edge-observability.tftest.hcl:177-225`. | PASS |
| DEMO-10 AC5 | Acceptance is `100 * accepted / attempts`, returning zero when there are no attempts. | `main.tf:1017-1027` defines `IF(taxa_tentativas>0,100*(taxa_tentativas-taxa_4xx-taxa_5xx)/taxa_tentativas,0)`. The exact expression is asserted at `edge-observability.tftest.hcl:242-247`. | PASS |
| DEMO-10 AC6 | Event-query and reservation-attempt latency expose separate p50 and p95 values. | `main.tf:1090-1105` and `main.tf:1111-1126` define the two latency charts with `p50` and `p95`; their labels are part of the catalog asserted at `edge-observability.tftest.hcl:193-225`. | PASS |
| DEMO-10 AC7 | The dashboard states that it counts HTTP interactions/responses, not unique customers, reservations, sales or revenue. | `main.tf:888` declares this boundary verbatim. `edge-observability.tftest.hcl:242` asserts the required limitation text, consistent with AD-024 at `.specs/STATE.md:186-193`. | PASS |
| DEMO-10 AC8 | Only existing detailed API Gateway metrics are used at five-minute periods; no custom metrics, alarms, log queries or retention changes are added. | `main.tf:899-1114` gives every metric widget `period = 300`; `edge-observability.tftest.hcl:230-237` asserts period 300 and `AWS/ApiGateway`/expression-only metric rows. The reconciled payload has 14 widgets: 3 text, 11 metric, 0 log; every metric period is 300 and its only namespace is `AWS/ApiGateway`. The reviewed diff adds only the dashboard resource. | PASS |

**Spec-anchored status:** 8/8 ACs match the outcomes in `spec.md:180-194`; no spec-precision gap was found.

### Remote reconciliation and drift separation

The verifier attempted the requested direct `AWS_PROFILE=demo-provisioner aws sts get-caller-identity` and CloudWatch read on 2026-09-21. This execution environment has no AWS CLI profile or credentials, so it could not repeat that direct CLI lookup. It did not create credentials or alter AWS configuration.

Instead, the verifier independently inspected the existing read-only `infra/environments/demo/post-business.plan`, generated on 2026-09-21 05:01:33 UTC with the configured `demo-provisioner` profile. Terraform's remote refresh records `module.edge_observability.aws_cloudwatch_dashboard.business` as **no-op** and exposes the reconciled dashboard body used above. The only non-no-op action is `module.edge_observability.aws_api_gateway_rest_api.this:update`.

That API Gateway policy update is pre-existing drift, not part of T32: the earlier `post-dashboard.plan` from 2026-09-20 22:54:07 UTC reports the same sole update, while neither plan changes either CloudWatch dashboard. No plan or apply was run by this verifier.

| Remote-plan check | Observed result |
| --- | --- |
| Dashboard identity | `flash-booking-demo-negocio` |
| Widget inventory | 14 total: 3 text, 11 metric, 0 log |
| Titles, texts and labels | All supplied dashboard strings are the approved pt-BR catalog; no English dashboard title or legend occurs |
| Metric period and namespace | 11/11 metric widgets use `300`; the only published namespace is `AWS/ApiGateway` |
| Reconciliation | Business dashboard is `no-op`; only the pre-existing API Gateway REST policy canonicalization remains an update |

### Gate evidence

| Gate | Result |
| --- | --- |
| `TF_CLI_CONFIG_FILE=NUL terraform fmt -check -recursive infra` | PASS, exit 0 |
| `TF_CLI_CONFIG_FILE=NUL terraform test` in `infra/modules/edge-observability` | PASS — 1 run passed, 0 failed |
| `TF_CLI_CONFIG_FILE=NUL terraform validate` in `infra/environments/demo` | PASS — configuration valid |
| `validate_spec.py .specs/features/flash-booking-demo/spec.md` | PASS — 0 errors, 0 warnings |
| `validate_tasks.py .specs/features/flash-booking-demo/tasks.md --strict` | PASS — 0 errors, 0 warnings |
| `git diff --check 96caff9^ 96caff9` | PASS — no output |

**Test integrity:** T32 adds five assertions at `edge-observability.tftest.hcl:160-247`. The reviewed diff removes no existing Terraform assertion; the checks are tied directly to widget inventory, pt-BR catalog, metric source/period, acceptance formula and route dimensions.

### Discrimination sensor

The sensor copied only `infra/modules/edge-observability` into the system temporary directory. In that isolated copy it changed the visible title `Eventos publicados` to `Published events`; the real worktree was never edited.

| Mutation | Targeted assertion | Outcome |
| --- | --- | --- |
| Anglicize the visible `Eventos publicados` title | `edge-observability.tftest.hcl:171-187` requires every pt-BR journey title | KILLED — `terraform test` failed at line 171 with `The business dashboard must expose the complete customer journey with pt-BR titles.` (0 passed, 1 failed) |

**Sensor result:** 1/1 mutation killed, 0 survived. The explicitly created temporary copy was removed. The real-tree porcelain immediately after cleanup exactly matched the pre-sensor baseline: deleted `docs/intellij-debug.md`, modified `scripts/compose-smoke.ps1`, and the pre-existing untracked `.tmp/`, `docs/research/`, `docs/rodar-localmente.md` and `scripts/generate-interview-audio.ps1`. None belongs to T32.

### Gaps and traceability

No implementation defect, surviving mutation or requirement gap was found. The direct AWS CLI profile is absent in this verifier environment; this is an environment-observation limitation, not a dashboard discrepancy, because the current remote-refresh plan independently proves the dashboard is reconciled as no-op. No lesson is distilled from this clean outcome.

| Requirement | Previous status | Verified status |
| --- | --- | --- |
| DEMO-10 | Implemented | Validated |

**Final verdict:** PASS — T32 is ready. The orchestrator should update `spec.md` traceability and `.specs/STATE.md` handoff in its closing commit; this verifier intentionally changed only `validation.md`.

## Independent verification — DEMO-11 / T33-T35 automatic cost cutoff

- **Verifier date:** 2026-09-21
- **Reviewed commits:** `3c2ff33`, `e015c6c`, `48176a9` and `d6e6486`
- **Verifier:** independent sub-agent (author != verifier)
- **Scope:** Budget at US$50, dedicated SNS delivery, least-privilege Lambda, the recoverable ECS/RDS stop sequence and the cluster-name correction for Application Auto Scaling. AWS inspection was read-only; SNS was not published and Lambda was not invoked because either action would pause the live demo.

### Verdict

**Overall: PASS.** The deployed configuration and the tested handler meet DEMO-11. The safe evidence strategy intentionally does not perform an end-to-end SNS publication against the running environment.

### Spec-anchored acceptance criteria

| Criterion | Spec-defined outcome | Evidence (`file:line` + assertion or remote observation) | Result |
| --- | --- | --- | --- |
| DEMO-11 AC1 | At actual monthly US$50, Budget notifies the dedicated SNS topic and the topic invokes the cutoff mechanism. | `infra/modules/edge-observability/main.tf:485-497` sets a monthly USD 50 Budget and subscribes its actual 100% notification to the dedicated topic; `edge-observability.tftest.hcl:87,117,122` asserts the limit, subscription and Budgets-only topic policy. Read-only AWS observation returned `50.0 USD`, actual thresholds 50/80/100, the 100% SNS subscriber, a Lambda subscription and an Active Lambda. | PASS |
| DEMO-11 AC2 | Freeze autoscaling at min/max zero for query/command and set desired count zero for all three ECS services. | `cost_emergency_stop.py:19-32` registers only the two scalable targets with all scale actions suspended and updates query, command and worker to `desiredCount=0`; `cost_emergency_stop_test.py:49-65` asserts those exact calls, capacities and service set. The test killed the ARN-as-ResourceId mutant below. | PASS |
| DEMO-11 AC3 | Request PostgreSQL stop after ECS reduction. | `cost_emergency_stop.py:31-36` orders ECS updates before `StopDBInstance`; `cost_emergency_stop_test.py:67` asserts the database identifier. | PASS |
| DEMO-11 AC4 | Re-delivery or already-stopped RDS is safe and does not recreate, delete or reactivate resources. | The operations only set zero/stop values in `cost_emergency_stop.py:19-45`; `cost_emergency_stop_test.py:34-40,69-72` simulates `InvalidDBInstanceState` for an already stopped database and asserts `already-stopped`. | PASS |
| DEMO-11 AC5 | Lambda has only logs, two autoscaling targets, three ECS services and the demo RDS permissions. | `main.tf:550-577` contains exactly those IAM actions/resources; `edge-observability.tftest.hcl:109-111` asserts the scoped resource sets. Read-only IAM observation returned exactly those four policy statements and no wildcard operational action. | PASS |
| DEMO-11 AC6 | ALB, Valkey, network, storage, WAF, API Gateway and data remain provisioned and residual cost is declared. | `spec.md:203` and `design.md:276` explicitly preserve those resources and declare residual cost; the handler has no deletion or change operation for them (`cost_emergency_stop.py:19-45`). | PASS |
| DEMO-11 AC7 | Documentation declares periodic Budget processing and no exact US$50 ceiling. | `spec.md:204` and `design.md:276,280` state the delayed cost processing and non-exact cap. | PASS |

**Spec-anchored status:** 7/7 outcomes have precise implementation or configuration evidence; no spec-precision gap found.

### Remote read-only observation

The independent read used account `581645023528` through assumed role `FlashBookingDemoProvisioner` in `sa-east-1` on 2026-09-21.

| Check | Observed result |
| --- | --- |
| Budget | `flash-booking-demo-monthly-cap`, monthly COST, `50.0 USD`; actual thresholds 50%, 80%, 100% |
| 100% subscribers | Operational email plus `arn:aws:sns:sa-east-1:581645023528:flash-booking-demo-budget-emergency` |
| SNS policy | Only `budgets.amazonaws.com` may publish, constrained to account `581645023528` |
| SNS delivery | One Lambda subscription to `flash-booking-demo-cost-emergency-stop` |
| Lambda | `Active`, handler `cost_emergency_stop.handler`, timeout 30 seconds; distinct cluster ARN and name variables are deployed |
| Lambda permission | `sns.amazonaws.com` only, constrained to the dedicated topic ARN |
| Execution policy | Logs only, exactly two Application Auto Scaling target ARNs, three ECS service ARNs and the single demo RDS ARN |

### Gate evidence

| Gate | Result |
| --- | --- |
| `python -m unittest infra/modules/edge-observability/lambda/cost_emergency_stop_test.py -v` | PASS — 2 passed, 0 failed |
| `terraform test` in `infra/modules/edge-observability` | PASS — 1 passed, 0 failed |
| `terraform validate` in `infra/environments/demo` | PASS — configuration valid |
| `validate_spec.py .specs/features/flash-booking-demo/spec.md --strict` | PASS — 0 errors, 0 warnings |
| `validate_tasks.py .specs/features/flash-booking-demo/tasks.md --strict` | PASS — 0 errors, 0 warnings |
| `git diff --check d6e6486^..d6e6486` | PASS — no output |

### Discrimination sensor

The preferred temporary worktree could not be created because this execution identity cannot write Git's `.git/worktrees` metadata. The fallback copied only the Lambda and its unit test to the system temporary directory; the real tree was never edited. The temporary copy was deleted after the test.

| Mutation | File:line | Targeted assertion | Result |
| --- | --- | --- | --- |
| Use `cluster_arn` instead of `cluster_name` in the Application Auto Scaling `ResourceId` | `cost_emergency_stop.py:24` | `cost_emergency_stop_test.py:52-58` requires `service/cluster-name/{service}` | KILLED — 1 of 2 tests failed with expected `cluster-name` and mutated `cluster-arn` values |

**Sensor result:** 1/1 killed, 0 survived. The real-tree porcelain after cleanup matched its baseline; only pre-existing `scripts/compose-smoke.ps1` and generated Lambda `__pycache__/` remained changed/untracked.

### Code quality and limitation

| Check | Result |
| --- | --- |
| Surgical scope and no destructive infrastructure action | PASS |
| ARN/name separation protects Application Auto Scaling while preserving ECS API input | PASS — `cost_emergency_stop.py:24,32` and unit assertions `:54-65` |
| Tests map to DEMO-11 and assert concrete zero/suspension values | PASS |
| Documented guidance followed | PASS — `.specs/STATE.md` AD-026 and `AGENTS.md` Terraform/spec workflow |

No test SNS message was published and no remote Lambda invocation was made: those are intentionally omitted to avoid triggering the real circuit breaker and stopping the demo. This is an operational-safety limitation, not a configuration discrepancy; the deployed subscription, invocation permission, Lambda configuration and handler behavior were independently verified by read-only observation and local tests.

### Requirement traceability update

| Requirement | Previous status | Verified status |
| --- | --- | --- |
| DEMO-11 | Implemented | Validated |

**Final verdict:** PASS — the orchestrator may mark DEMO-11 `Validated` and complete the handoff.
