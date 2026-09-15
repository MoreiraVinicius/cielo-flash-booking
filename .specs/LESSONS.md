# LESSONS - auto-maintained by scripts/lessons.py

> Machine-owned. Do NOT hand-edit. Changes are overwritten on the next `lessons.py` write.
> Canonical state lives in `.specs/lessons.json`. Edit lessons only via the script.
> promote_threshold=2 distinct features · window_days=45 · quarantine_threshold=2

## Confirmed (load these at Specify/Design)

Corroborated across multiple features. Safe to apply as guidance.

_none_

## Candidates (under observation - do NOT load as guidance yet)

Seen once or not yet corroborated. Tracked, not trusted.

### L-001 - Teste de expiração dependente de lock deve manter o lock anterior e provar que a nova janela começa após sua liberação.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `postgresql-idempotency` · harmful: 0
- features: flash-booking-demo
- evidence: .specs/features/flash-booking-demo/validation.md:213 (postgresql-idempotency) (+1 more)
- last seen: 2026-09-15T08:38:17Z

### L-002 - Limpeza e reativação concorrentes da mesma linha devem ser testadas nas duas ordens de aquisição do lock.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `postgresql-retention` · harmful: 0
- features: flash-booking-demo
- evidence: .specs/features/flash-booking-demo/validation.md:214 (postgresql-retention) (+1 more)
- last seen: 2026-09-15T08:38:17Z

## Quarantined (failed when applied - ignore)

A confirmed lesson that recurred alongside failure. Kept for the maintainer to review.

_none_
