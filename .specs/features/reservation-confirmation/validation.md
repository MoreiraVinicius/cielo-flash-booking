# Validação da confirmação externa da reserva

**Data:** 2026-09-29
**Spec:** `.specs/features/reservation-confirmation/spec.md`
**Diff avaliado:** T01–T06 nos commits `bd3e376..4b7ce5c`; T07–T12 na árvore de trabalho (inclui os arquivos de documentação e diagramas ainda não commitados).
**Verificador:** agente independente do implementador; inspeção read-only de código e testes.

## Resultado

**Overall: ✅ PASS.** T12 corrigiu a ordem de locks que causava deadlock e acrescentou cobertura para os três casos antes sem prova: resoluções com IDs distintos concorrendo pela mesma reserva, confirmação após cancelamento vencedor e confirmação após `event.endsAt` mas antes de `expiresAt`. O build completo e a integração focada passaram, e a mutação do mapeamento de `confirmedAt` foi morta em scratch isolado.

## Tarefas

| Tarefa | Estado | Evidência observada |
|---|---|---|
| T01–T06 | ✅ Feitas | Commits de contrato, inbox/schema, decisão sob lock, filas, consumidor e outbox listados acima. |
| T07 | ✅ Feita | Projeções, consulta/cancelamento HTTP, resumo e fluxo local cobertos pelos testes abaixo. |
| T08–T09 | ✅ Feitas | `tasks.md` registra renderização/inspeção; SVGs atuais contêm títulos e descrições acessíveis e representam contexto, containers e lifecycle. A vista `flash-booking-data-model.svg` foi renderizada novamente e passou em revisão visual independente: caixa preta externa, filas direcionais, decisão no PostgreSQL e rótulos sem corte. |
| T10–T11 | ✅ Feitas | Gates README/Postman passaram; auditoria documental cobre guias, limites de infraestrutura e operação local. |
| T12 | ✅ Feita | `ReservationResolutionProcessor` bloqueia a reserva antes de inserir/consultar inbox; três cenários de concorrência/fronteira temporal passaram em integração e o sensor matou a mutação de `confirmedAt`. |

## Acceptance criteria ancorados à spec

