# Resumo executivo da performance do evento: validacao

**Date**: 2026-09-29
**Spec**: `.specs/features/event-executive-summary/spec.md`
**Diff range**: `78de884..b4bca06`
**Verifier**: independent sub-agent (author != verifier)

## Validation: resumo executivo - PASS

**Verdict**: PASS

## Task Completion

| Task | Status | Notes |
| --- | --- | --- |
| T01-T09 | Complete | All nine tasks are marked Complete in `tasks.md`; task commits are present through `b4bca06`. T09 adds the corrective evidence requested by the prior validation. |

## Spec-Anchored Acceptance Criteria

Every criterion below was checked against its specified outcome and an assertion. Line citations refer to committed files in `b4bca06`. The implementation of the inclusive activation comparison is also cited where useful.

| Requirement | Spec-defined outcome | `file:line` + assertion expression | Result |
| --- | --- | --- | --- |
| EXECSUM-01 | Exactly one global control row starts disabled. | `src/test/java/com/cielo/flashbooking/event/summary/ExecutiveSummarySchemaIT.java:40-46` - `enabled` is false, row count is `1`, duplicate singleton insert fails. | PASS |
| EXECSUM-02 | Enabling persists `enabled=true` and `enabledAt`, without immediately creating a report. | `ExecutiveSummaryActivationIT.java:58-63` - HTTP 200, `enabled=true`, `enabledAt` present, report count `isZero()`. | PASS |
| EXECSUM-03 | Repeated enable retains the same `enabledAt`; disabling clears the active window and is idempotent. | `ExecutiveSummaryActivationIT.java:70-76, 80-98` - timestamp equals the first window start; repeated disable remains false with null `enabledAt`. | PASS |
| EXECSUM-04 | Events starting at or after this activation window and closed while enabled are eligible. | `EventSummarySchedulerIT.java:92-120, 124-142` - an in-window closed event creates exactly one report and invokes analysis; a prior-window event creates none. `JdbcExecutiveSummaryReportStore.java:47-51` uses `COALESCE(starts_at, created_at) >= enabled_at` and requires `ends_at <= clock_timestamp()`. | PASS |
| EXECSUM-05 | Enabling after an event started excludes that event and causes no analysis. | `EventSummarySchedulerIT.java:134-142` - event started before reactivation has zero reports; `verify(signals, never())` and `verify(narrative, never())`. | PASS |
| EXECSUM-06 | While disabled, no report or analysis work occurs; GET returns `DISABLED` with null Markdown. | `EventSummarySchedulerIT.java:78-88` - no row and both adapters `never()`; `ExecutiveSummaryQueryIT.java:61-70` - HTTP 200, exact `DISABLED`, null Markdown and delivery status. | PASS |
| EXECSUM-07 | Missing, null, or non-boolean `enabled` returns HTTP 400 without changing activation state. | `ExecutiveSummaryActivationIT.java:99-113` - three invalid bodies return bad request and `enabledAt` remains equal to its prior value. | PASS |
| EXECSUM-08 | The first post-close scan waits for an in-flight inventory transaction, then persists and delivers one report based on committed reservations. | `EventSummarySchedulerIT.java:146-197` - scan future remains incomplete while inventory row is locked; after commit accepted tickets equal `1` and Markdown contains the reservation. `ExecutiveSummaryDeliveryServiceTest.java:40-50` asserts persisted content is the single publish payload and records the confirmed result. | PASS |
| EXECSUM-09 | Before `endsAt`, event remains unclaimed and external analysis is not called. | `EventSummarySchedulerIT.java:78-88` - future `ends_at`, zero report rows, CloudWatch and narrative `never()`; `ExecutiveSummaryQueryIT.java:74-79` asserts `SCHEDULED`. | PASS |
| EXECSUM-10 | Concurrent scans allow one claim, one Bedrock attempt and one Discord attempt. | `EventSummarySchedulerIT.java:98-120` - two parallel scans, report count `1`, one signal/narrative call; `ExecutiveSummaryDeliveryIT.java:53-68` - second delivery claim is empty and persisted status transitions once. | PASS |
| EXECSUM-11 | Show start (`startsAt` or `createdAt` fallback), end and as-of in Sao Paulo time. | `ExecutiveSummaryFactsIT.java:113-130` - null `starts_at` resolves to `createdAt`, and Markdown contains exact local start/end plus as-of; `ExecutiveSummaryRendererTest.java:29-31` asserts all three rendered timestamps. | PASS |
| EXECSUM-12 | Show event capacity, accepted reservation count and accumulated tickets. | `ExecutiveSummaryFactsIT.java:47-50` - exact values `4` reservations and `15` tickets; `ExecutiveSummaryRendererTest.java:22-25` asserts capacity and distinct accepted/valid lines. | PASS |
| EXECSUM-13 | Close-valid tickets exclude cancellations through close and expirations at or before close. | `ExecutiveSummaryFactsIT.java:47-52` - valid `10`, cancelled `2`, expired `3` for a fixture spanning the close boundary. | PASS |
| EXECSUM-14 | Record the first zero-availability instant only while enabled, preserve it after restock, and render elapsed time or no-zero wording. | `InventoryConcurrencyIT.java:107-130` - disabled decrement leaves timestamp null; enabled depletion records it and restock does not replace it. `ExecutiveSummaryRendererTest.java:26` asserts elapsed wording; `:44-45` asserts no-zero wording. | PASS |
| EXECSUM-15 | Show the highest one-minute accepted ticket volume. | `ExecutiveSummaryFactsIT.java:53-54` asserts peak minute and value `10`; `ExecutiveSummaryRendererTest.java:63-65` renders the peak. | PASS |
| EXECSUM-16 | For a window of at least ten minutes with accepted reservations, show first-five-minute share. | `ExecutiveSummaryFactsIT.java:55` asserts first-five count `5`; `ExecutiveSummaryRendererTest.java:28` asserts the exact `50%` share. | PASS |
| EXECSUM-17 | With no accepted reservations, show zero and omit rhythm indicators without inferring no interest. | `ExecutiveSummaryFactsIT.java:76-81` asserts zero and null rhythm metrics; `ExecutiveSummaryRendererTest.java:37-47` asserts “Nenhuma reserva foi aceita” and absence of peak/first-five wording; `BedrockExecutiveNarrativeTest.java:71-75` asserts no inference call. | PASS |
| EXECSUM-18 | Explain cancellation and expiry counts in plain language without internal statuses. | `ExecutiveSummaryRendererTest.java:27, 33-34` asserts cancellation/expiry sentence and excludes `PENDING`/`EXPIRED`. | PASS |
| EXECSUM-19 | Distinguish accumulated accepted volume from valid close volume and include the fixed temporary-reservation caveat. | `ExecutiveSummaryRendererTest.java:22-25, 33, 75` asserts separate numeric statements and the exact caveat; facts are `15` accumulated versus `10` valid at close in `ExecutiveSummaryFactsIT.java:49-52`. | PASS |
| EXECSUM-20 | Show at most two friendly operational notes as environment signals, without causal attribution. | `ExecutiveSummaryRendererTest.java:55-69` asserts two allowed labels, third omitted, and exact non-causal environment qualifier; `:75-91` asserts the same neutral wording for two events. | PASS |
| EXECSUM-21 | Empty alert history omits the alert section; partial/unavailable checks show the exact limitation. | `ExecutiveSummaryRendererTest.java:37-50` checks omission and fixed partial-history note; `CloudWatchOperationalSignalsReaderTest.java:54-86` distinguishes EMPTY, PARTIAL, UNAVAILABLE. | PASS |
| EXECSUM-22 | Bedrock receives only permitted aggregates, prompt <=2 KB, output <=120 tokens, at most one low-randomness call. | `BedrockExecutiveNarrativeTest.java:33-52` asserts one `converse`, Nova Micro, 120-token cap, temperature, byte bound, allowed fields and PII/log/alarm-name exclusions; `EventSummarySchedulerIT.java:119-120` asserts a single narrative call for concurrent scans. | PASS |
| EXECSUM-23 | Failed, truncated, or unsafe AI output is omitted; deterministic facts remain and inference is not retried. | `BedrockExecutiveNarrativeTest.java:55-68` rejects digit, purchase, causality, excessive sentences/tokens; `EventSummarySchedulerIT.java:254-272` asserts durable `PARTIAL` and no second narrative call. | PASS |
| EXECSUM-24 | GET returns persisted status, Markdown, timestamps and delivery state without recomputation; unknown event returns 404. | `ExecutiveSummaryQueryIT.java:61-104` asserts exact statuses/fields, stable stored Markdown after source mutation and `never()` for CloudWatch, Bedrock and Discord; `:106-118` asserts 404. | PASS |
| EXECSUM-25 | A shared alarm is contextualized as an environment signal, never attributed to either event. | `ExecutiveSummaryRendererTest.java:75-91` renders the same signal for two event instances; both contain neutral qualifier and `doesNotContain("causou")`. | PASS |
| EXECSUM-26 | Inconsistent close aggregates yield `PARTIAL`, incomplete wording, omitted inconsistent numbers and no Bedrock call. | `ExecutiveSummaryFactsIT.java:90-101` asserts inconsistent partition; `EventSummarySchedulerIT.java:200-229` asserts `PARTIAL`, omission and `never()` for CloudWatch/narrative on that fixture. | PASS |
| EXECSUM-27 | Disabling before close prevents generation; a later activation does not recover that event. | `EventSummarySchedulerIT.java:124-142` closes the event while disabled, reactivates, scans, and asserts no report or adapter calls; a pre-window event is also excluded. | PASS |
| EXECSUM-28 | Invalid global activation input returns HTTP 400 without state mutation. | `ExecutiveSummaryActivationIT.java:99-113` - exact 400 and unchanged `enabledAt`. | PASS |
| EXECSUM-29 | Missing Discord ARN stores `NOT_CONFIGURED` without secret lookup and preserves the report. | `ExecutiveSummaryDeliveryServiceTest.java:31-36` verifies not-configured state and `never()` begin/publish; `EventSummarySchedulerIT.java:106-117` asserts report and Markdown remain persisted. | PASS |
| EXECSUM-30 | Definitive Discord error is FAILED; ambiguous delivery is UNKNOWN; no retry occurs. | `SecretsManagerDiscordSummaryPublisherTest.java:51-75` asserts invalid URL/definitive failure and timeout UNKNOWN; `ExecutiveSummaryDeliveryIT.java:53-68` asserts second claim empty and SENT confirmation persistence. | PASS |
| EXECSUM-31 | Reservation creation and expiry use the PostgreSQL inventory-decision instant after lock wait. | `CreateReservationServiceTest.java:52-64` asserts createdAt equals returned decision instant and expiry is ten minutes later; `InventoryConcurrencyIT.java:156-163` asserts returned timestamp follows a held inventory lock. | PASS |
| EXECSUM-32 | No `endsAt` or an unknown event yields no summary facts. | `ExecutiveSummaryFactsIT.java:104-109` - reads for both open/no-end and unknown UUID are empty. | PASS |
| EXECSUM-33 | Persisted claim and Markdown survive interruption before external work and are not retried. | `EventSummarySchedulerIT.java:232-251` manually persists claim, asserts one durable `PARTIAL` Markdown, and verifies CloudWatch, Bedrock and delivery are never called afterward. | PASS |
| EXECSUM-34 | A new activation window does not retroactively claim events from outside that window. | `EventSummarySchedulerIT.java:131-142` - event closes while disabled and event started before reactivation both remain unreported after scan; external adapters `never()`. | PASS |
| EXECSUM-35 | Aggregation ends at commercial `endsAt`; an event without commercial end is not summarized. | `ExecutiveSummaryFactsIT.java:32-57` asserts exact close aggregates; fixture helper adds reservations around close; `:104-109` asserts no facts for no-end event. | PASS |
| EXECSUM-36 | GET is read-only and invokes no aggregation or external analysis/delivery adapters. | `ExecutiveSummaryQueryIT.java:93-104` repeats GET then asserts CloudWatch `.read`, Bedrock `.write`, and Discord `.publish` are all `never()`. | PASS |
| EXECSUM-37 | Facts are event-scoped; windows shorter than ten minutes omit first-five indicator. | `ExecutiveSummaryFactsIT.java:61-86` reads two independent events and asserts separate totals plus null first-five metric for short/empty cases. | PASS |
| EXECSUM-38 | Concurrent inventory acceptance uses database decision time for first-zero and rhythm data. | `InventoryConcurrencyIT.java:156-163` holds the row lock and proves acceptedAt follows it; `CreateReservationServiceTest.java:62-64` passes that instant into reservation timestamps. | PASS |
| EXECSUM-39 | Configured webhook obtains URL lazily from Secrets Manager and publishes persisted text once. | `ExecutiveSummaryDeliveryServiceTest.java:40-61` asserts ordered begin/publish/finish and no duplicate publish; `SecretsManagerDiscordSummaryPublisherTest.java:31-49` asserts lazy cached secret and validated Discord HTTPS URL. | PASS |
| EXECSUM-40 | Runbook instructs webhook creation, SecretString storage and ARN-only local `demo.tfvars` configuration. | `infra/environments/demo/README.md:7-18` documents SecretString and ARN-only configuration; `:20-49` provides activation, GET and disable commands. | PASS |
| EXECSUM-41 | Confirmed Discord success persists SENT and confirmation time. | `ExecutiveSummaryDeliveryIT.java:63-70` asserts `SENT` and non-null database confirmation timestamp. | PASS |
| EXECSUM-42 | Missing ARN persists NOT_CONFIGURED without Secrets Manager or HTTP lookup. | `ExecutiveSummaryDeliveryServiceTest.java:31-36` asserts state transition and `never()` for delivery claim and publisher. | PASS |
| EXECSUM-43 | Definitive failure -> FAILED; ambiguous response -> UNKNOWN; neither is retried. | `SecretsManagerDiscordSummaryPublisherTest.java:60-75` exact FAILED/UNKNOWN; `ExecutiveSummaryDeliveryIT.java:53-59` second attempt claim is empty. | PASS |
| EXECSUM-44 | Long Discord content is <=2,000 chars and preserves results, first exhaustion, rhythm and caveat. | `DiscordSummaryMessageFormatterTest.java:12-26` asserts length bound and required sections/caveat remain while optional sections are removed. | PASS |

