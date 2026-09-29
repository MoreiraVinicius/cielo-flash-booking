# Confirmação externa da reserva — plano de tarefas

**Design:** `.specs/features/reservation-confirmation/design.md`
**Status:** T01–T15 concluídas; runtime local e documentação visual verificados; nenhuma implantação AWS ou implementação de pagamentos foi realizada.

## Execution Protocol

Seguir `tlc-spec-driven`: atualizar spec/context/design e decisões antes de alterar comportamento, testar cada tarefa contra seus critérios, concluir cada tarefa em commit atômico e executar um Verifier independente após a última. A execução alterou arquivos e preparou Terraform, mas não autorizou `terraform apply`, publicação AWS nem implementação de pagamentos.

## Test Coverage Matrix

| Camada | Teste exigido | Resultado observado |
| --- | --- | --- |
| Estado e transições | unit + PostgreSQL integration | 98 testes unitários passaram; integrações cobrem `PENDING → CONFIRMED`, cancelamento pendente sem retorno a `CONFIRMED`, corridas, prazo pós-lock e invariante por evento. |
| Infraestrutura de integração | Terraform/static + Compose smoke | T04: 7 testes Terraform passaram nos módulos data-plane, compute e edge-observability; `terraform validate` passou na composição demo; LocalStack saudável criou as 4 filas novas, cada qual com DLQ própria, long polling de 20s e visibility timeout de 60s. |
| Inbox, outbox, SQS | integration | T05: 8 integrações SQS/PostgreSQL passaram para inbox, replay, concorrência e retry. T06: 37 integrações selecionadas passaram; outbox de `ReservationHeld`, resultado, fechamento e cancelamento, roteamento ao dono, criação, expiração e concorrência. |
| HTTP e fluxo externo simulado | integration | T07: 8 testes `ReservationQueryControllerIT` e 10 `SqsReservationConfirmationConsumerIT` passaram; a simulação percorre retenção, confirmação e cancelamento; cinco rotas do case preservadas. |
| Specs e diagramas | validation + inspeção visual | Baseline documental anterior passou. T13–T14 exigem sequência numerada, auditoria de todos os diagramas, renderização dos alterados e gate de links. |
| Regressões do Verifier | focused PostgreSQL/LocalStack integration + discrimination sensor | T12: 13 integrações focadas passaram; os três cenários identificados foram adicionados; sensor isolado matou 1/1 mutações. |

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

**Status:** Complete; decisões e specs afetadas descrevem o ciclo atual.

### T02: Evoluir o schema de reserva e inbox

**What:** Migrar `reservation` e criar inbox de integração com constraints de `CONFIRMED`/`CANCELLATION_PENDING`, identidade externa e unicidade; validar contra PostgreSQL real.
**Where:** `src/main/resources/db/migration/`
**Depends on:** T01
**Requirement:** CONFIRM-01, CONFIRM-03, CONFIRM-05
**Done when:** V6 acrescenta estados e metadados coerentes, inbox com chave estável/fingerprint/resultado e preserva reservas legadas V5; PostgreSQL aceita combinações válidas, rejeita inválidas e impede resolução ou cancellationId repetidos.
**Tests:** integration de schema e constraints, incluídos na tarefa.
**Gate:** Java.

**Status:** Complete; V6 e oito casos de integração PostgreSQL cobrem os estados, a inbox, unicidade, retenção e migração dos estados anteriores.

### T03: Implementar a decisão autoritativa

**What:** Fazer a confirmação condicional após lock com relógio PostgreSQL e as transições de cancelamento de confirmado; manter inventário em todas as corridas.
**Where:** `src/main/java/com/cielo/flashbooking/reservation/`
**Depends on:** T02
**Requirement:** CONFIRM-01, CONFIRM-02, CONFIRM-05
**Done when:** Confirmação antes do prazo não altera `available`; expiração/cancelamento vencedores impedem confirmação; `CANCELLATION_PENDING` retém capacidade até resposta externa positiva e nunca retorna a `CONFIRMED`; testes de lock e invariante passam.
**Tests:** unit + integration + concorrência, incluídos na tarefa.
**Gate:** Java.

**Status:** Complete; decisão usa relógio PostgreSQL depois do lock, expiração tardia libera uma vez e pedido de cancelamento confirmado conserva a capacidade.

### T04: Preparar canais de integração