| Critério | Resultado esperado na spec | Evidência `arquivo:linha` + assertiva observada | Resultado |
|---|---|---|---|
| P1 Confirmação AC1 | Antes de `expiresAt`, `PENDING→CONFIRMED`, preenche `confirmedAt`, não altera `available`. | `src/test/java/com/cielo/flashbooking/reservation/controller/ReservationDeadlineIT.java:185-203` — estado/timestamp e `available == 7`; após lock ultrapassar prazo, `:207-224` — `EXPIRED`, capacidade 10. | ✅ |
| P1 Confirmação AC2 | GET retorna `CONFIRMED` e `confirmedAt`. | `src/test/java/com/cielo/flashbooking/reservation/controller/ReservationQueryControllerIT.java:78-84` — status 200, estado e campo não vazio. README `:50-73` exclui interpretação financeira. | ✅ |
| P1 Confirmação AC3 | Inexistente → resultado `NOT_FOUND`, sem estoque. | `src/test/java/com/cielo/flashbooking/reservation/confirm/SqsReservationConfirmationConsumerIT.java:319-335` — inbox `NOT_FOUND`, `reservation_id IS NULL`, mensagem consumida. | ✅ |
| P1 Confirmação AC4 | Envelope v1 estrito e ≤16 KiB é validado; inválido não altera reserva e permanece para retry/DLQ; IAM delimita produtor. | `src/test/java/com/cielo/flashbooking/reservation/confirm/ReservationResolutionMessageTest.java:67-82` — versão, campos extras, JSON tipo incorreto e corpo >16 KiB rejeitados; `SqsReservationConfirmationConsumerIT.java:402-416` — inválido mantido para retry; `infra/modules/data-plane/data-plane.tftest.hcl:90-99` e `infra/modules/compute/compute.tftest.hcl:97-107` — principal/ações e recursos exatos. | ✅ |
| P1 Confirmação AC5 | Reserva é confirmada inteira ou não confirmada. | `src/test/java/com/cielo/flashbooking/reservation/controller/ReservationDeadlineIT.java:185-203` — fixture de quantidade 3 confirmada como uma reserva e capacidade permanece 7; `SqsReservationConfirmationConsumerIT.java:357-400` — quantidade 4 percorre a mesma transição inteira. | ✅ |
| P1 Confirmação AC6 | Criar PENDING grava `ReservationHeld` com identidade e dados requeridos na mesma transação. | `src/test/java/com/cielo/flashbooking/reservation/controller/ReservationControllerIT.java:64-118` — evento na outbox, ID estável, tipo/v1, quantidade e ausência de PII; transação de criação no teste. | ✅ |
| P1 Concorrência AC1 | Expiração não libera `CONFIRMED` nem `CANCELLATION_PENDING`. | `src/test/java/com/cielo/flashbooking/reservation/controller/ReservationDeadlineIT.java:254-282` — `expire(...) == false`, estado pendente de cancelamento, capacidade 7 e uma solicitação. | ✅ |
| P1 Concorrência AC2 | Se cancelamento/expiração vence, confirmação posterior rejeita com causa e não devolve estoque novamente. | `src/test/java/com/cielo/flashbooking/reservation/confirm/SqsReservationConfirmationConsumerIT.java:193-207` — estado `CANCELLED`, outcome `CANCELLED`, um evento de rejeição e um fechamento; `:337-355` prova o caminho `EXPIRED`. | ✅ |
| P1 Concorrência AC3 | DELETE confirmado grava `CANCELLATION_PENDING` + outbox, retorna 202 e conserva estoque. | `src/test/java/com/cielo/flashbooking/reservation/controller/ReservationQueryControllerIT.java:88-108` — duas respostas 202, um evento e `available == 7`. | ✅ |
| P1 Concorrência AC4 | Lock que cruza deadline causa `EXPIRED` e devolução única. | `src/test/java/com/cielo/flashbooking/reservation/controller/ReservationDeadlineIT.java:207-224` — bloqueio até deadline e `assertTerminalState`, incluindo capacidade e um `ReservationHoldClosed`. | ✅ |
| P1 Concorrência AC5 | `capacity - available` equivale à soma de reservas que comprometem capacidade após as transações. | Transições e valores de estoque são verificados em `ReservationDeadlineIT.java:185-203,228-281`, `SqsReservationConfirmationConsumerIT.java:121-156,196-249` e `ReservationQueryControllerIT.java:88-108`; resumo também classifica estados confirmados/pendentes em `src/test/java/com/cielo/flashbooking/event/summary/ExecutiveSummaryFactsIT.java:33-84`. | ✅ |
| P1 Decisão confiável AC1 | Inbox grava identidade, fingerprint, resultado na decisão. | `src/test/java/com/cielo/flashbooking/reservation/confirm/SqsReservationConfirmationConsumerIT.java:121-139` — uma inbox, outcome confirmado e evento de resultado; `InitialSchemaIT.java:164-212` valida chaves/constraints da inbox. | ✅ |
| P1 Decisão confiável AC2 | Redelivery sem efeito novo; divergência sob a mesma chave conflita e segue retry. | `SqsReservationConfirmationConsumerIT.java:121-139` — IDs SQS distintos, uma inbox/transição; `:232-263` — payload divergente, segunda reserva não muda e mensagem continua com receive count 2; `ReservationResolutionMessageTest.java:46-63` — formatação JSON não altera fingerprint. | ✅ |
| P1 Decisão confiável AC3 | Resultado é inserido na outbox junto à decisão da inbox/reserva. | `ReservationResolutionProcessorTest.java:21-54` — resultado, `inbox.complete` e `addReservationConfirmationResultOutboxEvent`; integração `SqsReservationConfirmationConsumerIT.java:130-139` verifica o resultado persistido. | ✅ |
| P1 Decisão confiável AC4 | Falha na publicação preserva evento para nova tentativa. | `src/test/java/com/cielo/flashbooking/adapter/out/messaging/publisher/OutboxSqsPublisherIT.java:194-208` — primeira publicação nula, contador incrementa, segundo envio marca publicado. | ✅ |
| P1 Decisão confiável AC5 | Mensagem exaurida fica recuperável na DLQ com observabilidade. | `infra/modules/data-plane/data-plane.tftest.hcl:35-55` — redrive para DLQs distintas, SSE, long polling e alarmes; execução do smoke local do T04 está registrada em `tasks.md:88-94`. Não foi repetida durante esta verificação. | ✅ Gate anterior; sem replay independente da DLQ |
| P1 Decisão confiável AC6 | Rejeição posterior à operação externa é correlacionada; compensação é responsabilidade externa. | `SqsReservationConfirmationConsumerIT.java:337-355` verifica rejeição/outcome por expiração; `README.md:67-73` e `context.md` delimitam compensação ao responsável externo, sem estado financeiro local. | ✅ Limite de responsabilidade |
| P1 Decisão confiável AC7 | DELETE de PENDING fecha e emite `ReservationHoldClosed(CANCELLED)` na transação da devolução. | `src/test/java/com/cielo/flashbooking/reservation/controller/ReservationQueryControllerIT.java:150-173` — `CANCELLED`, estoque 10 e exatamente um evento. | ✅ |
| P1 Decisão confiável AC8 | Republishing conserva identidade e roteia só ao responsável único, que deduplica fora do sistema. | `OutboxSqsPublisherIT.java:148-192` — cinco tipos somente na fila owner, atributo `outboxEventId`; `ReservationControllerIT.java:100-118` compara ID de payload com ID da outbox. Deduplicação da operação externa é contrato documentado, não implementação deste sistema. | ✅ Fronteira documentada |
| P1 Decisão confiável AC9 | Apenas a transição que vence `PENDING→EXPIRED` registra um fechamento. | `ReservationDeadlineIT.java:317-338` — estado terminal, capacidade 10 e count do evento 1; `SqsReservationConfirmationConsumerIT.java:337-355` verifica expiração por confirmação tardia. | ✅ |
| P1 Decisão confiável AC10 | Mensagem de resultado/fechamento é recuperável sem acompanhamento financeiro local. | `OutboxSqsPublisherIT.java:194-208` prova retry; `README.md:71-73` documenta compensação externa e a ausência de acompanhamento no Flash Booking. | ✅ |
| P1 Decisão confiável AC11 | Identidades de resolução distintas disputam a mesma reserva com lock adquirido antes da referência FK da inbox; cada resolução tem resultado e só uma transição/efeito de estoque ocorre. | `src/main/java/com/cielo/flashbooking/reservation/confirm/ReservationResolutionProcessor.java:34-35` — lock precede `inbox.tryBegin`; `src/main/java/com/cielo/flashbooking/adapter/out/persistence/reservation/JdbcReservationPersistenceAdapter.java:34-40` — `SELECT ... FOR UPDATE`; `src/test/java/com/cielo/flashbooking/reservation/confirm/SqsReservationConfirmationConsumerIT.java:164-188` — resultados `CONFIRMED`/`ALREADY_CONFIRMED`, duas inboxes, capacidade permanece 7 e futures têm timeout de 5s. | ✅ |
| P1 Cancelamento AC1 | DELETE confirmado cria ID, estado e outbox atomicamente, sem liberar capacidade. | `ReservationQueryControllerIT.java:88-108` — 202, `CANCELLATION_PENDING`, estoque 7 e apenas uma solicitação; `ReservationDeadlineIT.java:264-281` verifica ID estável no estado e payload. | ✅ |
| P1 Cancelamento AC2 | Conclusão correlacionada leva a `CANCELLED` e devolve uma vez. | `SqsReservationConfirmationConsumerIT.java:264-287` — estado, estoque 10, cancellationId e replay duplicado com uma inbox. | ✅ |
| P1 Cancelamento AC3 | Sem resposta externa, estado permanece pendente e estoque retido. | `ReservationQueryControllerIT.java:88-108` e `SqsReservationConfirmationConsumerIT.java:289-300` — mismatch preserva estado/estoque. | ✅ |
| P1 Cancelamento AC4 | Reentrega da mesma conclusão não repete devolução. | `SqsReservationConfirmationConsumerIT.java:264-287` — duas mensagens, uma resolução e estoque volta a 10 apenas uma vez. | ✅ |
| P1 Cancelamento AC5 | Falha/espera conserva `CANCELLATION_PENDING`; nunca reverte a `CONFIRMED`. | `SqsReservationConfirmationConsumerIT.java:302-317` — rollback conserva pendência e estoque; `ReservationDeadlineIT.java:254-281` — retries locais preservam cancellationId e um pedido. | ✅ |
| P1 Cancelamento AC6 | DELETE repetido retorna pendente sem segundo evento. | `ReservationQueryControllerIT.java:88-108` — ambos 202, count do pedido igual a um. | ✅ |
| P1 README AC1 | README identifica os cinco estados e não introduz status financeiro. | `README.md:50-73` — estados e fluxo; seção declara que serviço não processa pagamentos. Gate `validate-readme.ps1` passou. | ✅ |
| P1 README AC2 | Contexto C4 mostra ator, sistemas e responsabilidades. | `README.md:82-83`; `docs/images/flash-booking-confirmation-c4-context.svg:1-3` — `title`, `desc`, fronteiras e ator. | ✅ |
| P1 README AC3 | Containers C4 mostra processos, dados, filas e limites corretos. | `README.md:86-87`; `docs/images/flash-booking-confirmation-c4-containers.svg:1-3` — `title`/`desc`, APIs, worker, PostgreSQL e filas; inbox/outbox são dados. | ✅ |
| P1 README AC4 | Diagrama cobre aceite, atraso, duplicidade, corrida e cancelamento pendente. | `README.md:90-91`; `docs/images/flash-booking-confirmation-lifecycle.svg:1-3` — `desc` explicita atraso, duplicidade, expiração e cancelamento. | ✅ |
| P1 README AC5 | SVG legível/acessível, ligado ao README e verdadeiro sobre pagamento/deploy. | README `:82-94`; SVGs usam `role="img"`, `aria-labelledby`, `<title>`/`<desc>`; validador README passou (6 imagens, 44 referências). `tasks.md` registra renderização e inspeção das figuras. | ✅ |

