# Postman Executable Documentation Validation

**Date**: 2026-09-29
**Spec**: `.specs/features/postman-executable-documentation/spec.md`
**Diff range**: `64d4c9b..HEAD` (Postman replay extension); previous coverage remains in Git history.
**Verifier**: independent sub-agent (author != verifier)

## Verdict

**PASS** — the Local and signed AWS Runner batteries assert the outcomes required by POSTMAN-10 through POSTMAN-13. The Local Runner also covers POSTMAN-14 and POSTMAN-15. An actual Local Newman execution completed 19 requests and 50 assertions with zero failures.

## Task Completion

| Task | Status | Notes |
| --- | --- | --- |
| T01–T10 | Done | T10 adds Local cancellation and not-found replays plus a Windows-compatible Compose build. |

## Spec-Anchored Acceptance Criteria

| Requirement | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| POSTMAN-01 | Local uses command `:8082`, query `:8081`, no auth. | `postman/flash-booking-aws.postman_collection.json:53`; `postman/flash-booking.local.postman_environment.json:6`; `scripts/validate-postman.ps1:88` | PASS |
| POSTMAN-02 | Local creation is `201`, reads/cancel are `200`, IDs persist. | `postman/flash-booking-aws.postman_collection.json:96`; `postman/flash-booking-aws.postman_collection.json:141`; `postman/flash-booking-aws.postman_collection.json:224` | PASS |
| POSTMAN-03 | Reservation replay returns `201` and the original ID. | `postman/flash-booking-aws.postman_collection.json:166-167`; `scripts/validate-postman.ps1:141` | PASS |
| POSTMAN-04 | AWS uses SigV4 for `execute-api`. | `postman/flash-booking-aws.postman_collection.json:9`; `postman/flash-booking-aws.postman_collection.json:15`; `scripts/validate-postman.ps1:81` | PASS |
| POSTMAN-05 | Unsigned AWS returns `403`; committed AWS values are empty. | `postman/flash-booking-aws.postman_collection.json:459-470`; `postman/flash-booking.aws.postman_environment.json:6`; `scripts/validate-postman.ps1:247` | PASS |
| POSTMAN-06 | Collection documents APIs, capacity, cancellation and sale window. | `postman/flash-booking-aws.postman_collection.json:53`; `postman/flash-booking-aws.postman_collection.json:214`; `postman/flash-booking-aws.postman_collection.json:271` | PASS |
| POSTMAN-07 | Static gate rejects malformed or unsafe collection/environment artifacts. | `scripts/validate-postman.ps1:76`; `scripts/validate-postman.ps1:137`; `scripts/validate-postman.ps1:249` | PASS |
| POSTMAN-08 | Creation requests expose safe editable JSON bodies. | `postman/flash-booking-aws.postman_collection.json:64`; `postman/flash-booking-aws.postman_collection.json:135`; `postman/flash-booking-aws.postman_collection.json:280` | PASS |
| POSTMAN-09 | Create responses save and reuse named IDs. | `postman/flash-booking-aws.postman_collection.json:100`; `postman/flash-booking-aws.postman_collection.json:144`; `postman/flash-booking-aws.postman_collection.json:288` | PASS |
| POSTMAN-10 | Both folders offer ordered replay, payload-conflict and target-conflict requests. | `postman/flash-booking-aws.postman_collection.json:151`; `postman/flash-booking-aws.postman_collection.json:318`; `postman/flash-booking-aws.postman_collection.json:336`; `postman/flash-booking-aws.postman_collection.json:560`; `postman/flash-booking-aws.postman_collection.json:674`; `postman/flash-booking-aws.postman_collection.json:692` | PASS |
| POSTMAN-11 | Changed payload/target assert `409`, `resource-conflict`, Problem Details in Local and signed AWS. | `postman/flash-booking-aws.postman_collection.json:330-331`; `postman/flash-booking-aws.postman_collection.json:348-349`; `postman/flash-booking-aws.postman_collection.json:686-687`; `postman/flash-booking-aws.postman_collection.json:704-705` | PASS |
| POSTMAN-12 | Missing/oversized key assert `400`, `invalid-request`, Problem Details in both flows. | `postman/flash-booking-aws.postman_collection.json:263-265`; `postman/flash-booking-aws.postman_collection.json:384-386`; `postman/flash-booking-aws.postman_collection.json:668-670`; `postman/flash-booking-aws.postman_collection.json:740-742` | PASS |
| POSTMAN-13 | Capacity-conflict replay asserts persisted `409 resource-conflict` plus Problem Details in both flows. | `postman/flash-booking-aws.postman_collection.json:366-368`; `postman/flash-booking-aws.postman_collection.json:722-724`; `scripts/validate-postman.ps1:146` | PASS |
| POSTMAN-14 | Local cancellation replay returns `200`, the original reservation ID, `CANCELLED` and unchanged stock. | `postman/flash-booking-aws.postman_collection.json:390`; `postman/flash-booking-aws.postman_collection.json:398-413`; `scripts/validate-postman.ps1:147-148`; `artefatos/idempotencia-postman-run.json` | PASS |
| POSTMAN-15 | Local missing-event attempt and replay both return `404 resource-not-found` Problem Details with the same command identity. | `postman/flash-booking-aws.postman_collection.json:417-449`; `scripts/validate-postman.ps1:149-150`; `scripts/validate-postman.ps1:167-171`; `artefatos/idempotencia-postman-run.json` | PASS |

