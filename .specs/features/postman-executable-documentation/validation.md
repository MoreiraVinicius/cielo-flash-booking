# Postman Executable Documentation Validation

**Date**: 2026-09-18  
**Spec**: `.specs/features/postman-executable-documentation/spec.md`  
**Diff range**: `55a820c..7ad612c`  
**Verifier**: independent sub-agent (author != verifier)

---

## Verdict

**PASS** — the single Postman Collection v2.1, its Local and AWS environments, guide, and executable structural validator meet POSTMAN-01 through POSTMAN-07. The declared static gate passed. Runtime execution against Compose and a deployed AWS demo was not performed: the feature gate is intentionally structural, and no active AWS endpoint is authorized or committed.

## Task Completion

| Task | Status | Notes |
| --- | --- | --- |
| T01 | Done | Contract and traceability committed. |
| T02 | Done | Existing collection was extended; no second collection was added. |
| T03 | Done | Importable Local environment added. |
| T04 | Done | Importable, blank-safe AWS environment added. |
| T05 | Done | Import and execution guide added. |
| T06 | Done | Repository structural validator added. |

## Spec-Anchored Acceptance Criteria

| Requirement | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| POSTMAN-01 | Local commands use `http://localhost:8082`, reads use `http://localhost:8081`, and no authentication is sent. | `postman/flash-booking.local.postman_environment.json:6-15` supplies the two URLs; `postman/flash-booking-aws.postman_collection.json:52-54` applies `noauth`; `scripts/validate-postman.ps1:53-58,126-129` rejects absent Local flow, wrong auth, URLs, or fixture. | PASS |
| POSTMAN-02 | Ordered Local flow asserts `201` for creations, `200` for both reads and cancellation, and retains generated IDs. | `postman/flash-booking-aws.postman_collection.json:93-97` asserts event `201` and stores `eventId`; `:115-116` asserts event read `200`; `:137-140` asserts reservation `201` and stores `reservationId`; `:179-181` asserts reservation read `200`; `:220-222` asserts cancellation `200`; `:239-240` asserts post-cancel read `200`. | PASS |
| POSTMAN-03 | Replaying the same reservation idempotency key asserts `201` and the original reservation identifier. | `postman/flash-booking-aws.postman_collection.json:146-163` reuses `reservationKey` and asserts `201` plus `response.id === reservationId`. | PASS |
| POSTMAN-04 | AWS requests inherit SigV4 configured for `execute-api` once temporary environment values are supplied. | `postman/flash-booking-aws.postman_collection.json:8-16` declares collection-level `awsv4`, `execute-api`, and environment credential variables; `:313-315` documents inheritance; `scripts/validate-postman.ps1:49-51` enforces schema, auth type, and service. | PASS |
| POSTMAN-05 | The unsigned AWS boundary is `403`; committed AWS URL, temporary keys/token, and email are blank. | `postman/flash-booking-aws.postman_collection.json:317-329` overrides only the boundary request to `noauth` and asserts `403`; `postman/flash-booking.aws.postman_environment.json:6-45` contains blank endpoint, recipient and credentials; `scripts/validate-postman.ps1:94-95,131-138` enforces both properties. | PASS |
| POSTMAN-06 | Names/descriptions document setup, split Command/Query APIs, idempotency, capacity, cancellation, and sale window; a future sale rejects reservation before `startsAt` with `409 application/problem+json`. | `postman/flash-booking-aws.postman_collection.json:52-54,131,155,196,214,274,298` documents the required contract topics; `:265-305` creates a future event, asserts returned `startsAt`, then asserts `409` and `application/problem+json`; `postman/README.md:20-50` gives ordered local execution and expected outcomes. | PASS |
| POSTMAN-07 | Validator rejects malformed JSON, missing environment variables/key assertions, active AWS values, non-empty AWS secret/recipient values, and retired personal/endpoint data. | `scripts/validate-postman.ps1:21,44-47` parses all artifacts and fails malformed JSON; `:97-110` checks key assertions and a 40-test floor; `:112-124` checks keys in both environments; `:131-138` requires blank AWS values and rejects the retired personal address/endpoint. Gate executed successfully. | PASS |