**Status**: 44/44 covered; 0 gaps; 0 spec-precision gaps.

## Edge Cases

- [x] Shared infrastructure alert appears for two events with environment-only, non-causal wording (`ExecutiveSummaryRendererTest.java:75-91`).
- [x] Inconsistent accepted/close partition is incomplete, omits numbers and skips external analysis (`EventSummarySchedulerIT.java:200-229`).
- [x] Disable before close and reactivation does not recover event (`EventSummarySchedulerIT.java:124-142`).
- [x] Invalid activation body returns 400 without state change (`ExecutiveSummaryActivationIT.java:99-113`).
- [x] No webhook ARN persists report without publisher call (`ExecutiveSummaryDeliveryServiceTest.java:31-36`).
- [x] Discord failure/ambiguity persists delivery state without retry (`ExecutiveSummaryDeliveryIT.java:53-68`).
- [x] No accepted reservations omits AI call and rhythm indicators (`ExecutiveSummaryRendererTest.java:37-50`; `BedrockExecutiveNarrativeTest.java:71-75`).
- [x] Inventory lock wait acceptance time is used for reservation dates (`InventoryConcurrencyIT.java:156-163`; `CreateReservationServiceTest.java:52-64`).

## Discrimination Sensor

The real-tree porcelain status was captured before sensor work. `git worktree add` was denied by read-only `.git` permissions, so the fallback was a full tracked `git archive` copy at the explicitly writable visualization root. Both faults and all generated build artifacts stayed in that isolated copy, which was removed after the tests. Real-tree `git status --porcelain` SHA-256 before and after was identical: `68B135140C39429E5B8FCF2B9C4979F39BA2A52AB79CFEF3DE092E7066079C5B`.

