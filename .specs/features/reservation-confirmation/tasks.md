# Confirmação externa da reserva — plano de tarefas

**Design:** `.specs/features/reservation-confirmation/design.md`
**Status:** Execução aprovada. T02 está concluída; T03 é o próximo passo. T08–T11 reconciliam documentação, diagramas e operação depois de T07.

## Execution Protocol

Seguir `tlc-spec-driven`: atualizar spec/context/design e decisões antes de alterar comportamento, testar cada tarefa contra seus critérios, concluir cada tarefa em commit atômico e executar o Verifier independente após a última. A execução altera arquivos e prepara Terraform, mas não autoriza `terraform apply`, publicação AWS nem implementação de pagamentos.

## Test Coverage Matrix

| Camada | Teste exigido | Resultado observado |
| --- | --- | --- |
| Estado e transições | unit + PostgreSQL integration | `PENDING → CONFIRMED`, cancelamento pendente sem retorno a `CONFIRMED`, corridas, prazo pós-lock e invariante por evento. |
| Infraestrutura de integração | Terraform/static + Compose smoke | Filas com dono lógico definido, DLQ, permissões do produtor/worker e ambiente local; sem SNS sem assinantes. |
| Inbox, outbox, SQS | integration | `ReservationHeld` a um único dono lógico, fechamento por `CANCELLED`/`EXPIRED` só na transição vencedora, duplicata, mudança de `messageId` com mesmo `resolutionId`, replay após janela de dedup da fila, ordem invertida de hold/fechamento, retry, queda entre commit e ack, DLQ e resultado ao produtor. |
| HTTP | integration | `GET` confirmado/em cancelamento e `DELETE` confirmado assíncrono; cinco rotas do case preservadas. |
| Specs e diagramas | validation + inspeção visual | Links, SVG acessível, distinção atual/proposto e contratos verdadeiros. |

## Gate Check Commands

| Gate | Comando |
| --- | --- |
| Spec | `python <skill-dir>/scripts/validate_spec.py .specs/features/reservation-confirmation/spec.md` |
| Tasks | `python <skill-dir>/scripts/validate_tasks.py .specs/features/reservation-confirmation/tasks.md` |
| Java | `.\mvnw.cmd --batch-mode clean verify -Pintegration` |
| Docs | `powershell -ExecutionPolicy Bypass -File scripts/validate-readme.ps1` |
| Diff | `git diff --check` |

## Execution Plan

### Phase 1: Contrato e persistência

```text
T01 → T02
```

### Phase 2: Ciclo de confirmação

```text
T03 → T04 → T05 → T06 → T07
```

### Phase 3: Explicação visual

```text
T08 → T09 → T10
```

## Task Breakdown

### T01: Consolidar a decisão de produto

**What:** Fechar entrada, prazo, semântica de `CONFIRMED`, cancelamento assíncrono e compensação; superseder AD-007, atualizar glossário e specs afetadas como sistema pretendido.
**Where:** `.specs/`
**Depends on:** None
**Requirement:** CONFIRM-01, CONFIRM-02, CONFIRM-03, CONFIRM-05
**Done when:** AD-007 e as specs vigentes concordam sobre estados, relógio, estoque e responsabilidade externa; não há afirmação de pagamento no domínio de reservas.
**Tests:** validation documental dos critérios e links.
**Gate:** Spec, Tasks, Diff.

**Status:** Complete; decisões e specs afetadas agora descrevem o ciclo aprovado. Os contratos de runtime e a reconciliação visual seguem nas tarefas dependentes.

### T02: Evoluir o schema de reserva e inbox

**What:** Migrar `reservation` e criar inbox de integração com constraints de `CONFIRMED`/`CANCELLATION_PENDING`, identidade externa e unicidade; validar contra PostgreSQL real.
**Where:** `src/main/resources/db/migration/`
**Depends on:** T01
**Requirement:** CONFIRM-01, CONFIRM-03, CONFIRM-05
**Done when:** V6 acrescenta estados e metadados coerentes, inbox com chave estável/fingerprint/resultado e preserva reservas legadas V5; PostgreSQL aceita combinações válidas, rejeita inválidas e impede resolução ou cancellationId repetidos.
**Tests:** integration de schema e constraints, incluídos na tarefa.
**Gate:** Java.

**Status:** Complete; V6 e oito casos de integração PostgreSQL cobrem os estados, a inbox, unicidade, retenção e migração dos três estados legados.

### T03: Implementar a decisão autoritativa