**Spec-anchored result**: 7/7 requirements match their specified outcomes; no spec-precision gaps.

## Edge Cases

- [x] Blank AWS values have no fallback endpoint or credentials: `postman/flash-booking.aws.postman_environment.json:6-45`; enforced by `scripts/validate-postman.ps1:131-138`.
- [x] Future sale returns and checks `startsAt` before the pre-opening reservation: `postman/flash-booking-aws.postman_collection.json:280-305`.
- [x] Problem responses assert content type as well as status: `postman/flash-booking-aws.postman_collection.json:202-204,258-260,304-305,470-472`.

## Discrimination Sensor

The real worktree was never mutated. A temporary directory under the system temp path received copies of only the collection, both environments, and `scripts/validate-postman.ps1`. In that copy, Local `commandBaseUrl` was changed from `http://localhost:8082` to `http://localhost:9999`; the copied validator exited `1` with `Local commandBaseUrl must target Command API port 8082.` The temporary directory was removed.

| Mutation | Target | Expected detector | Killed? |
| --- | --- | --- | --- |
| Wrong Local Command API endpoint (`:8082` -> `:9999`) | Scratch `postman/flash-booking.local.postman_environment.json` value corresponding to production `:6-10` | `scripts/validate-postman.ps1:127` | Yes — exit 1 |

**Sensor depth**: lightweight, behavior-level environment regression.  
**Isolation**: real `git status --porcelain` before and after sensor was identical (`.tmp/`, two pre-existing audio artifacts, and the pre-existing audio script only).

## Gate Check

- **Gate command**: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-postman.ps1`
- **Result**: PASS — `Postman executable documentation validation passed.`
- **Collection assertion count**: 10 before this diff, 40 after this diff (no assertions were removed).
- **Failures / skipped tests**: none. This feature's declared gate is static validation; Postman/Newman runtime execution is not part of it.
- **Diff hygiene**: `git diff --check 55a820c..7ad612c` produced no whitespace errors.

## Code Quality

| Principle | Status |
| --- | --- |
| Minimum code / one collection preserved | PASS — `postman/flash-booking-aws.postman_collection.json:4-6` states the single collection and `postman/README.md:3-8` explains both flows. |
| Surgical scope | PASS — diff is limited to feature spec/tasks, existing collection, two environments, guide, and validator. |
| No scope creep | PASS — no AWS deployment, credentials, active URL, or Java behavior changed. |
| Matches repository guidance | PASS — follows `.specs/` source-of-truth requirement in `AGENTS.md:3-7` and existing Docker-local workflow in `README.md:21-31`. |
| Assertions map to requirements | PASS — every collection scenario in scope is represented in the AC table above; the validator verifies required names/assertions/variables. |

## Requirement Traceability

| Requirement | Previous Status | Validation Result | Spec mutation |
| --- | --- | --- | --- |
| POSTMAN-01 | Implementing | Verified by this report | Not changed by independent verifier; `validate_state.py` does not require a status mutation. |
| POSTMAN-02 | Implementing | Verified by this report | Not changed by independent verifier; `validate_state.py` does not require a status mutation. |
| POSTMAN-03 | Implementing | Verified by this report | Not changed by independent verifier; `validate_state.py` does not require a status mutation. |
| POSTMAN-04 | Implementing | Verified by this report | Not changed by independent verifier; `validate_state.py` does not require a status mutation. |
| POSTMAN-05 | Implementing | Verified by this report | Not changed by independent verifier; `validate_state.py` does not require a status mutation. |
| POSTMAN-06 | Implementing | Verified by this report | Not changed by independent verifier; `validate_state.py` does not require a status mutation. |
| POSTMAN-07 | Implementing | Verified by this report | Not changed by independent verifier; `validate_state.py` does not require a status mutation. |

## Summary

**Overall**: Ready. The collection is a safe executable guide for Local and authorized AWS usage, and its structural validator both passes on the real artifacts and kills an isolated endpoint regression. No lessons were recorded because validation had no failed requirement, precision gap, or surviving mutation.
