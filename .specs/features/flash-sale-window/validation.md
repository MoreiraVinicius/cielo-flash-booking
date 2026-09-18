# Flash Sale Window Validation

**Date:** 2026-09-18
**Result:** PASS

## Requirement Evidence

| Requirement | Evidence | Result |
| --- | --- | --- |
| WINDOW-01 | `Event.java`, `CreateEventService.java`, `EventControllerIT.java` create events with null or populated instants. | PASS |
| WINDOW-02 | `JdbcInventoryOperations.java` requires `starts_at IS NULL OR starts_at <= clock_timestamp()`; `ReservationControllerIT` rejects a before-start command. | PASS |
| WINDOW-03 | `JdbcInventoryOperations.java` requires `ends_at IS NULL OR clock_timestamp() < ends_at`; `ReservationControllerIT` rejects an ended command. | PASS |
| WINDOW-04 | `Event.java` accepts end-only values at the ten-minute boundary; `EventControllerIT` returns the persisted end and null start. | PASS |
| WINDOW-05 | `Event.java` validates ordering and ten minutes; invalid HTTP creation returns `400` in `EventControllerIT`. | PASS |
| WINDOW-06 | `JdbcInventoryOperations.java` combines capacity and the PostgreSQL wall clock in one `UPDATE`; `InventoryConcurrencyIT` covers the window outcomes. | PASS |
| WINDOW-07 | `V4__add_event_sale_window.sql` adds nullable columns and direct-write checks; `InitialSchemaIT` proves invalid rows fail. | PASS |
| WINDOW-08 | `EventResponse.java`, `EventPersistenceAdapter.java` and `EventControllerIT` preserve both fields in POST and GET. | PASS |
| WINDOW-09 | `RedisEventAvailabilityCache.java` serializes both fields; `EventControllerIT` reads them after the database row is removed, proving a cache hit. | PASS |
| WINDOW-10 | `ReservationControllerIT` proves before-start and after-end rejections preserve availability and dependent-row counts; it also replays the 409 idempotently. | PASS |

## Gates

| Gate | Command | Result |
| --- | --- | --- |
| Specification | `python .../validate_spec.py .specs/features/flash-sale-window/spec.md` | PASS — 0 errors, 0 warnings |
| Tasks | `python .../validate_tasks.py .specs/features/flash-sale-window/tasks.md` | PASS — 0 errors, 0 warnings |
| Documentation | `pwsh -File scripts/validate-readme.ps1` | PASS — 5 endpoints, 14 images, 42 local references, 3 performance scenarios |
| Postman | `Get-Content postman/flash-booking-aws.postman_collection.json -Raw | ConvertFrom-Json` | PASS |
| Java + integration | `./mvnw.cmd clean verify -Pintegration` with the portable JDK | PASS — 53 unit tests, 68 integration tests, 0 failures, 0 errors |

## Discrimination Sensor

The `ends_at` predicate was temporarily removed from `JdbcInventoryOperations.decrement`. `InventoryConcurrencyIT.refusesDecrementBeforeStartAndAtOrAfterEnd` then failed because an ended event incorrectly accepted a decrement. The original `clock_timestamp() < ends_at` predicate was restored before closure. **Killed.**

## Scope Audit

Updated source, migration, tests, existing demo and high-load specifications, README, data-model document, ADRs, demo runbook, case evaluation, architecture prompt, compose smoke, Postman collection/readme, README validator, teaching transcript, and regenerated teaching audio. No Terraform runtime or AWS resource was changed.
