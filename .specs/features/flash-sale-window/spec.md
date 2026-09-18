# Flash Sale Window Specification

## Problem Statement

Eventos da demo aceitam reservas imediatamente e não têm data de encerramento. Uma flash sale precisa poder abrir em um instante futuro e, opcionalmente, encerrar em um instante definido sem enfraquecer a garantia de estoque sob concorrência.

## Goals

- [x] Permitir configurar datas opcionais de início e fim em `POST /events`.
- [x] Autorizar reservas somente durante a janela comercial calculada pelo PostgreSQL.
- [x] Expor a janela configurada em `POST /events` e `GET /events/{id}`.
- [x] Preservar idempotência, oversell zero, cache-aside e os contratos existentes.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Cancelamento de evento | O case não possui ciclo de vida ou endpoint administrativo para evento. |
| Alteração de janela depois da criação | Exige contrato de atualização, auditoria e regra para reservas em andamento. |
| Pré-escala automática da demo | Pertence à arquitetura high-load planejada. |
| Mudança da expiração de reserva | A janela da venda e o `expiresAt` da reserva são regras independentes. |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Formato temporal | `Instant` ISO-8601 com timezone, serializado em UTC | Mantém o contrato Java/PostgreSQL explícito e sem timezone local ambíguo. | yes |
| Início informado | Deve ser estritamente posterior ao `createdAt` decidido pelo PostgreSQL | Uma data passada equivale ao padrão imediato e ocultaria erro de agendamento. | yes |
| Fim com início informado | Deve ser estritamente posterior a `startsAt` | A janela não pode ser vazia ou invertida. | yes |
| Fim sem início | Deve ser maior ou igual a `createdAt + 10 minutos` | Permite venda imediata com duração útil mínima; exatamente dez minutos é válido. | yes |
| Reserva fora da janela | Retorna `409 application/problem+json`, sem cliente, reserva ou outbox | O payload é válido, mas o estado comercial do evento não permite a reserva. | yes |
| Evento sem fim | Permanece elegível enquanto houver disponibilidade; um cancelamento pode devolver capacidade e reabrir a venda | Não existe estado separado de evento encerrado nem endpoint de cancelamento de evento. | yes |

**Open questions:** none - all resolved or logged above.

## User Stories

### P1: Agendar uma flash sale

**User Story**: Como operador, quero criar um evento com início e fim opcionais para controlar quando a venda aceita reservas.

**Why P1**: A janela comercial é necessária para representar uma flash sale agendada sem criar outro recurso de domínio.

**Acceptance Criteria**:

1. WHEN `POST /events` omits `startsAt` and `endsAt` THEN the system SHALL create an event whose `startsAt` and `endsAt` responses are `null`, and reservations SHALL be eligible immediately when capacity exists. <!-- event-driven -->
2. WHEN `POST /events` supplies a `startsAt` strictly after database `createdAt` and omits `endsAt` THEN the system SHALL persist and return that start instant, and reservations before it SHALL return `409 application/problem+json` without creating customer, reservation, or outbox rows. <!-- event-driven -->
3. WHEN `POST /events` supplies `startsAt` and an `endsAt` strictly after it THEN the system SHALL persist and return both instants, and reservations at or after `endsAt` SHALL return `409 application/problem+json` without changing availability. <!-- event-driven -->
4. WHEN `POST /events` omits `startsAt` and supplies `endsAt` at least ten minutes after database `createdAt` THEN the system SHALL persist and return a `null` start instant and that end instant. <!-- event-driven -->
5. IF `startsAt` is at or before database `createdAt`, IF `endsAt` is at or before `startsAt`, or IF an end-only event has `endsAt` before database `createdAt + 10 minutes`, THEN the system SHALL return `400 application/problem+json` and SHALL not persist an event. <!-- unwanted-behavior -->
6. WHILE a reservation command contends for an event row, the system SHALL evaluate sale-window eligibility with the PostgreSQL wall clock in the same conditional inventory decrement that protects capacity. <!-- state-driven -->

**Independent Test**: Criar um evento imediato, um futuro, um encerrado e um com término imediato válido; consultar cada resposta e tentar reservar nos limites da janela.

---

### P1: Consultar a janela comercial

**User Story**: Como cliente, quero visualizar as datas configuradas da venda para entender sua disponibilidade comercial.

**Why P1**: A consulta precisa explicar se a venda abre depois ou tem encerramento programado.

**Acceptance Criteria**:

1. WHEN `POST /events` or `GET /events/{id}` returns an event THEN the system SHALL include `startsAt` and `endsAt` with their persisted ISO-8601 values or `null`. <!-- event-driven -->
2. WHEN `GET /events/{id}` returns an event from Valkey THEN the system SHALL return the same persisted sale-window values as a PostgreSQL read. <!-- event-driven -->
3. WHILE an event has no `endsAt`, the system SHALL not reject a reservation solely because time elapsed; capacity and the configured start instant remain the authorization conditions. <!-- state-driven -->
4. WHEN a cancellation returns capacity to an event without `endsAt` whose start instant has passed, THEN the system SHALL make the returned capacity eligible for a subsequent reservation. <!-- event-driven -->

**Independent Test**: Consultar eventos com as três combinações válidas de janela com cache frio e quente, e cancelar uma reserva de evento sem fim para confirmar uma nova reserva.

## Edge Cases

- IF a retry repeats a rejected before-start or after-end reservation with the same idempotency fingerprint, THEN the system SHALL replay the persisted `409` response without a new effect.
- WHEN the PostgreSQL clock equals `startsAt`, THEN the system SHALL allow a reservation if capacity exists.
- WHEN the PostgreSQL clock equals `endsAt`, THEN the system SHALL reject a reservation with `409` and preserve availability.
- IF a direct database write violates the persisted window constraints, THEN PostgreSQL SHALL reject the row.

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| WINDOW-01 | P1: Agendar uma flash sale | Execute | Verified |
| WINDOW-02 | P1: Agendar uma flash sale | Execute | Verified |
| WINDOW-03 | P1: Agendar uma flash sale | Execute | Verified |
| WINDOW-04 | P1: Agendar uma flash sale | Execute | Verified |
| WINDOW-05 | P1: Agendar uma flash sale | Execute | Verified |
| WINDOW-06 | P1: Agendar uma flash sale | Execute | Verified |
| WINDOW-07 | P1: Consultar a janela comercial | Execute | Verified |
| WINDOW-08 | P1: Consultar a janela comercial | Execute | Verified |
| WINDOW-09 | P1: Consultar a janela comercial | Execute | Verified |
| WINDOW-10 | P1: Consultar a janela comercial | Execute | Verified |

**Coverage:** 10 total, 10 mapped to tasks, 0 unmapped.

## Success Criteria

- [x] As três combinações válidas de janela são criadas e retornadas pelo contrato HTTP.
- [x] Reserva antes da abertura ou no fim/depois do encerramento retorna `409` sem efeito parcial.
- [x] As validações temporais retornam `400` e também existem como constraints no PostgreSQL.
- [x] Os gates unitário e PostgreSQL/Testcontainers passam.