**What:** Preparar uma fila SQS direta de mensagens ao único responsável externo (`ReservationHeld`, resultados e cancelamento) e uma fila SQS de entrada de confirmação/desfecho de cancelamento para Flash Booking, com DLQ, permissões mínimas e equivalentes locais sem aplicar recursos AWS; não criar SNS.
**Where:** `infra/modules/`
**Depends on:** T03
**Requirement:** CONFIRM-03, CONFIRM-05
**Done when:** Terraform provisiona duas filas direcionais com DLQs distintas, long polling, criptografia SQS e alarmes; a role externa exata pode consumir a saída e publicar a entrada, enquanto a role do worker publica na saída e consome a entrada; Compose/LocalStack representa os mesmos canais; nenhuma fila atual muda de finalidade.
**Tests:** Terraform static/module + Compose smoke, incluídos na tarefa.
**Gate:** `terraform test` nos módulos alterados, Compose smoke e Diff.

**Status:** Complete; 4 testes data-plane, 2 compute, 1 edge-observability e `terraform validate` passaram. O smoke Compose iniciou LocalStack, verificou as oito filas existentes/novas e confirmou redrive para DLQ, long polling de 20s e visibility timeout de 60s nas duas filas de integração. Nenhum recurso AWS foi aplicado.

### T05: Consumir solicitações externas com deduplicação

**What:** Receber `ReservationConfirmationRequested` e desfecho de cancelamento nos canais definidos, validar payload, `resolutionId` e identidade contratual autodeclarada, consultar inbox e decidir uma vez; a permissão de envio é controlada por IAM, não pelo campo `source`.
**Where:** `src/main/java/com/cielo/flashbooking/reservation/confirm/`
**Depends on:** T04
**Requirement:** CONFIRM-03, CONFIRM-05
**Done when:** Reenvio com o mesmo `resolutionId`, ainda que outro `messageId`, reproduz resultado; chave repetida com payload diferente conflita; erro técnico mantém mensagem para retry/DLQ; testes não encontram efeito duplicado.
**Tests:** integration de SQS/PostgreSQL, incluídos na tarefa.
**Gate:** Java.

**Status:** Complete; consumidor valida envelope v1, deduplica por `(source, resolutionId)` com fingerprint semântico, grava decisão transacionalmente e confirma/devolve estoque apenas uma vez. Oito testes de integração SQS/PostgreSQL passaram, cobrindo reentrega, concorrência, colisão, cancelamento correlacionado, rollback, inexistente e retry de payload inválido.

### T06: Publicar resultados e solicitação de cancelamento

**What:** Acrescentar `ReservationHeld`, `ReservationConfirmed`, `ReservationConfirmationRejected`, `ReservationHoldClosed(CANCELLED/EXPIRED)` e `ReservationCancellationRequested` à outbox e rotear ao único dono lógico externo, sem SNS; o fechamento por expiração deve ser gravado só por quem vencer `PENDING → EXPIRED`, inclusive `DELETE` tardio. Adicionar `outboxEventId` estável ao payload público e aceitar os tipos novos no schema do evento.
**Where:** `src/main/java/com/cielo/flashbooking/adapter/out/messaging/`
**Depends on:** T05
**Requirement:** CONFIRM-03, CONFIRM-05
**Done when:** Eventos têm versão/correlação sem PII, `ReservationHeld` usa identidade estável em republicações, `DELETE` e expiração de `PENDING` notificam com motivo sem retardar devolução de estoque, falha de publicação deixa outbox pendente e replay não cria resultado adicional; Flash Booking não grava estado de compensação externa.
**Tests:** integration de outbox/publisher, incluídos na tarefa.
**Gate:** Java.

**Status:** Complete; V7 libera os cinco tipos de integração; cada produtor grava evento na mesma transação da reserva/inbox, e o publisher os encaminha apenas à fila do responsável, mantendo filas de notificação/expiração separadas. 98 unitários e 37 integrações selecionadas passaram; contratos verificam identidade no payload, ausência de PII em `ReservationHeld`, resultado por resolução, fechamento após prazo, cancellationId estável, republicação e roteamento.

### T07: Atualizar leitura, cancelamento e projeções de reserva