## Edge cases

- [x] Compensação posterior a uma rejeição fica sob responsabilidade do sistema externo; não há confirmação retroativa: `README.md:67-73`.
- [x] Duas resoluções **distintas** disputando a mesma reserva: `SqsReservationConfirmationConsumerIT.java:164-188` — resultados `CONFIRMED`/`ALREADY_CONFIRMED`, duas inboxes finalizadas e capacidade preservada; futures limitados a 5s, para deadlock falhar em tempo finito.
- [x] `endsAt` posterior à criação não bloqueia confirmação antes de `expiresAt`: `SqsReservationConfirmationConsumerIT.java:211-229` — venda encerrada, prazo da reserva ainda futuro, outcome `CONFIRMED` e capacidade inalterada.
- [x] Cancelamento que vence primeiro não permite confirmação nem nova devolução: `SqsReservationConfirmationConsumerIT.java:193-207` — outcome `CANCELLED`, um evento de rejeição e estoque terminal.
- [x] Fila atrasada além de `expiresAt` resulta em rejeição explícita e fechamento único: `SqsReservationConfirmationConsumerIT.java:337-355`.

## Discrimination sensor

**Profundidade:** leve, 1 mutação de comportamento em scratch isolado (`.tmp/reservation-confirmation-sensor`). Mutação: o mapeamento de `confirmed_at` para `ReservationDetails` em `JdbcReservationPersistenceAdapter.java:471-474` foi substituído por `null`; comando executado no scratch: `mvnw.cmd --batch-mode -s C:\Users\vinic\projetos\cielo\.tmp\maven-settings.xml -Pintegration "-Dit.test=ReservationQueryControllerIT#get_whenReservationIsConfirmed_exposesTheConfirmationTimestamp" verify`.

