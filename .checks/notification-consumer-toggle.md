# Pausa temporária do consumidor de notificações

Sources:

- [.tasks/notification-consumer-toggle.md](../.tasks/notification-consumer-toggle.md) — critérios C1–C4 e limites do trabalho.
- [.specs/features/notification-consumer-toggle/spec.md](../.specs/features/notification-consumer-toggle/spec.md) — requisitos NOTIFY-01 a NOTIFY-03.

## Out of scope

- Seeder AWS, Faker.js e chamadas remotas — esta alteração apenas torna a pausa declarativa.
- Descarte, drenagem ou replay de mensagens SQS — a fila deve preservar as mensagens durante a pausa.
- Alteração de schema, dados de cliente, reservas ou eventos — não há migração de dados.

## Landing

O ambiente `demo` encaminha uma variável booleana ao módulo `compute`, que a serializa para a variável de ambiente já consumida pelo Spring. A configuração `SqsReservationCreatedConsumerConfiguration` existente continua a ser a única responsável por criar o consumidor quando a flag é verdadeira.

| One-way door | Literal shape | Alternative rejected |
| --- | --- | --- |
| Flag operacional da demo | `notification_consumer_enabled: bool = true` chega como `NOTIFICATION_CONSUMER_ENABLED` na task `worker` | Desabilitar a task worker - também pararia publisher e expiração, que devem permanecer ativos. |

- Nothing else in this change is hard to reverse.

## Checks

### S1 - Pausa declarativa do consumidor · Terraform e configuração Spring · ~35k

**C1** - Sem override, a task `worker` recebe `NOTIFICATION_CONSUMER_ENABLED=true`.
Proof: `terraform -chdir=infra/modules/compute test` — run `uses_one_image_with_separate_least_privilege_services`

**C2** - Com `notification_consumer_enabled=false`, a task `worker` recebe `NOTIFICATION_CONSUMER_ENABLED=false`.
Proof: `terraform -chdir=infra/modules/compute test` — run `pauses_only_notification_consumer`

**C3** - Com `notification.consumer.enabled=false` no perfil `worker`, o contexto Spring não cria `SqsReservationCreatedConsumer`.
Proof: `./mvnw.cmd -Dtest=SqsReservationCreatedConsumerConfigurationTest test`

**C4** - O runbook instrui aplicar `false` antes da semeadura, `true` depois dela, e declara que a pausa não remove mensagens SQS.
Proof: `@('notification_consumer_enabled = false', 'notification_consumer_enabled = true', 'não remove mensagens') | ForEach-Object { if (-not (Select-String -LiteralPath docs/pausar-notificacoes-semeadura.md -SimpleMatch $_)) { throw "missing runbook statement: $_" } }`

## Swept

- validation: C1, C2
- failure modes: existing - Terraform mantém o último estado aplicado em um deploy interrompido
- idempotency: existing - outbox e entrega pelo menos uma vez não mudam
- authorization: existing - o apply exige o operador Terraform autorizado
- concurrency: C2 - publisher e expiração continuam ativos com o consumidor pausado
- data lifecycle: C4 - a pausa não remove mensagens ou registros
- dependency failure: C3 - sem consumidor, SES não é chamado pela task worker
- state transitions: C1, C2, C4
- observability: existing - métricas SQS e DLQ permanecem no dashboard existente

## Handoff

S1 = ~35k tokens; cabe integralmente em um único lote, sem handoff de construção.
