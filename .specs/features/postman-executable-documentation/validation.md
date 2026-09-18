# Postman Executable Documentation Validation

**Date**: 2026-09-18  
**Spec**: `.specs/features/postman-executable-documentation/spec.md`  
**Diff range**: `366a93e..59b7906`
**Verifier**: independent sub-agent (author != verifier)

---

## Verdict

**PASS** — the existing single collection remains a safe Local/AWS executable guide and now visibly teaches editable creation payloads plus automatic response chaining. The static gate passed and killed an isolated regression that removed ID capture.

## Task Completion

| Task | Status | Notes |
| --- | --- | --- |
| T01–T06 | Done | Previously validated in `validation.md` for `55a820c..7ad612c`. |
| T07 | Done | Payload examples, response capture, presentation instructions and validator coverage are present in `59b7906`. |

## Spec-Anchored Acceptance Criteria

| Requirement | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| POSTMAN-01 | Local uses Command `:8082`, Query `:8081` and no auth. | `postman/flash-booking.local.postman_environment.json:6-15`; `postman/flash-booking-aws.postman_collection.json:52-54`; `scripts/validate-postman.ps1:53-58,156-166`. | PASS |
| POSTMAN-02 | Ordered Local flow asserts creation `201`, reads/cancellation `200`, and retains IDs. | `postman/flash-booking-aws.postman_collection.json:93-98,116-117,138-142,181,222-224,241`; `scripts/validate-postman.ps1:105-119`. | PASS |
| POSTMAN-03 | Same idempotency key returns `201` and the original reservation ID. | `postman/flash-booking-aws.postman_collection.json:148-163`. | PASS |
| POSTMAN-04 | Authorized AWS requests inherit SigV4 for `execute-api`. | `postman/flash-booking-aws.postman_collection.json:8-16,313-315`; `scripts/validate-postman.ps1:49-51`. | PASS |
| POSTMAN-05 | Unsigned AWS boundary is `403`; committed AWS endpoint, credentials and recipient are blank. | `postman/flash-booking-aws.postman_collection.json:317-329`; `postman/flash-booking.aws.postman_environment.json:6-45`; `scripts/validate-postman.ps1:94-95,161-166`. | PASS |
| POSTMAN-06 | Collection documents preparation, API split, idempotency, capacity, cancellation and sale window; pre-opening reservation is `409 application/problem+json`. | `postman/flash-booking-aws.postman_collection.json:52-54,148-163,196-224,267-308`; `postman/README.md:20-38`; `scripts/validate-postman.ps1:97-119`. | PASS |
| POSTMAN-07 | Gate rejects malformed/incomplete artifacts, missing assertions, active AWS values and retired personal data. | `scripts/validate-postman.ps1:21,44-47,97-119,154-168`; executed successfully. | PASS |
| POSTMAN-08 | Creation requests show editable JSON bodies with required fields and safe fixtures or declared variables. | `postman/flash-booking-aws.postman_collection.json:57-66,123-132,267-276,340-345,395-404`; `postman/README.md:41-49`; `scripts/validate-postman.ps1:121-133`. | PASS |
| POSTMAN-09 | Returned IDs are saved as named collection variables and the following request's variable is documented. | `postman/flash-booking-aws.postman_collection.json:48,97-110,141-142,285-301,370-381,413-414`; `postman/README.md:51-58`; `scripts/validate-postman.ps1:135-151`. | PASS |

**Spec-anchored result**: 9/9 requirements match the specified outcomes; no spec-precision gaps.

## Edge Cases

- [x] An AWS environment has no default endpoint or credentials: `scripts/validate-postman.ps1:161-166`.
- [x] Future-sale timestamps are generated, returned and used before the pre-opening `409`: `postman/flash-booking-aws.postman_collection.json:267-308`.
- [x] Creation request 01 clears prior captured IDs before creating a fresh chain: `postman/flash-booking-aws.postman_collection.json:74-84`.
- [x] The presentation guide identifies Postman's **Variables / Current value** view as the observable proof of capture: `postman/README.md:51-58`.

## Discrimination Sensor

The real worktree was not mutated. A temporary directory received copies of the collection, both environments and the validator. In that copy, the required call `pm.collectionVariables.set('reservationId', reservation.id)` was replaced by a broken value. The copied validator exited `1` at its required-teaching-fragment check. The temporary directory was removed and the real `git status --porcelain` was byte-for-byte unchanged.

| Mutation | Target | Expected detector | Killed? |
| --- | --- | --- | --- |
| Remove semantic capture of `reservation.id` | Scratch copy of `postman/flash-booking-aws.postman_collection.json`, production equivalent `:141` | `scripts/validate-postman.ps1:135-147` | Yes — exit 1: missing required response-capture fragment |

**Sensor depth**: lightweight, behavior-level response-chaining regression.
**Result**: 1/1 killed — PASS.

## Gate Check

- **Gate command**: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-postman.ps1`
- **Result**: PASS — `Postman executable documentation validation passed.`
- **Test count**: one repository static-contract gate; it requires at least 40 Postman `pm.test` assertions (`scripts/validate-postman.ps1:119`), and no assertions were removed in the reviewed diff.
- **Skipped runtime tests**: Postman/Newman requests against Compose or AWS were not run. The declared task gate is structural; no active AWS endpoint is committed or authorized.
- **Diff hygiene**: `git diff --check 366a93e..59b7906` produced no whitespace errors.

## Code Quality

| Principle | Status |
| --- | --- |
| Minimum code / no second collection | PASS — only the existing collection was extended: `postman/README.md:3-8`. |
| Surgical scope | PASS — the diff is limited to the feature spec/tasks, existing collection, guide and static validator. |
| No scope creep | PASS — no deployment, credential, infrastructure or Java behavior changed. |
| Assertions map to requirements | PASS — validator checks sample payload fields, capture calls, variable references and guide language: `scripts/validate-postman.ps1:121-151`. |
| Project guidance | PASS — follows the `.specs/` source-of-truth policy in `AGENTS.md:3-7`. |

## Requirement Traceability

| Requirement | Previous Status | Validation Result | Spec mutation |
| --- | --- | --- | --- |
| POSTMAN-01–POSTMAN-07 | Verified | Retained | None by the independent verifier. |
| POSTMAN-08 | Implementing | Verified by this report | None by the independent verifier. |
| POSTMAN-09 | Implementing | Verified by this report | None by the independent verifier. |

## Summary

**Overall**: Ready. A presenter can open the body examples, send creation requests, show the `Current value` of collection variables, and explain exactly how each response ID is consumed next. No lessons were recorded: all requirements passed, and the injected regression was killed.