| Mutação | Resultado |
|---|---|
| `JdbcReservationPersistenceAdapter.java:471-474` — `confirmedAt` sempre nulo | ✅ Morta: o teste real na cópia mutada recebeu `confirmedAt:null` e falhou exatamente na asserção `ReservationQueryControllerIT.java:84` (`jsonPath("$.confirmedAt").isNotEmpty()`). O gate mutante terminou com uma falha de teste esperada. |

O primeiro build sem elevação falhou ao ler dependências no cache Maven; a repetição elevada, conforme a política do sandbox, compilou e executou o teste. A cópia continha apenas `src`, `.mvn`, POM e wrappers; nenhum diretório de `artefatos/` foi copiado. A árvore de trabalho principal permaneceu byte-a-byte igual no status Git (55 itens) antes e depois da execução do sensor; o scratch foi removido após a coleta do resultado.

**Resultado do sensor:** 1 mutação / 1 morta / 0 sobreviventes — **PASS**.

## Gates

| Gate | Evidência | Resultado |
|---|---|---|
| Build (`mvnw.cmd --batch-mode -s .tmp/maven-settings.xml -Pintegration verify`) | Execução do orquestrador nesta árvore após T12: BUILD SUCCESS; Surefire 98 testes unitários, Failsafe 118 integrações; 0 falhas, 0 erros, 0 skips. | ✅ |
| Integração focada T12 | `SqsReservationConfirmationConsumerIT`: 13 testes após adicionar timeout de 5s aos futures concorrentes. | ✅ 13 passaram |
| Spec | `validate_spec.py .specs/features/reservation-confirmation/spec.md` | ✅ 0 erros, 0 avisos |
| Tasks | `validate_tasks.py .specs/features/reservation-confirmation/tasks.md` | ✅ 0 erros, 0 avisos |
| README | `powershell -ExecutionPolicy Bypass -File scripts/validate-readme.ps1` | ✅ 6 imagens e 45 referências locais; vista de dados/integração renderizada e inspecionada após revisão das fronteiras e rótulos |
| Postman | `powershell -ExecutionPolicy Bypass -File scripts/validate-postman.ps1` | ✅ |
| Diff | `git diff --check` | ✅ (avisos Git de normalização LF/CRLF, sem erro) |

**Contagem:** baseline pre-feature no commit `bd3e376^`: 170 declarações de teste (`@Test`/equivalentes); agora 210 (+40 declarações) e 216 execuções observadas (98 unitárias + 118 integrações; expansões de casos explicam a diferença). Nenhum teste foi removido no diff avaliado. Nenhum skip no gate completo.

## Qualidade e gaps

| Verificação | Resultado |
|---|---|
| Escopo solicitado e sem pagamentos implementados | ✅ |
| Padrões existentes e mudanças de runtime local coerentes com boundaries | ✅ |
| Critérios ancorados nos resultados definidos | ✅ 33/33 critérios cobertos; sem gap de precisão |
| Testes por camada cobrem confirmação, concorrência, inbox/outbox, HTTP e projeções | ✅ T12 adiciona os cenários antes descobertos |
| Cada teste relacionado ao escopo mapeia um requisito | ✅ Amostra conferida |
| Diretrizes do repositório | ✅ `.specs/STATE.md`, `AGENTS.md`, `tasks.md` e Java/Spring patterns atuais |

### Gaps ranqueados

Nenhum gap permanece nesta validação.

**Spec-anchored check:** 33/33 ACs cobertos; sem gap de precisão textual.
**Sensor:** 1/1 mutações mortas.
**Gate:** 216 passaram; 0 falharam; 0 ignorados.
**Próxima ação:** nenhuma; T01–T12 estão verificados nesta árvore.

## Revisão documental complementar

Após o commit `e959234`, a vista `flash-booking-data-model.svg` passou a dizer que a inbox reconhece a reentrega da mesma resolução e reapresenta o resultado salvo. A nova vista `flash-booking-confirmation-aws-components.svg` usa os componentes AWS declarados em Terraform, mostra as duas SQS com DLQ, mantém o responsável externo opaco e deixa autenticação fora do desenho, conforme solicitado. Essas mudanças não alteram contrato nem runtime.