| Mutation | File:line | Description | Killed? |
| --- | --- | --- | --- |
| 1 | `src/main/java/com/cielo/flashbooking/event/summary/ExecutiveSummaryRenderer.java:85` | Removed explicit phrase confirming no relationship between environment signals and the event. `ExecutiveSummaryRendererTest` failed both neutral-wording assertions. | Killed |
| 2 | `src/main/java/com/cielo/flashbooking/adapter/out/persistence/summary/JdbcExecutiveSummaryReportStore.java:49` | Shifted the activation lower bound by one hour. `EventSummarySchedulerIT.concurrentScansPersistOneClaimAndRunExternalAnalysisOnlyOnce` failed because expected report count `1` was `0`. | Killed |

**Sensor depth**: lightweight, two behavior-level mutants. **Result**: 2/2 killed; real-tree status unchanged.

## Code Quality

| Principle | Status |
| --- | --- |
| No features beyond the approved scope | PASS |
| No single-use abstractions or speculative flexibility | PASS |
| Changes remain within feature, persistence, API, infra and runbook boundaries | PASS |
| Existing Spring/JDBC/Terraform patterns followed | PASS |
| Tests assert spec outcomes rather than only successful execution | PASS |
| Domain/data calculations have direct integration coverage; both routes and error paths have tests | PASS |
| Tests in scope map to an acceptance criterion or specified edge case | PASS |
| Guidance followed: repository `AGENTS.md`, `.specs/STATE.md`, and `java-spring-engineering` / bounded Spring skills | PASS |

