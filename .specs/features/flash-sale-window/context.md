# Flash Sale Window Context

**Gathered:** 2026-09-18
**Spec:** `.specs/features/flash-sale-window/spec.md`
**Status:** Complete

## Feature Boundary

Adicionar uma janela comercial opcional ao evento existente. A janela controla apenas a aceitação de novas reservas. Não cria estado de evento, alteração posterior ou cancelamento administrativo.

## Implementation Decisions

### Semântica temporal

- O PostgreSQL decide `createdAt`, abertura e encerramento com `clock_timestamp()`.
- A ausência de `startsAt` representa venda imediata.
- A ausência de `endsAt` representa venda sem encerramento por tempo.
- `startsAt` precisa ser posterior a `createdAt`.
- Com `startsAt`, `endsAt` precisa ser posterior a `startsAt`.
- Sem `startsAt`, `endsAt` precisa ser pelo menos dez minutos depois de `createdAt`.
- A abertura é inclusiva: `clock_timestamp() >= starts_at` permite reservar.
- O encerramento é exclusivo: `clock_timestamp() >= ends_at` bloqueia reservar.

### Consistência e erros

- A condição da janela pertence ao mesmo `UPDATE` condicional que decrementa `available`.
- O resultado fora da janela é conflito de estado `409`, não erro de formato `400`.
- Uma validação inválida de criação devolve `400` sem persistir evento; como os demais resultados finais de comandos, a resposta pode ser reproduzida pela idempotência quando a chave é usada.
- Uma rejeição final de reserva fora da janela é persistida pela idempotência, como a rejeição por capacidade.

### Contrato e compatibilidade

- Os campos JSON são opcionais no request e sempre presentes no response, como `startsAt` e `endsAt` com valor ISO-8601 ou `null`.
- O mesmo contrato é compartilhado por demo e arquitetura high-load.
- Cache de evento preserva os dois campos e continua com TTL máximo de um segundo.

## Deferred Ideas

- Endpoint de edição ou cancelamento de evento.
- Pré-escala automática antes de `startsAt`.
- Status comercial calculado no response, como `SCHEDULED`, `ACTIVE` ou `ENDED`.