| Gate documental | Resultado |
| --- | --- |
| SVGs | XML válido; ambas as vistas renderizadas e inspecionadas sem texto cortado. |
| README | `validate-readme.ps1`: 7 imagens e 46 referências locais; PASS. |
| Tasks | `validate_tasks.py`: 0 erros e 0 avisos. |
| Diff | `git diff --check`: PASS. |
| Verificador independente | PASS na revisão read-only dos dois SVGs renderizados, README, spec e Terraform; sem clipping ou inconsistência de fronteira, e sem IAM/SigV4/ARN no novo SVG. |

O gate funcional anterior (98 unitários e 118 integrações) continua sendo a evidência do runtime; este complemento verifica apenas documentação e representação visual.

## Revisão visual 01–08 e inventário completo

T13 produziu a sequência numerada no SVG AWS (`ef04c42`): 01 entrada, 02 commit de PENDING/outbox, 03 entrega, 04 trabalho externo, 05 declaração de resolução, 06 inbox/lock/relógio, 07 estado e resultado transacionais, 08 resposta assíncrona. Os ramos mostram expiração/cancelamento de PENDING e cancelamento de CONFIRMED. O desenho mantém o responsável como caixa preta, marca os recursos AWS de confirmação como não aplicados e não contém IAM.

T14 auditou cada um dos 22 arquivos de `docs/images/` em `documentation-audit.md`. Seis SVGs vigentes/históricos foram corrigidos ou criados: `flash-booking-transactional-outbox.svg`, `flash-booking-confirmation-lifecycle.svg`, `flash-booking-last-ticket.svg`, `flash-booking-sequence-reservation.svg`, `flash-booking-aws-eventual-consistency.svg` e `flash-booking-hero.svg`. A antiga outbox PNG foi renomeada como baseline anterior à confirmação e retirada da vista vigente do README. O novo SVG mostra os três eventos que `CreateReservationService` grava e que `OutboxSqsPublisher` roteia; a notificação SES é de reserva temporária.

| Gate documental T13–T14 | Resultado |
| --- | --- |
| Inventário/XML | 22 imagens nomeadas na auditoria; 19 SVGs parseados; sequência 01–08 sem IAM. |
| Renderização | SVG AWS de T13 e os seis SVGs de T14 renderizados em Chrome headless e inspecionados; sem corte impeditivo. |
| README | `validate-readme.ps1`: PASS, 7 imagens e 47 referências locais. |
| Estrutura | `validate_spec.py` e `validate_tasks.py`: 0 erros, 0 avisos. |
| Diff | `git diff --check`: PASS. |

Esta revisão é documental; os testes funcionais de T01–T12 são a evidência do runtime local.

## Revisão standalone provisória antes da validação independente

**Resultado provisório, supersedido:** o fallback standalone foi usado quando o primeiro despacho do Verifier falhou por limite de uso. A revisão independente posterior encontrou a lacuna do nome visível no passo 08; portanto, a conclusão preliminar abaixo não fecha AC7. A revisão funcional T01–T12 continua registrada acima.

| Critério | Resultado esperado pela spec | Evidência |
| --- | --- | --- |
| AC6 — fluxo AWS numerado | Criação PENDING/outbox → responsável único → resolução completa → inbox/decisão PostgreSQL → resultado; incluir expiração/cancelamento e estado não aplicado, sem IAM | `spec.md:128`; sequência e ramos em `docs/images/flash-booking-confirmation-aws-components.svg:126`, `:159`, `:166`, `:173`, `:180`, `:190`, `:198`; README incorpora a figura em `README.md:90`. Render final inspecionado sem clipping. |
| AC7 — auditoria e atualização | Todos os desenhos correntes coerentes; históricos/alvos identificados | Inventário cobre cada imagem em `documentation-audit.md:23` e linhas 25–46; legendas históricas no README em `README.md:136`, `:146`; nova outbox em `README.md:134`. |
| Três eventos na criação | ReservationCreated, ReservationExpirationScheduled e ReservationHeld seguem destinos distintos | `CreateReservationService.java:59`, `:60`, `:61`; roteamento em `OutboxSqsPublisher.java:80`, `:81`, `:82`; desenho em `docs/images/flash-booking-transactional-outbox.svg:47`, `:55`, `:63`. |
| Nome do cancelamento | O evento declarado usa o nome integral | `JdbcReservationPersistenceAdapter.java:172`, `OutboxSqsPublisher.java:86` e `docs/images/flash-booking-confirmation-lifecycle.svg:39`. |

**Sensor documental:** em uma cópia temporária do lifecycle, troquei `ReservationCancellationRequested` por `CancellationRequested`. A asserção do contrato que exige o nome canônico falhou (mutação morta). A cópia foi removida e o `git status --porcelain` real permaneceu idêntico ao baseline.

