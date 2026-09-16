# AWS Eventual Consistency Visuals Validation

**Result:** PASS
**Date:** 2026-09-16
**Spec:** `.specs/features/eventual-consistency-aws-visuals/spec.md`
**Diff surface:** `89cafa3^..37ee2ae` (subsequent outbox work intentionally excluded)
**Verifier:** independent sub-agent (author != verifier)

## Task Completion

T01–T09 are marked Complete in `tasks.md`. The feature surface is complete through `37ee2ae`; the concurrent outbox work and `.tmp/outbox-scale-sensor-9f2666d` were not changed.

## Spec-Anchored Acceptance Criteria

| AC | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| AWSVIS-01.1 | One PostgreSQL transaction persists stock decrement, PENDING reservation, customer, and both outbox events before `201`. | `docs/images/flash-booking-aws-eventual-consistency.svg:64-70`; `CreateReservationService.java:39-67`; gate facts `scripts/validate-readme.ps1:184-222` | PASS |
| AWSVIS-01.2 | Only event GET is cache-aside; Query API falls back to PostgreSQL and reservation GET is direct. | `flash-booking-aws-eventual-consistency.svg:79-111`; `GetEventService.java:42-75`; gate facts at `scripts/validate-readme.ps1:184-222` | PASS |
| AWSVIS-01.3 | AFTER_COMMIT best-effort eviction, maximum one-second TTL, and cache never authorizes inventory. | `flash-booking-aws-eventual-consistency.svg:90-93`; `EventAvailabilityInvalidationListener.java:20-25`; `RedisEventAvailabilityCache.java:17-18`; gate facts at `scripts/validate-readme.ps1:184-222` | PASS |
| AWSVIS-02.1 | Publisher polls committed outbox and worker consumers publish the two events to separate queues, expire reservations, and request SES. | `flash-booking-aws-eventual-consistency.svg:129-161`; `OutboxSqsPublisher.java:44-76`; `SqsExpirationConsumer.java:30-48`; `SqsReservationCreatedConsumer.java:43-55`; gate facts and routes at `scripts/validate-readme.ps1:184-244` | PASS |
| AWSVIS-02.2 | Consumers are conditional/deduplicated; both flows have DLQ and `maxReceiveCount = 5`. | `flash-booking-aws-eventual-consistency.svg:142-151`; `infra/modules/data-plane/main.tf:67-101`; gate occurrence checks at `scripts/validate-readme.ps1:239-244,336-343` | PASS |
| AWSVIS-02.3 | Healthy convergence is `expiresAt + 5s` and SES request `<= 30s`; async failure does not roll back reservation or `201`. | `flash-booking-aws-eventual-consistency.svg:165,179-199`; `ExpirationReconciler.java:25-27`; gate facts at `scripts/validate-readme.ps1:184-222` | PASS |
| AWSVIS-03.1 | The executed demo view contains the specified edge, private compute, data/async, operations resources, honest lifecycle status, and regional boundaries. | `flash-booking-aws-demo.svg:3,20-32,61-106`; Terraform `infra/modules/edge-observability/main.tf:42-44,155-159,431-475`, `compute/main.tf:202-378`, `data-plane/main.tf:15-107`; gate contract at `scripts/validate-readme.ps1:247-280` | PASS |
| AWSVIS-03.2 | The high-load view is planned/not provisioned, retains three Java modes, and shows Multi-AZ, independent scaling, Aurora/RDS Proxy, Valkey, and separate queues without measured claims. | `flash-booking-aws-high-load.svg:3,18-33,74-117`; AD-006 in `.specs/STATE.md:35-40`; gate contract at `scripts/validate-readme.ps1:283-312` | PASS |
| AWSVIS-03.3 | Three standalone accessible SVGs parse, use native text and routed arrows, and retain the previously rendered no-clipping/no-crossing layout. | XML parse 3/3; `scripts/validate-readme.ps1:316-343` asserts canvas, role, labels, title/desc, no raster/foreignObject, required routes and forbidden crossings. Source inspection: 92/21, 78/12, and 77/11 text/path nodes respectively; the rendered files are unchanged from the native-canvas inspection recorded by T07. | PASS |
| AWSVIS-03.4 | Four named obsolete assets are absent and every remaining image has a documentary Markdown reference. | Exact absence and inventory checks at `scripts/validate-readme.ps1:135-170`; gate reported 13 images and 41 local references. | PASS |

**Spec-anchored status:** 10/10 ACs matched their precise outcome; no spec-precision gaps.

## Gate Check

| Gate | Result |
| --- | --- |
| README/documentation | PASS — PowerShell 7.6.5: `validate-readme: PASS - 5 endpoints, 13 images, 41 local references, 3 performance scenarios`. The Windows PowerShell `powershell -File` alias is blocked by local execution policy and parses this UTF-8-without-BOM file incorrectly; this is host policy/legacy encoding behavior, not a contract failure. |
| Spec | PASS — `validate_spec.py`: 0 errors, 0 warnings. |
| Tasks | PASS — `validate_tasks.py`: 0 errors; 3 existing granularity warnings (T06–T08), none invalidating dependencies or task gates. |
| XML/accessibility | PASS — all three SVGs parse as standalone XML with expected viewBox, `role=img`, `aria-labelledby`, non-empty title and description, and no `foreignObject`/raster image. |
| Diff hygiene | PASS — `git diff --check 89cafa3^..37ee2ae`. |

## Discrimination Sensor

| Mutation | Scratch-only change | Result |
| --- | --- | --- |
| Direct SQS → SES route | Added `M872 834 H1158` while retaining the worker → SES arrow in a copied consistency SVG outside the repository `.tmp`. | Killed, exit 1: `retains an obsolete or misleading fact: M872 834 H1158`. |

The real-tree porcelain was identical before and after the sensor; `.tmp/outbox-scale-sensor-9f2666d` still existed after cleanup.

## Code Quality

The feature is documentation-only and stays within its intended scope: three native accessible SVGs, README integration, asset cleanup, and a deterministic documentation contract. It neither changes Java/Terraform behavior nor presents the high-load target as provisioned. Every contract assertion maps to an AC, an edge case, or image-inventory hygiene.

## Summary

**Overall:** Ready.

**Spec-anchored check:** 10/10 ACs passed.
**Gate:** PASS (documentation, spec, tasks, XML/accessibility, inventory, diff).
**Sensor:** 1/1 mutation killed.
