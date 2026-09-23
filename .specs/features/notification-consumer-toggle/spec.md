# Flag de pausa do consumidor de notificações

## Problem Statement

Cada reserva publicada na demo AWS aciona a fila de notificações e o consumidor SES. A semeadura autorizada de 1.000 clientes fake precisa preservar clientes e reservas no PostgreSQL sem iniciar mil tentativas de e-mail, retries ou mensagens de DLQ.

## Goals

- [ ] Expor uma flag Terraform da demo para habilitar ou pausar somente o consumidor de notificações.
- [ ] Manter o comportamento atual habilitado quando a flag não for definida.
- [ ] Preservar outbox, expiração, persistência de clientes e reservas enquanto o consumidor estiver pausado.
- [ ] Documentar a ativação temporária antes da semeadura e a reativação obrigatória depois.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Alterar o contrato HTTP, schema ou os dados persistidos | A flag controla somente a task worker. |
| Descartar mensagens já publicadas | A pausa deve evitar SES; mensagens continuam na fila para processamento posterior. |
| Criar o seeder AWS com Faker.js | É uma feature separada, que consumirá a flag depois de validada. |
| Aplicar Terraform na AWS durante esta implementação | O apply remoto exige plano revisado do ambiente e permanece uma ação operacional separada. |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- |
| Estado padrão | `notification_consumer_enabled = true` | Preserva o envio de e-mail da demo fora da janela de semeadura. | yes, comportamento atual |
| Escopo da pausa | Apenas `NOTIFICATION_CONSUMER_ENABLED` | Outbox e expiração continuam necessários para as reservas persistidas. | yes, usuário pediu pausar notificações, não o worker inteiro |
| Dados publicados na pausa | Mensagens permanecem na SQS | Não há descarte silencioso ou perda de evento. | yes, clientes e reservas devem permanecer |

**Open questions:** none.

---

## User Stories

### P1: Pausar temporariamente a entrega de e-mails

**User Story**: Como operador da demo, quero configurar a entrega de notificações separadamente do worker para semear clientes fake na AWS sem enviar e-mails.

**Why P1**: Mil reservas fake acionariam SES, retries e DLQ sem contribuir para a massa persistida.

**Acceptance Criteria**:

1. WHEN `notification_consumer_enabled` não for informado THEN a task `worker` SHALL receber `NOTIFICATION_CONSUMER_ENABLED=true`.
2. WHEN `notification_consumer_enabled=false` for aplicado na demo THEN a task `worker` SHALL receber `NOTIFICATION_CONSUMER_ENABLED=false` e o bean `SqsReservationCreatedConsumer` SHALL not be criado.
3. WHILE `notification_consumer_enabled=false` THEN `OUTBOX_PUBLISHER_ENABLED=true` e `EXPIRATION_CONSUMER_ENABLED=true` SHALL permanecer inalterados na task `worker`.
4. WHEN o operador concluir a semeadura THEN o runbook SHALL instruir a restaurar `notification_consumer_enabled=true` e aplicar Terraform antes de tratar a fila de notificações como ativa.

**Independent Test**: `terraform test` do módulo compute deve provar os valores `true` e `false` em definições de task independentes; um teste Spring deve provar que a propriedade `false` não cria o consumidor SQS.

---

## Implicit-Requirement Dimensions

| Dimension | Resolution |
| --- | --- |
| Input validation & bounds | A variável Terraform é booleana; valores não booleanos são rejeitados pelo Terraform. |
| Failure / partial-failure states | A documentação exige reativação explícita; Terraform mantém a configuração declarada mesmo após falha parcial de deploy. |
| Idempotency / retry / duplicate handling | A pausa não altera outbox nem a semântica de entrega pelo menos uma vez; apenas impede a criação do consumidor. |
| Auth boundaries & rate limits | Não cria rota ou credencial; apply AWS continua sujeito às permissões Terraform existentes. |
| Concurrency / ordering | Mensagens podem acumular na SQS enquanto a flag é `false`; nenhuma é descartada ou reordenada pelo novo código. |
| Data lifecycle / expiry | Clientes, reservas e eventos de outbox permanecem; expiração continua habilitada. |
| Observability | A fila de notificação e a DLQ existentes permanecem os sinais operacionais durante a pausa. |
| External-dependency failure | SES deixa de ser chamado pelo consumidor pausado; SQS continua dependência do publisher. |
| State-transition integrity | A flag alterna `enabled`/`paused` somente para o consumidor, sem mudar estados de reserva. |

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| NOTIFY-01 | Flag da task worker | Execute | Implemented |
| NOTIFY-02 | Pausa sem interromper fluxos críticos | Execute | Implemented |
| NOTIFY-03 | Reativação operacional | Execute | Implemented |

## Success Criteria

- [ ] Terraform prova o default habilitado e a pausa explícita.
- [ ] A configuração Spring prova ausência do consumidor quando `enabled=false`.
- [ ] O procedimento de pausa e restauração não contém credenciais e não altera dados persistidos.