**Spec-anchored outcome**: 15/15 requirements match their specified outcomes. The Local runtime report covers the full Local folder; AWS remains a static contract because the demo is inactive.

## Gate Check

- **Command**: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-postman.ps1`
- **Result**: PASS — `Postman executable documentation validation passed.`
- **Command**: `node .tmp/postman-idempotency/runner/node_modules/newman/bin/newman.js run postman/flash-booking-aws.postman_collection.json --folder "Local | fluxo completo do case" --environment postman/flash-booking.local.postman_environment.json --reporters cli,json --reporter-json-export artefatos/idempotencia-postman-run.json`
- **Result**: PASS — 19/19 HTTP requests and 50/50 Postman assertions; the JSON report is retained in `artefatos/`.
- **PostgreSQL integration evidence**: `target/failsafe-reports/TEST-*.xml` contains 20/20 passing tests across `IdempotencyControllerIT`, `PersistentIdempotencyIT`, `InventoryConcurrencyIT` and `ReservationConcurrencyIT`; sanitized machine-readable summary: `artefatos/idempotencia-evidencia-resumo.json`. These reports existed before T10 and Java production/test sources had no worktree modifications.
- **AWS runtime scope**: no AWS environment was invoked; its endpoint and credentials are intentionally blank.

## Discrimination Sensor

| Mutation | File:line | Detector | Result |
| --- | --- | --- | --- |
| Changed the Local 404 replay assertion from `404` to `418` in `.tmp/postman-idempotency/sensor/`, leaving the real collection intact. | `postman/flash-booking-aws.postman_collection.json:447` | `scripts/validate-postman.ps1:150` | Killed — the scratch validator exited `1` and identified request 19 as missing the required `404` assertion. |

The scratch copy is confined to `.tmp/`. The real collection and validator passed again after this sensor.

## Code Quality

| Check | Result |
| --- | --- |
| Surgical remediation | PASS — the Java business implementation was not changed. |
| Exact outcomes | PASS — named request checks require status, error code and Problem Details. |
| Runner order | PASS — `scripts/validate-postman.ps1:137` and `scripts/validate-postman.ps1:138` enforce both sequences. |
| Repository guidance | PASS — static documentation test follows `.specs/` and `AGENTS.md`. |

## Summary

**Overall**: Ready for a user-run Postman replay demonstration. The Local runtime report proves observable sequential HTTP outcomes; concurrency, rollback and expiry semantics rely on the PostgreSQL integration reports rather than the sequential Runner.