**What:** Expor `CONFIRMED` e `CANCELLATION_PENDING` na consulta; preservar cancelamento de `PENDING` conforme decisão final e estender `DELETE` de confirmado para solicitação assíncrona; reconciliar resumo executivo, auditoria de carga e documentação Postman com os novos estados; exercitar o fluxo com módulo externo simulado.
**Where:** `src/main/java/com/cielo/flashbooking/reservation/controller/`, `src/main/java/com/cielo/flashbooking/event/summary/`, `performance/dynamic-load/`, `postman/`
**Depends on:** T06
**Requirement:** CONFIRM-01, CONFIRM-02, CONFIRM-03, CONFIRM-05
**Done when:** `GET` mostra `confirmedAt` e estado em cancelamento; `DELETE` de `PENDING` encerra imediatamente e notifica o simulador; `DELETE` de confirmado retorna aceite pendente sem devolver estoque e repetição não cria novo pedido; falha externa mantém o estado pendente; o resumo conta confirmação e cancelamento pendente como estoque comprometido sem alegar compra; a auditoria de inventário inclui os três estados que retêm capacidade; um cenário local percorre retenção, confirmação e cancelamento com simulador sem implementar pagamentos.
**Tests:** integration HTTP, resumo executivo, auditoria de inventário, contratos Postman e fluxo local completo, incluídos na tarefa.
**Gate:** Java.

**Status:** Complete; `GET` expõe `confirmedAt`, `DELETE` confirmado responde 202 sem liberar capacidade, repetição mantém uma solicitação, resumo reconstrói o corte do evento com reservas confirmadas e auditoria de carga inclui todo o estoque comprometido. A simulação sem pagamentos percorre retenção, confirmação e cancelamento. Passaram 98 testes unitários e 23 integrações focadas; o cenário do resumo soma 15 ingressos no minuto de pico.

### T08: Reconciliar as vistas C4 com a implementação

**What:** Revisar as vistas C4 e AWS de demo/alta carga junto às vistas C4 da confirmação para representar as filas de integração, o responsável externo e a fronteira entre runtime local, infraestrutura declarada e implantação histórica destruída.
**Where:** `docs/images/`
**Depends on:** T07
**Requirement:** CONFIRM-04
**Done when:** Contexto mostra ator, Flash Booking e responsável externo; containers mostra APIs, worker, PostgreSQL e filas direcionais com o dono lógico correto; AWS e evolução distinguem runtime local com ciclo implementado, recursos declarados ainda não aplicados, implantação histórica destruída e topologia high-load não provisionada.
**Tests:** renderização e inspeção visual, incluídas na tarefa.
**Gate:** Docs, Diff.

**Status:** Complete; vistas C4 de contexto e containers identificam o runtime local, o dono de cada fila e AWS não aplicada. Uma vista adicional mostra os componentes AWS declarados para a confirmação e o responsável externo opaco, sem IAM no desenho. C4 de componentes foi atualizado; demo AWS histórica e alvo high-load permanecem distintos. SVGs foram renderizados e inspecionados.

### T09: Reconciliar a dinâmica e os desfechos

**What:** Revisar o ciclo de estados, sequência HTTP/outbox, disputa do último ingresso e figuras de modelo/outbox existentes para representar os estados novos, `ReservationHeld`, decisão tardia, fechamento e cancelamento pendente.
**Where:** `docs/images/`
**Depends on:** T08
**Requirement:** CONFIRM-02, CONFIRM-04
**Done when:** Setas e legendas distinguem pedido, decisão PostgreSQL e resultado externo; `CONFIRMED` mantém estoque, nenhuma mensagem confirma após prazo e só conclusão correlacionada libera o estoque de `CANCELLATION_PENDING`; modelo de dados mostra `confirmed_at`, `cancellation_id` e inbox; figuras de demo/AWS/high-load preservam corretamente o estado de cada runtime e deployment.
**Tests:** renderização, inspeção visual e conferência com cenários integration, incluídas na tarefa.
**Gate:** Docs, Diff.

**Status:** Complete; lifecycle, modelo de dados, último ingresso, sequência, outbox, demo e figuras AWS/high-load foram revisados. O modelo vigente mostra `confirmed_at`, `cancellation_id` e inbox; a imagem anterior foi renomeada e rotulada como baseline histórico. Figuras modificadas foram renderizadas para conferência visual.

### T10: Reconciliar a explicação do README com o runtime