**Gates finais:** `validate-readme.ps1` PASS (7 imagens, 47 referências); `validate_spec.py` PASS; `validate_tasks.py` PASS; 19 SVGs parseados como XML; `git diff --check` PASS. O verificador automático de estado deve ser executado depois deste registro.

## Resultado inicial do Verifier independente — falha corrigida (29/09/2026)

**Veredito: FAIL para o fechamento documental T13–T14.** A sequência, ramos, caixa preta externa, duas SQS com DLQs e o estado de AWS não aplicada estão corretos. O passo visível 08 abrevia o nome de rejeição e o gate atual não discrimina esse contrato.

| Critério | Resultado | Evidência independente |
| --- | --- | --- |
| AC6 — sequência e ramos AWS | PASS, com ressalva de rótulo | A sequência 01–08, ramo de `PENDING` encerrada e cancelamento de `CONFIRMED` estão em `docs/images/flash-booking-confirmation-aws-components.svg:126`, `:133`, `:140`, `:147`, `:159`, `:166`, `:173`, `:180`, `:190`, `:198`. Caixa preta/sem IAM/sem AWS aplicada no mesmo SVG `:3`, `:26`, `:98–109`. README vincula a vista em `README.md:88–90`. |
| AC7 — nomes atuais e inventário | FAIL parcial | O contrato em `src/main/java/com/cielo/flashbooking/adapter/out/persistence/reservation/JdbcReservationPersistenceAdapter.java:197` e `src/main/java/com/cielo/flashbooking/reservation/confirm/ReservationResolutionProcessor.java:50–51` é `ReservationConfirmed` / `ReservationConfirmationRejected`; o passo visual 08 diz `ReservationConfirmed ou Rejected` em `docs/images/flash-booking-confirmation-aws-components.svg:183`. A descrição acessível contém o nome completo em `:3`, mas o rótulo visível não. |
| Inventário e quantidade de eventos | PASS | Inventário contém 22 arquivos e nomeia todos em `documentation-audit.md:25–48`; conferência direta achou 22/22 cobertos e 19/19 SVGs XML válidos. A criação grava três eventos em `CreateReservationService.java:59–61`; o SVG da outbox os apresenta em separado e o README os identifica em `README.md:134–136`. Terraform declara as duas filas de integração e DLQs próprias em `infra/modules/data-plane/main.tf:121–160`; nenhum apply foi executado nesta revisão. |
| Rótulos de histórico/alvo | PASS | A legenda do README separa demo destruída e topologia high-load não aplicada em `README.md:141–147`; a auditoria classifica os 22 arquivos em `documentation-audit.md:27–48`. |

**Sensor documental:** cópia temporária do diretório de imagens, com `ReservationConfirmationRejected` abreviado para `Rejected` na descrição do SVG AWS. `validate-readme.ps1` passou (mutante sobreviveu); isso mostra que o gate não verifica o contrato da vista AWS. Scratch removido; `git status --porcelain=v1 -uall` permaneceu idêntico ao baseline preexistente.

**Gates executados:** `validate_spec.py` PASS (0 erros/avisos); `validate_tasks.py` PASS (0 erros/avisos); `validate-readme.ps1` PASS (7 imagens, 47 referências); XML PASS (19/19); `git diff --check ef04c42..b6d70b1` PASS. `validate_state.py` passa para o relatório anterior; depois deste complemento ele deve refletir o FAIL até re-verificação.

**Correção aplicada em T15:** o passo 08 agora exibe `ReservationConfirmationRejected` por extenso em `docs/images/flash-booking-confirmation-aws-components.svg:183–184`. `scripts/validate-readme.ps1:464–470` exige os nomes de resultado visíveis no desenho. O SVG foi renderizado e inspecionado.

**Sensor de T15:** cópia de `docs/images/` em `.tmp`; abreviar o rótulo visível para `Rejected` fez `validate-readme.ps1 -AwsVisualDirectory <scratch>` falhar na asserção `visible AWS confirmation sequence contract`; mutação morta, scratch removido e status real idêntico ao baseline.

**Gates T15:** `validate-readme.ps1` PASS; `validate_spec.py` PASS (0 erros/avisos); `validate_tasks.py` PASS (0 erros/avisos); XML AWS PASS; `git diff --check` PASS. Aguarda reexecução pelo Verifier independente.

### Resultado inicial do Verifier (supersedido por T15)

**Initial result (superseded by T15):** FAIL — AC7 label gap and one surviving documentation mutant. T15 corrige ambos e solicita nova verificação independente.

## Independent fresh re-verification of T13–T15 (29/09/2026)

**Verdict: FAIL — the visible SVG correction is present, but the T15 discriminator does not enforce the step 08 label.** Review scope: `.specs/features/reservation-confirmation/spec.md`, `tasks.md`, `documentation-audit.md`, `design.md`, `validation.md`, plus `git diff 22d5c04..53de354`.

### Spec-anchored evidence

