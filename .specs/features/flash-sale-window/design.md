# Flash Sale Window Design

## Overview

A janela pertence ao agregado `Event`. O evento armazena `startsAt` e `endsAt` opcionais. A criação valida a combinação contra o instante de criação fornecido pelo PostgreSQL. A reserva continua sendo autorizada somente pelo PostgreSQL, agora com capacidade e janela verificadas no mesmo comando condicional.

## Components

| Component | Change | Responsibility |
| --- | --- | --- |
| `Event` | Adiciona `startsAt` e `endsAt` | Mantém combinações temporais válidas em memória. |
| `CreateEventService` | Obtém hora pela porta de persistência | Faz a criação usar a mesma fonte temporal autoritativa do schema. |
| `EventPersistenceAdapter` | Persiste e lê os novos campos | Implementa a porta JDBC sem introduzir JPA. |
| `JdbcInventoryOperations` | Acrescenta a janela ao `UPDATE` | Autoriza a reserva no instante e estoque corretos. |
| Event request/response/cache | Expõe e preserva os campos | Mantém o contrato HTTP e o cache-aside consistentes. |
| Flyway V4 | Adiciona colunas e checks | Protege dados existentes e futuras escritas diretas. |

## Data Model

`event` recebe duas colunas nullable:

- `starts_at TIMESTAMPTZ`: início programado; `NULL` significa imediato.
- `ends_at TIMESTAMPTZ`: encerramento programado; `NULL` significa sem fim temporal.

A migration é expansiva e compatível com eventos existentes: ambas as colunas começam nulas. Checks garantem que um início exista depois de `created_at`, que um fim com início seja posterior a ele e que um fim sem início seja pelo menos dez minutos posterior a `created_at`.

Nenhum índice novo é necessário. As reservas atualizam uma linha pelo `event.id`, que já é chave primária; a janela é predicado adicional nessa busca pontual.

## Reservation Authorization

```sql
UPDATE event
SET available = available - :quantity
WHERE id = :eventId
  AND available >= :quantity
  AND (starts_at IS NULL OR starts_at <= clock_timestamp())
  AND (ends_at IS NULL OR clock_timestamp() < ends_at)
```

O `UPDATE` mantém capacidade e janela no mesmo ponto de serialização. A reavaliação da linha após aguardar concorrência usa o relógio de parede do PostgreSQL. Um comando antes do início, no fim ou depois dele afeta zero linhas e a aplicação devolve `409` sem criar dados dependentes.

## Flow

```text
POST /events
  -> JDBC SELECT clock_timestamp()
  -> Event valida name, capacity, startsAt e endsAt
  -> INSERT event com created_at, starts_at e ends_at
  -> 201 com os três instantes

POST /events/{id}/reservations
  -> idempotency claim
  -> event exists?
  -> UPDATE condicional: capacity + starts_at + ends_at
  -> cliente + reserva + outbox, ou 409 persistido pela idempotência
```

## Error Semantics

| Condition | HTTP result | Persistence |
| --- | --- | --- |
| Janela inválida ao criar evento | `400 application/problem+json` | Nenhum evento; o contrato de idempotência preserva o resultado final quando aplicável |
| Evento ainda não abriu | `409 application/problem+json` | Nenhum cliente, reserva ou outbox |
| Evento já encerrou | `409 application/problem+json` | Disponibilidade preservada |
| Estoque insuficiente | `409 application/problem+json` | Comportamento existente preservado |

## Alternatives Rejected

| Alternative | Reason |
| --- | --- |
| Worker mudar estado do evento ao abrir/encerrar | Introduz atraso e novo estado para uma decisão que o PostgreSQL já consegue fazer no comando autoritativo. |
| Usar relógio da JVM | Tasks podem divergir; a regra compartilhada precisa de uma fonte temporal persistida. |
| Validar somente no controller | Não protege chamadas internas ou escrita direta e não cobre a corrida entre validação e decremento. |

## Test Strategy

- Teste unitário do domínio para combinações de janela e limites de dez minutos.
- Teste unitário do serviço para a fonte de tempo da persistência.
- Integração PostgreSQL para migration, checks e decremento antes/no início/no fim/depois do fim.
- Integração HTTP para payload, `400`, `409`, ausência de efeitos e replay idempotente.
- Teste de cache para os novos campos em miss e hit.

## Decision

Esta mudança concretiza a decisão AD-021: o PostgreSQL será a fonte autoritativa também para a criação e a elegibilidade temporal da janela comercial. A decisão é transversal à demo e à arquitetura high-load e está registrada em `STATE.md`.