**What:** Reescrever a seção do README, seus rótulos de entrega e a auditoria documental para que estados, APIs, filas, módulos externos, figuras e estado de implantação correspondam à evidência de T01–T09.
**Where:** `README.md`, `scripts/`, `.specs/features/reservation-confirmation/`
**Depends on:** T09
**Requirement:** CONFIRM-04
**Done when:** Os cinco endpoints continuam descritos corretamente; o ciclo de reserva e as integrações assíncronas têm documentação de ponta a ponta; diagramas atuais, históricos e de alta carga têm rótulos verdadeiros; o README não sugere pagamentos implementados nem recursos AWS aplicados; validação de links, SVGs e contratos passa.
**Tests:** validation documental e inspeção visual do README, incluídas na tarefa.
**Gate:** Docs, Diff.

**Status:** Complete; README distingue runtime local implementado, implantação AWS histórica e recursos atuais ainda não aplicados. O validador atualizado passou, com links locais, contratos de endpoint e SVG.

### T11: Reconciliar guias operacionais e fronteiras de infraestrutura

**What:** Atualizar o guia local, runbook, avaliação do case e READMEs dos módulos Terraform com as duas filas direcionais, o responsável externo, a recuperação local e o estado não aplicado dos novos recursos.
**Where:** `docs/`, `infra/modules/`
**Depends on:** T10
**Requirement:** CONFIRM-03, CONFIRM-04, CONFIRM-05
**Done when:** Um operador consegue executar o cenário de integração simulada localmente e identificar quem publica/consome cada fila; guias não sugerem módulo de pagamento, role externa já provisionada ou deploy AWS dos novos recursos.
**Tests:** validação de links/contratos, revisão dos comandos documentados e inspeção dos exemplos locais.
**Gate:** Docs, Diff.

**Status:** Complete; guia local documenta o Compose/LocalStack e o comando de integração que simula o responsável; Postman explica que cobre HTTP, não publicação SQS; guias de carga e seed não alegam simular confirmação.

### T12: Fechar lacunas independentes de concorrência e discriminação

**What:** Corrigir o deadlock encontrado quando resoluções distintas para a mesma reserva inserem na inbox antes de obter o lock exclusivo; provar a concorrência, confirmação após cancelamento vencedor e confirmação depois do fim da venda mas antes de `expiresAt`; executar o sensor em scratch.
**Where:** `ReservationResolutionProcessor`, `ReservationWriter`, `JdbcReservationPersistenceAdapter`, `SqsReservationConfirmationConsumerIT`, `.specs/features/reservation-confirmation/`.
**Depends on:** T11.
**Requirement:** CONFIRM-02, CONFIRM-03.
**Done when:** O worker bloqueia a reserva antes de a inbox estabelecer a referência FK; resoluções distintas recebem um resultado cada sem deadlock e com efeito de estoque único; mensagem de confirmação após `CANCELLED` é rejeitada sem nova devolução; `endsAt` passado não rejeita mensagem ainda válida pelo prazo da reserva; um mutante de `confirmedAt` é morto por teste em scratch isolado.
**Tests:** `SqsReservationConfirmationConsumerIT` e sensor independente em cópia temporária.
**Gate:** Integration, Sensor, Diff.

**Status:** Complete; 13 integrações focadas passaram, os três gaps de comportamento têm asserts explícitos e o sensor isolado matou a mutação `confirmedAt = null` no GET confirmado.

### Phase 4: Clareza visual e auditoria

### T13: Numerar o fluxo AWS da confirmação

**What:** Ampliar `flash-booking-confirmation-aws-components.svg` com sequência 01–08 da criação ao resultado externo e ramos de expiração/cancelamento. Preservar a caixa preta do responsável, as duas SQS com DLQ, o estado não aplicado da infraestrutura e a ausência de IAM no desenho.
**Where:** `docs/images/flash-booking-confirmation-aws-components.svg`, `.specs/features/reservation-confirmation/`.
**Depends on:** T12.
**Requirement:** CONFIRM-04.
**Done when:** Os números levam o leitor da retenção e outbox ao responsável, retorno à inbox, decisão PostgreSQL e resposta assíncrona; os ramos mostram liberação única de estoque e cancelamento confirmado aguardando conclusão externa; o SVG renderiza sem cortes ou afirmação de AWS aplicada.
**Tests:** XML, renderização e inspeção visual, contrato da spec.
**Gate:** Docs, Diff.

**Status:** Complete; sequência 01–08 renderizada e inspecionada, com ramos de expiração e cancelamento, sem IAM.

### T14: Auditar e corrigir os demais diagramas