| Criterion | Expected outcome | Independent evidence | Result |
| --- | --- | --- | --- |
| CONFIRM-04 AC6 (`spec.md:128`) | AWS sequence 01–08 communicates hold/outbox, owner work, resolution, PostgreSQL decision, result, cancellation/expiration branches, and un-applied AWS status without IAM | Visible sequence and branch labels in `docs/images/flash-booking-confirmation-aws-components.svg:26–28`, `:122–123`, `:125–185`, `:191–203`; AWS resources are separately declared in `infra/modules/data-plane/main.tf:121–160`. README embeds it at `README.md:88–90`. Fresh Chrome headless render at 1960×2070 inspected: sequence and branch text visible, no clipping; SVG parses as XML. | ✅ PASS |
| CONFIRM-04 AC7 (`spec.md:129`) | Current diagrams use current state names, complete message names, and event count in visible labels; historical/target diagrams are identified | Step 08 now visibly contains both full names at `docs/images/flash-booking-confirmation-aws-components.svg:183–184`. Runtime names match `ReservationResolutionProcessor.java:49–51`, outbox event producers at `CreateReservationService.java:59–61`, and routing at `OutboxSqsPublisher.java:80–86`. Three creation events are shown in `flash-booking-transactional-outbox.svg:47`, `:55`, `:63`, and summarized at `README.md:134–136`. README history/target labels are at `README.md:141–147`; inventory classifications are at `documentation-audit.md:27–48`. | ❌ GAP: visible step 08 label mutation survives the README gate (sensor below). |
| T15 Done when (`tasks.md:229`) | Gate fails if the full visible result name in step 08 is shortened | Replaced only step 08 `ReservationConfirmationRejected.</text>` with `Rejected.</text>` in a scratch copy of `docs/images/`; ran `validate-readme.ps1 -AwsVisualDirectory <scratch>`. It exited 0 and reported PASS because the whole-file search at `scripts/validate-readme.ps1:463–470` still found the same token at SVG lines 114 and 196. | ❌ GAP |
| Inventory and classification (`spec.md:129`, `documentation-audit.md:23–48`) | Every image inventoried and current/historic/target scope identifiable | Direct directory comparison: 22/22 filenames listed; 19 SVG + 3 PNG. All 19 SVGs parse as XML. README identifies historical and target scopes in the cited lines. | ✅ PASS |

### Gates and isolation

| Gate | Result |
| --- | --- |
| `validate_spec.py .specs/features/reservation-confirmation/spec.md` | PASS — 0 errors, 0 warnings |
| `validate_tasks.py .specs/features/reservation-confirmation/tasks.md` | PASS — 0 errors, 0 warnings |
| `scripts/validate-readme.ps1` | PASS — 7 README images, 47 local references, 3 performance scenarios |
| XML | PASS — 19/19 SVGs |
| Chrome headless render and visual inspection | PASS — current AWS SVG rendered; visible step 08 names legible and no clipping |
| `git diff --check 22d5c04..53de354` | PASS |
| `validate_state.py reservation-confirmation` before this report | FAIL — existing report verdict was FAIL; validator requires ranked gaps to be fixed before the feature is done |
| Scratch sensor | ❌ SURVIVED — one targeted step 08 abbreviation; gate exited 0 |
| Real working-tree status around sensor | PASS — `git status --porcelain=v1 -uall` was byte-for-byte equivalent before and after cleanup |

### Ranked gap

1. **Major — gate does not bind the required complete rejection name to visible step 08.** The SVG itself is corrected at `docs/images/flash-booking-confirmation-aws-components.svg:184`, but a one-line abbreviation there survives because `scripts/validate-readme.ps1:467` searches the entire SVG and identical full-name text remains at lines 114 and 196. Anchor the assertion to step 08’s text block (or an equivalent semantic region), then repeat this exact scratch mutation. No source/runtime or diagram correction is indicated by this review.

**Summary:** T13’s visible flow and T14’s 22-image audit pass this review. T15’s visible output is corrected, but its required semantic sensor remains weak; overall re-verification is FAIL pending a gate fix and re-run.

### T15 follow-up fix by author (awaiting independent re-verification)

Grouped the full visible step 08 under SVG id `step08-result` at `docs/images/flash-booking-confirmation-aws-components.svg:180–186`. The README validator now parses the SVG and inspects only visible text nodes inside that group (`scripts/validate-readme.ps1:464–475`), so matching names in the accessibility description or other blocks cannot satisfy this contract.

Repeated the exact targeted mutation: only the step 08 line was shortened to `Rejected.` in a scratch copy. `validate-readme.ps1 -AwsVisualDirectory <scratch>` now fails specifically at `visible AWS step 08 result contract`; 1/1 targeted mutation killed. Scratch was removed and real working tree status matched its baseline. The correct diagram passed the README, spec, tasks, XML and diff gates, and a fresh Chrome render was visually inspected.

This is author-side evidence only. A fresh independent Verifier must confirm the correction and write the final PASS before `validate_state.py` can close the feature.

## Fresh independent re-verification of T15 (29/09/2026)

**Verdict: FAIL — the T15 visible-label contract and targeted mutation pass, but this verifier could not independently complete the required rendered visual inspection.** Scope: `git diff 53de354..b16fdf2`, CONFIRM-04 AC6/AC7, the current image inventory, and the Java/Terraform contracts represented by the documentation. No implementation or spec change was made by this verifier.