**What:** Fazer a confirmação condicional após lock com relógio PostgreSQL e as transições de cancelamento de confirmado; manter inventário em todas as corridas.
**Where:** `src/main/java/com/cielo/flashbooking/reservation/`
**Depends on:** T02
**Requirement:** CONFIRM-01, CONFIRM-02, CONFIRM-05
**Done when:** Confirmação antes do prazo não altera `available`; expiração/cancelamento vencedores impedem confirmação; `CANCELLATION_PENDING` retém capacidade até resposta externa positiva e nunca retorna a `CONFIRMED`; testes de lock e invariante passam.
**Tests:** unit + integration + concorrência, incluídos na tarefa.
**Gate:** Java.

### T04: Preparar canais de integração

**What:** Preparar uma fila SQS direta de mensagens ao único responsável externo (`ReservationHeld`, resultados e cancelamento) e uma fila SQS de entrada de confirmação/desfecho de cancelamento para Flash Booking, com DLQ, permissões mínimas e equivalentes locais sem aplicar recursos AWS; não criar SNS.
**Where:** `infra/modules/`
**Depends on:** T03
**Requirement:** CONFIRM-03, CONFIRM-05
**Done when:** Terraform/module tests e Compose representam canais isolados; somente roles IAM autorizadas enviam solicitações e o worker as consome; nenhuma fila atual muda de finalidade.
**Tests:** Terraform static/module + Compose smoke, incluídos na tarefa.
**Gate:** `terraform test` nos módulos alterados, Compose smoke e Diff.

### T05: Consumir solicitações externas com deduplicação

**What:** Receber `ReservationConfirmationRequested` e desfecho de cancelamento nos canais definidos, validar payload, `resolutionId` e identidade contratual autodeclarada, consultar inbox e decidir uma vez; a permissão de envio é controlada por IAM, não pelo campo `source`.
**Where:** `src/main/java/com/cielo/flashbooking/reservation/confirm/`
**Depends on:** T04
**Requirement:** CONFIRM-03, CONFIRM-05
**Done when:** Reenvio com o mesmo `resolutionId`, ainda que outro `messageId`, reproduz resultado; chave repetida com payload diferente conflita; erro técnico mantém mensagem para retry/DLQ; testes não encontram efeito duplicado.
**Tests:** integration de SQS/PostgreSQL, incluídos na tarefa.
**Gate:** Java.

### T06: Publicar resultados e solicitação de cancelamento

**What:** Acrescentar `ReservationHeld`, `ReservationConfirmed`, `ReservationConfirmationRejected`, `ReservationHoldClosed(CANCELLED/EXPIRED)` e `ReservationCancellationRequested` à outbox e rotear ao único dono lógico externo, sem SNS; o fechamento por expiração deve ser gravado só por quem vencer `PENDING → EXPIRED`, inclusive `DELETE` tardio.
**Where:** `src/main/java/com/cielo/flashbooking/adapter/out/messaging/`
**Depends on:** T05
**Requirement:** CONFIRM-03, CONFIRM-05
**Done when:** Eventos têm versão/correlação sem PII, `ReservationHeld` usa identidade estável em republicações, `DELETE` e expiração de `PENDING` notificam com motivo sem retardar devolução de estoque, falha de publicação deixa outbox pendente e replay não cria resultado adicional; Flash Booking não grava estado de compensação externa.
**Tests:** integration de outbox/publisher, incluídos na tarefa.
**Gate:** Java.

### T07: Atualizar leitura, cancelamento e projeções de reserva

**What:** Expor `CONFIRMED` e `CANCELLATION_PENDING` na consulta; preservar cancelamento de `PENDING` conforme decisão final e estender `DELETE` de confirmado para solicitação assíncrona; reconciliar resumo executivo, auditoria de carga e documentação Postman com os novos estados; exercitar o fluxo com módulo externo simulado.
**Where:** `src/main/java/com/cielo/flashbooking/reservation/controller/`, `src/main/java/com/cielo/flashbooking/event/summary/`, `performance/dynamic-load/`, `postman/`
**Depends on:** T06
**Requirement:** CONFIRM-01, CONFIRM-02, CONFIRM-03, CONFIRM-05
**Done when:** `GET` mostra `confirmedAt` e estado em cancelamento; `DELETE` de `PENDING` encerra imediatamente e notifica o simulador; `DELETE` de confirmado retorna aceite pendente sem devolver estoque e repetição não cria novo pedido; falha externa mantém o estado pendente; o resumo conta confirmação e cancelamento pendente como estoque comprometido sem alegar compra; a auditoria de inventário inclui os três estados que retêm capacidade; um cenário local percorre retenção, confirmação e cancelamento com simulador sem implementar pagamentos.
**Tests:** integration HTTP, resumo executivo, auditoria de inventário, contratos Postman e fluxo local completo, incluídos na tarefa.
**Gate:** Java.

### T08: Reconciliar as vistas C4 com a implementação

