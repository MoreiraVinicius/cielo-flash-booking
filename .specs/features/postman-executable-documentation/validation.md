# Postman Executable Documentation Validation

**Date**: 2026-09-18
**Spec**: `.specs/features/postman-executable-documentation/spec.md`
**Diff range**: `69a0489..49885c7`
**Verifier**: independent sub-agent (author != verifier)

## Verdict

**PASS** — the Local and signed AWS Runner batteries assert the exact idempotency outcomes required by POSTMAN-10 through POSTMAN-13, and the repository validator rejects an incorrect assertion in a named battery request.

## Task Completion

| Task | Status | Notes |
| --- | --- | --- |
| T01–T09 | Done | T09 remediates the prior verifier findings. |

## Spec-Anchored Acceptance Criteria

| Requirement | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| POSTMAN-01 | Local uses command `:8082`, query `:8081`, no auth. | `postman/flash-booking-aws.postman_collection.json:52`; `postman/flash-booking.local.postman_environment.json:6`; `scripts/validate-postman.ps1:222` | PASS |
| POSTMAN-02 | Local creation is `201`, reads/cancel are `200`, IDs persist. | `postman/flash-booking-aws.postman_collection.json:94`; `postman/flash-booking-aws.postman_collection.json:98`; `postman/flash-booking-aws.postman_collection.json:139`; `postman/flash-booking-aws.postman_collection.json:223` | PASS |
| POSTMAN-03 | Reservation replay returns `201` and the original ID. | `postman/flash-booking-aws.postman_collection.json:164`; `postman/flash-booking-aws.postman_collection.json:165`; `scripts/validate-postman.ps1:137` | PASS |
| POSTMAN-04 | AWS uses SigV4 for `execute-api`. | `postman/flash-booking-aws.postman_collection.json:9`; `postman/flash-booking-aws.postman_collection.json:15`; `scripts/validate-postman.ps1:80` | PASS |
| POSTMAN-05 | Unsigned AWS returns `403`; committed AWS values are empty. | `postman/flash-booking-aws.postman_collection.json:396`; `postman/flash-booking-aws.postman_collection.json:405`; `scripts/validate-postman.ps1:229` | PASS |
| POSTMAN-06 | Collection documents APIs, capacity, cancellation and sale window. | `postman/flash-booking-aws.postman_collection.json:48`; `postman/flash-booking-aws.postman_collection.json:199`; `postman/flash-booking-aws.postman_collection.json:217`; `postman/flash-booking-aws.postman_collection.json:303` | PASS |
| POSTMAN-07 | Static gate rejects malformed or unsafe collection/environment artifacts. | `scripts/validate-postman.ps1:21`; `scripts/validate-postman.ps1:76`; `scripts/validate-postman.ps1:217`; `scripts/validate-postman.ps1:233` | PASS |
| POSTMAN-08 | Creation requests expose safe editable JSON bodies. | `postman/flash-booking-aws.postman_collection.json:64`; `postman/flash-booking-aws.postman_collection.json:131`; `postman/flash-booking-aws.postman_collection.json:276` | PASS |
| POSTMAN-09 | Create responses save and reuse named IDs. | `postman/flash-booking-aws.postman_collection.json:98`; `postman/flash-booking-aws.postman_collection.json:142`; `postman/flash-booking-aws.postman_collection.json:287` | PASS |
| POSTMAN-10 | Both folders offer ordered replay, payload-conflict and target-conflict requests. | `scripts/validate-postman.ps1:96`; `scripts/validate-postman.ps1:115`; `scripts/validate-postman.ps1:132`; `postman/flash-booking-aws.postman_collection.json:316`; `postman/flash-booking-aws.postman_collection.json:609` | PASS |
| POSTMAN-11 | Changed payload/target assert `409`, `resource-conflict`, Problem Details in Local and signed AWS. | `postman/flash-booking-aws.postman_collection.json:328`; `postman/flash-booking-aws.postman_collection.json:329`; `postman/flash-booking-aws.postman_collection.json:346`; `postman/flash-booking-aws.postman_collection.json:621`; `scripts/validate-postman.ps1:139` | PASS |
| POSTMAN-12 | Missing/oversized key assert `400`, `invalid-request`, Problem Details in both flows. | `postman/flash-booking-aws.postman_collection.json:261`; `postman/flash-booking-aws.postman_collection.json:262`; `postman/flash-booking-aws.postman_collection.json:382`; `postman/flash-booking-aws.postman_collection.json:603`; `scripts/validate-postman.ps1:138` | PASS |
| POSTMAN-13 | Capacity-conflict replay asserts persisted `409 resource-conflict` plus Problem Details in both flows. | `postman/flash-booking-aws.postman_collection.json:364`; `postman/flash-booking-aws.postman_collection.json:365`; `postman/flash-booking-aws.postman_collection.json:657`; `scripts/validate-postman.ps1:141` | PASS |

**Spec-anchored outcome**: 13/13 requirements match their specified outcomes.

## Gate Check

- **Command**: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-postman.ps1`
- **Result**: PASS — `Postman executable documentation validation passed.`
- **Runtime scope**: This feature's gate is static contract validation. No Compose or authorized AWS demo was invoked by this review.

## Discrimination Sensor

| Mutation | File:line | Detector | Result |
| --- | --- | --- | --- |
| Changed the Local payload-conflict assertion from expected `409` to `418` in an isolated temporary copy. | `postman/flash-booking-aws.postman_collection.json:328` | `scripts/validate-postman.ps1:139` | Killed — validator exited `1` reporting the missing named-request `409` assertion. |

The temporary copy was removed. The real worktree `git status --porcelain` matched its pre-sensor baseline.

## Code Quality

| Check | Result |
| --- | --- |
| Surgical remediation | PASS — only the named-request assertion contract and validator hardening changed in `69a0489..49885c7`. |
| Exact outcomes | PASS — named request checks require status, error code and Problem Details. |
| Runner order | PASS — `scripts/validate-postman.ps1:132` and `scripts/validate-postman.ps1:133` enforce both sequences. |
| Repository guidance | PASS — static documentation test follows `.specs/` and `AGENTS.md`. |

## Summary

**Overall**: Ready. The previously missing Local `invalid-request` assertion is present, and a wrong Local idempotency status is now detected by the static gate.
