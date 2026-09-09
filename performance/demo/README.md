# Demo performance baseline

`run.ps1` starts the local Compose environment with two additional `command-api` processes, exercises queries, reservations, and mixed traffic with k6, captures k6 summaries, then records Valkey hit/miss counters and PostgreSQL connection/lock-wait snapshot. It always tears down the local stack.

The initial baseline uses intentionally small, reproducible batches: five query VUs, three command VUs, and four mixed VUs for 15 seconds each. Run `./performance/demo/run.ps1`; generated evidence is kept outside Git under `performance/demo/results/`.

## Baseline local (2026-09-09)

| Scenario | Throughput | p95 | p99 | HTTP failures |
| --- | ---: | ---: | ---: | ---: |
| Query, 5 VUs | 1,385.40 TPS | 7.47 ms | 17.60 ms | 0% |
| Reservation, 3 VUs | 176.56 TPS | 27.71 ms | 40.21 ms | 0% |
| Mixed, 4 VUs | 499.51 TPS | 19.70 ms | 31.37 ms | 0% |

Valkey reported 24,316 hits and 2,739 misses (89.88% hit rate). PostgreSQL reported 31 connections and zero lock waits at the final snapshot. The sustainable envelope is constrained by the single PostgreSQL writer path for an event; the first likely production bottleneck is contention on that inventory row. This is a local baseline, not a remote AWS capacity claim.