### Spec-anchored evidence

| Criterion | Spec-defined outcome | Independent evidence | Result |
| --- | --- | --- | --- |
| CONFIRM-04 AC6 (`spec.md:128`) | Visible AWS sequence 01–08 shows the hold/outbox, external owner work, resolution, PostgreSQL decision, result and external reaction; includes expiration/cancellation branches and marks AWS resources unapplied without detailing IAM. | `docs/images/flash-booking-confirmation-aws-components.svg:26–28`, `:126–186`, `:195–205`; README embeds it at `README.md:88–90`. Terraform declares the two owner queues and dedicated DLQs at `infra/modules/data-plane/main.tf:121–160`. Source event creation/routing remains consistent at `CreateReservationService.java:59–61` and `OutboxSqsPublisher.java:80–86`. | ✅ Structural/content PASS; independent rendered inspection unavailable (see ranked gap). |
| CONFIRM-04 AC7 (`spec.md:129`) | Current visible diagrams use current states, complete message names and event counts; historic and target views are identified. | Step 08 group `step08-result` visibly contains `08`, `Entrega o resultado`, `ReservationConfirmed` and `ReservationConfirmationRejected` at `docs/images/flash-booking-confirmation-aws-components.svg:180–186`. Runtime result names match `ReservationResolutionProcessor.java:49–51` and persistence validation `JdbcReservationPersistenceAdapter.java:197–198`; three creation events match `CreateReservationService.java:59–61`, publisher routing `OutboxSqsPublisher.java:80–86`, and outbox labels `flash-booking-transactional-outbox.svg:47,55,63`. README marks historical and target scope at `README.md:141–147`; `documentation-audit.md:23–48` inventories all 22 image files and classifications. | ✅ PASS for source, labels, contracts and inventory. |
| T15 Done when (`tasks.md:229`) | Correct gate passes; abbreviating the visible step 08 result in a scratch copy fails. | Replaced only `<text x="70" y="1711" class="stepBody ink">ReservationConfirmationRejected.</text>` with `Rejected.` in a scratch copy of all `docs/images/`. `validate-readme.ps1 -AwsVisualDirectory <scratch>` exited 1 specifically: `visible AWS step 08 result contract is missing 'ReservationConfirmed ou ReservationConfirmationRejected.'`. Scratch was removed; `git status --porcelain=v1 -uall` before/after was byte-for-byte identical. | ✅ PASS; 1/1 targeted mutation killed. |

### Gates and isolation

| Gate | Result |
| --- | --- |
| `scripts/validate-readme.ps1` | PASS — 7 README images, 47 local references, 3 performance scenarios |
| `validate_spec.py .specs/features/reservation-confirmation/spec.md` | PASS — 0 errors, 0 warnings |
| `validate_tasks.py .specs/features/reservation-confirmation/tasks.md` | PASS — 0 errors, 0 warnings |
| XML | PASS — 19/19 SVGs parse |
| Inventory | PASS — 22/22 `docs/images/` files listed and classified |
| `git diff --check 53de354..b16fdf2` | PASS |
| Targeted scratch sensor | PASS — shortened visible step 08 result was rejected by the required contract assertion |
| Real working-tree status around sensor | PASS — unchanged byte-for-byte; pre-existing unrelated dirty files remained present |
| Independent rendered inspection | NOT VERIFIED — browser policy blocked opening the local SVG (`file:` protocol); the policy forbids alternate browser surfaces or indirect workarounds. Earlier render notes remain author-side evidence only. |

### Ranked gap

1. **Verification gap — independently render and inspect the final AWS SVG for legibility and clipping.** This is required by the diagram acceptance evidence; the browser rejected the local file protocol and explicitly disallowed alternate surfaces/workarounds. The SVG's visual content was unchanged in this diff apart from grouping step 08, but that does not substitute for fresh rendered inspection. No code, gate, state-name, event-count or inventory gap was found.

**Summary:** AC6/AC7 source and contract checks pass; the exact T15 abbreviation mutant is killed; all documentation gates pass. The independent verification remains FAIL until rendered inspection is independently completed. No Java or Terraform production files changed in the reviewed range.

## Validation: Reservation Confirmation T15 — PASS

**Final independent visual follow-up (29/09/2026):** Opened the provided final Chrome headless render `C:\Users\vinic\projetos\cielo\.tmp\aws-step08-final.png` with the local image inspection tool. Step 08 is legible: the number, heading, both full result names, and compensation note are visible inside the card. No text in step 08 is clipped; the full diagram's sequence cards and alternative-path panels are within the rendered canvas. This completes the only outstanding check recorded above. The PNG is author-generated evidence, but the screenshot inspection and conclusion here are independent.

**Updated verdict: PASS.** AC6/AC7 structural and contract checks remain PASS; the exact abbreviation mutant is killed; README/spec/task/XML/inventory/diff gates pass; rendered legibility and clipping inspection now passes. Ranked gaps: none.