**What:** Conferir todos os SVGs e PNGs de `docs/images/` contra o runtime, Terraform e a fonte da verdade; corrigir ou substituir os vigentes que usam estado, nome de mensagem, contagem de eventos ou texto de notificação antigos. Rotular visões históricas e alvos não provisionados, mantendo evidência histórica intacta.
**Where:** `docs/images/`, `README.md`, `.specs/features/reservation-confirmation/`.
**Depends on:** T13.
**Requirement:** CONFIRM-04.
**Done when:** Auditoria registra cada imagem e seu escopo; diagramas vigentes não dizem que o domínio termina em PENDING, não chamam e-mail temporário de compra/confirmação, usam `ReservationCancellationRequested` e três eventos na criação; imagens históricas e alvos têm rótulo visível ou legenda inequívoca no README.
**Tests:** Extração de textos, XML, renderização e inspeção das figuras alteradas, gate README e conferência com código/spec.
**Gate:** Docs, Diff.

**Status:** Complete; 22 imagens inventariadas, 19 SVGs XML válidos, seis SVGs corrigidos ou criados e README/validador atualizados; revisão visual e gate documental passaram.

### T15: Exibir e validar integralmente o resultado de confirmação

**What:** Corrigir o passo 08 para exibir o nome completo `ReservationConfirmationRejected`; tornar o gate README sensível aos nomes completos visíveis na sequência AWS, não só à descrição acessível do SVG.
**Where:** `docs/images/flash-booking-confirmation-aws-components.svg` (the task also strengthens its README validation gate).
**Depends on:** T14.
**Requirement:** CONFIRM-04.
**Done when:** O passo 08 contém `ReservationConfirmed` e `ReservationConfirmationRejected` completos e legíveis; o gate passa na versão correta e falha quando o nome visível é abreviado numa cópia temporária.
**Tests:** README gate, XML, renderização/inspeção e sensor em scratch isolado.
**Gate:** Docs, Sensor, Diff.

**Status:** Complete; evento de rejeição exibido por extenso, gate semântico limitado ao grupo step08-result passa e mata a mutação abreviada em cópia temporária; SVG renderizado e inspecionado. Aguarda veredito independente final.

### T16: Explicar e diagramar Outbox e SQS como responsabilidades distintas

**What:** Explicar no README que a outbox PostgreSQL persiste os eventos atomicamente e a SQS os transporta; ilustrar a confirmação e o cancelamento com ícones AWS em sequências PlantUML editáveis e SVGs renderizados.
**Where:** Seção de integração do README; fontes e imagens em `docs/diagrams/` e `docs/images/`; especificação e evidências em `.specs/features/reservation-confirmation/`.
**Depends on:** T15.
**Requirement:** CONFIRM-04.
**Done when:** O leitor consegue seguir criação/outbox/publisher/SQS/responsável/fila de retorno/inbox/decisão/outbox de resultado; a documentação não confunde transporte com persistência nem `CONFIRMED` com pagamento; a fonte PlantUML gera os dois SVGs com ícones AWS e caixas externas explícitas; o README identifica recursos AWS declarados, não aplicados.
**Tests:** Renderizar os dois diagramas a partir do PlantUML; verificar XML, links locais e inspeção visual; executar o gate documental do README.
**Gate:** Docs, XML, Visual.

**Status:** Complete; as duas sequências foram renderizadas da mesma fonte PlantUML com ícones AWS da standard library local, inspecionadas em PNG e incluídas no README. README e design explicam a distinção Outbox/SQS, retry, deduplicação de inbox, módulo externo em caixa-preta e status não aplicado da AWS.

## Phase Execution Map

```text
Phase 1: T01 → T02
Phase 2: T02 → T03 → T04 → T05 → T06 → T07
Phase 3: T07 → T08 → T09 → T10 → T11 → T12
Phase 4: T12 → T13 → T14 → T15 → T16
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
| T12 | T11 | T11 → T12 | OK |
| T13 | T12 | T12 → T13 | OK |
| T14 | T13 | T13 → T14 | OK |
| T15 | T14 | T14 → T15 | OK |
| T16 | T15 | T15 → T16 | OK |

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
| T12 | Concorrência | integration + sensor | integration + sensor | OK |
| T13 | SVG AWS | XML + visual + README | XML + visual + README | OK |
| T14 | Auditoria de imagens | texto + XML + visual + README | texto + XML + visual + README | OK |
| T15 | Contrato visual de resultados | README + XML + visual + sensor | README + XML + visual + sensor | OK |
| T16 | Fonte diagram-as-code | Renderização PlantUML + XML + visual + README | Renderização PlantUML + XML + visual + README | OK |