**What:** Revisar as vistas C4 e AWS de demo/alta carga junto às novas vistas C4 para representar as filas de integração, o responsável externo e a fronteira entre runtime local, Terraform atual e implantação histórica destruída.
**Where:** `docs/images/`
**Depends on:** T07
**Requirement:** CONFIRM-04
**Done when:** Contexto mostra ator, Flash Booking e responsável externo; containers mostra APIs, worker, PostgreSQL e filas direcionais com o dono lógico correto; AWS e evolução distinguem recursos declarados em Terraform da última implantação já destruída e da topologia high-load não provisionada.
**Tests:** renderização e inspeção visual, incluídas na tarefa.
**Gate:** Docs, Diff.

### T09: Reconciliar a dinâmica e os desfechos

**What:** Revisar o ciclo de estados, sequência HTTP/outbox, disputa do último ingresso e figuras de modelo/outbox existentes para representar os estados novos, `ReservationHeld`, decisão tardia, fechamento e cancelamento pendente.
**Where:** `docs/images/`
**Depends on:** T08
**Requirement:** CONFIRM-02, CONFIRM-04
**Done when:** Setas e legendas distinguem pedido, decisão PostgreSQL e resultado externo; `CONFIRMED` mantém estoque, nenhuma mensagem confirma após prazo e só conclusão correlacionada libera o estoque de `CANCELLATION_PENDING`; figuras que registram apenas o baseline anterior são claramente rotuladas como históricas.
**Tests:** renderização, inspeção visual e conferência com cenários integration, incluídas na tarefa.
**Gate:** Docs, Diff.

### T10: Reconciliar a explicação do README com o runtime

**What:** Reescrever a seção do README, seus rótulos de entrega e a auditoria documental para que estados, APIs, filas, módulos externos, figuras e estado de implantação correspondam à evidência de T01–T09.
**Where:** `README.md`, `scripts/`, `.specs/features/reservation-confirmation/`
**Depends on:** T09
**Requirement:** CONFIRM-04
**Done when:** Os cinco endpoints continuam descritos corretamente; o ciclo de reserva e as integrações assíncronas têm documentação de ponta a ponta; diagramas atuais, históricos e de alta carga têm rótulos verdadeiros; o README não sugere pagamentos implementados nem recursos AWS aplicados após as mudanças; validação de links, SVGs e contratos passa.
**Tests:** validation documental e inspeção visual do README, incluídas na tarefa.
**Gate:** Docs, Diff.

### T11: Reconciliar guias operacionais e fronteiras de infraestrutura

**What:** Atualizar o guia local, runbook, avaliação do case e READMEs dos módulos Terraform com as duas filas direcionais, o responsável externo, a recuperação local e o estado não aplicado dos novos recursos.
**Where:** `docs/`, `infra/modules/`
**Depends on:** T10
**Requirement:** CONFIRM-03, CONFIRM-04, CONFIRM-05
**Done when:** Um operador consegue reproduzir a integração simulada localmente e identificar quem publica/consome cada fila; guias não sugerem módulo de pagamento, role externa já provisionada ou deploy AWS dos novos recursos.
**Tests:** validação de links/contratos, revisão dos comandos documentados e inspeção dos exemplos locais.
**Gate:** Docs, Diff.

## Phase Execution Map

```text
Phase 1: T01 → T02
Phase 2: T02 → T03 → T04 → T05 → T06 → T07
Phase 3: T07 → T08 → T09 → T10 → T11
```

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram shows | Status |
| --- | --- | --- | --- |
| T01 | None | início | OK |
| T02 | T01 | T01 → T02 | OK |
| T03 | T02 | T02 → T03 | OK |
| T04 | T03 | T03 → T04 | OK |
| T05 | T04 | T04 → T05 | OK |
| T06 | T05 | T05 → T06 | OK |
| T07 | T06 | T06 → T07 | OK |
| T08 | T07 | T07 → T08 | OK |
| T09 | T08 | T08 → T09 | OK |
| T10 | T09 | T09 → T10 | OK |
| T11 | T10 | T10 → T11 | OK |

## Test Co-location Validation

| Task | Layer | Matrix requires | Task says | Status |
| --- | --- | --- | --- | --- |
| T01 | Specs | validation | validation | OK |
| T02 | Schema | integration | integration | OK |
| T03 | Estado | unit + integration | unit + integration | OK |
| T04 | Infra/SQS | Terraform/static + Compose | Terraform/static + Compose | OK |
| T05 | SQS/inbox | integration | integration | OK |
| T06 | Outbox | integration | integration | OK |
| T07 | HTTP | integration | integration | OK |
| T08 | SVG | visual | visual | OK |
| T09 | SVG | visual | visual | OK |
| T10 | README | validation | validation | OK |
| T11 | Operational docs | validation | validation | OK |