No interactive UAT was run: this change exposes backend APIs and an operator runbook, not a user interface. All external AWS/Discord dependencies in tests are mocked or local emulations; no live requests or deploy occurred.

## Gate Check

- **Gate command**: `mvnw.cmd -Dmaven.repo.local=C:\Users\vinic\.m2\repository verify -Pintegration`; tasks record `clean verify -Pintegration`.
- **Independent result**: elevated `verify -Pintegration` completed with BUILD SUCCESS: 81 unit tests + 95 integration tests, 176 total, 0 failures, 0 errors, 0 skipped. Docker access in this verifier required elevated test execution. Testcontainers shutdown produced background scheduler/exporter logs, but the Surefire/Failsafe summary was clean.
- **Clean-gate note**: the first local `clean verify -Pintegration` attempt could not delete `target` due workspace file permissions. The subsequent elevated full `verify -Pintegration` passed; the orchestrator reports its latest clean gate also passed.
- **Test integrity**: source annotations increased from 125 before `78de884` to 169 at `b4bca06` (+44); executed total is 176 due parameterized/repeated cases. No test deletion or weakened assertion was found in the feature diff.
- **Terraform format**: targeted `terraform fmt -check` on all touched Terraform files passed. Recursive repository-wide format was blocked by access denied in unrelated `artefatos/video-deps` directories; the orchestrator records the recursive check passing before those unrelated files were present.
- **Terraform tests**: compute 2 passed/0 failed; edge-observability 1 passed/0 failed.
- **Diff check**: `git diff --check 78de884^ b4bca06` passed.
- **Skipped tests**: none.
- **Failures**: none in the successful feature gates. The two deliberately mutated scratch runs failed their expected assertions and are recorded above.

## Requirement Traceability Update

The orchestrator updated EXECSUM-01..44 to `Verified`, marked all goals and success criteria complete, and changed the feature status to independently verified after this PASS.

| Requirement | Previous Status | Verified Status |
| --- | --- | --- |
| EXECSUM-01..EXECSUM-44 | Pending | Verified (44/44, evidence above and source traceability table) |

## Summary

**Overall**: PASS - feature complete and independently verified.

**Spec-anchored check**: 44/44 criteria match their specified outcomes; 0 precision gaps.
**Sensor**: 2/2 behavior mutations killed.
**Gate**: 176 tests passed, 0 failed/skipped; Terraform 3/3 runs passed; targeted fmt and diff checks passed.

**What works**: Global manual opt-in; automatic report generation at event close; PostgreSQL-backed, reservation-aware facts; concise neutral Portuguese report; one bounded optional Bedrock read; no-repeat Discord delivery with configured secret; read-only GET; operator setup documentation.

**Issues found**: None in the feature scope. The repo-wide fmt command could not inspect unrelated locked content; changed Terraform files were checked directly.

**Next steps**: None. No code changes are requested by this verifier.
