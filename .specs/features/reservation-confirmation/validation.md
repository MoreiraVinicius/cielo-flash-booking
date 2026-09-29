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

## Verificação final independente da documentação (fallback TLC)

**Veredito: PASS para os critérios documentais AC6 e AC7.** A tentativa de despachar um subagente Verifier falhou antes de iniciar, pois a conta atingiu o limite de uso. Foi executada a revisão standalone de fallback prevista pelo TLC: rederivei os resultados diretamente da spec e revisei os commits `ef04c42..22d5c04`, o README e os desenhos renderizados. Este veredito cobre T13–T14; a verificação funcional anterior T01–T12 permanece registrada acima.

| Critério | Resultado esperado pela spec | Evidência |
| --- | --- | --- |
| AC6 — fluxo AWS numerado | Criação PENDING/outbox → responsável único → resolução completa → inbox/decisão PostgreSQL → resultado; incluir expiração/cancelamento e estado não aplicado, sem IAM | `spec.md:128`; sequência e ramos em `docs/images/flash-booking-confirmation-aws-components.svg:126`, `:159`, `:166`, `:173`, `:180`, `:190`, `:198`; README incorpora a figura em `README.md:90`. Render final inspecionado sem clipping. |
| AC7 — auditoria e atualização | Todos os desenhos correntes coerentes; históricos/alvos identificados | Inventário cobre cada imagem em `documentation-audit.md:23` e linhas 25–46; legendas históricas no README em `README.md:136`, `:146`; nova outbox em `README.md:134`. |
| Três eventos na criação | ReservationCreated, ReservationExpirationScheduled e ReservationHeld seguem destinos distintos | `CreateReservationService.java:59`, `:60`, `:61`; roteamento em `OutboxSqsPublisher.java:80`, `:81`, `:82`; desenho em `docs/images/flash-booking-transactional-outbox.svg:47`, `:55`, `:63`. |
| Nome do cancelamento | O evento declarado usa o nome integral | `JdbcReservationPersistenceAdapter.java:172`, `OutboxSqsPublisher.java:86` e `docs/images/flash-booking-confirmation-lifecycle.svg:39`. |

**Sensor documental:** em uma cópia temporária do lifecycle, troquei `ReservationCancellationRequested` por `CancellationRequested`. A asserção do contrato que exige o nome canônico falhou (mutação morta). A cópia foi removida e o `git status --porcelain` real permaneceu idêntico ao baseline.

**Gates finais:** `validate-readme.ps1` PASS (7 imagens, 47 referências); `validate_spec.py` PASS; `validate_tasks.py` PASS; 19 SVGs parseados como XML; `git diff --check` PASS. O verificador automático de estado deve ser executado depois deste registro.
