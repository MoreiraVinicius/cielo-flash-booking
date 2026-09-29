# Tarefas do resumo executivo da performance do evento

## Execution Protocol

Execute aprovado pelo usuario. Implementar uma tarefa por vez, testar, atualizar rastreabilidade e criar commit atomico antes da proxima. O usuario autorizou separadamente a publicacao na AWS e a ativacao da chave global em 2026-09-29. A entrega Discord real depende do encerramento de um evento elegivel, nao de um POST de teste.

**Design:** `.specs/features/event-executive-summary/design.md`
**Status:** T01-T09 com validacao independente PASS; T10 aplicado na demo, aguardando verificacao final
**Task count:** 10

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Schema e estado JDBC | integration | Flag default off, atualizacao global, claim unico e invariantes | `src/test/java/**/*IT.java` | `./mvnw.cmd verify -Pintegration` |
| Comando e inventario | integration | API global, idempotencia, horario PostgreSQL, limites `startsAt`/`endsAt` | `src/test/java/**/*IT.java` | `./mvnw.cmd verify -Pintegration` |
| Agregacao comercial | integration | Corte em `endsAt`, fluxo, validade, ritmo, eventos isolados | `src/test/java/**/*IT.java` | `./mvnw.cmd verify -Pintegration` |
| Texto e adapters externos | unit | Template, prompt limitado, validacao, falha/fallback, payload Discord | `src/test/java/**/*Test.java` | `./mvnw.cmd test` |
| Worker e consulta HTTP | integration | Fechamento automatico, concorrencia, uma tentativa, status e GET sem efeitos externos | `src/test/java/**/*IT.java` | `./mvnw.cmd verify -Pintegration` |
| Terraform e runbook | static | IAM minimo, ARN, rotas IAM e passos operacionais | `infra/modules/**/*.tftest.hcl` | `terraform test` no modulo alterado |
| Demo AWS | operational | Task worker saudavel, rotas publicadas e ativacao global confirmada | ECS, API Gateway e logs CloudWatch | AWS CLI na conta demo autorizada |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Java puro | `./mvnw.cmd test` |
| Full | PostgreSQL ou HTTP | `./mvnw.cmd verify -Pintegration` |
| Infra | Terraform | `terraform fmt -check -recursive` e `terraform test` nos modulos alterados |
| Build | Ultima tarefa | `./mvnw.cmd clean verify -Pintegration`, `terraform fmt -check -recursive`, `terraform test` nos modulos alterados e `git diff --check` |

## Execution Plan

```text
T01 -> T02 -> T03 -> T04 -> T05 -> T06 -> T07 -> T08 -> T09 -> T10
```

## Task Breakdown

### T01: Persistir controle global e relatorio

**Status:** Complete
**What:** Adicionar controle singleton global, marco de primeiro zero e armazenamento idempotente do resumo.
**Where:** `src/main/resources/db/migration/V5__add_event_executive_summary.sql`
**Depends on:** none
**Requirement:** EXECSUM-01, EXECSUM-09, EXECSUM-10, EXECSUM-14, EXECSUM-39, EXECSUM-41, EXECSUM-42, EXECSUM-43
**Done when:** A chave e criada `false`; uma linha de resumo por evento; estados de entrega aceitos sao `NOT_CONFIGURED/SENT/FAILED/UNKNOWN`; timestamps usam `TIMESTAMPTZ`; contagens sao nao negativas; URLs e PII nao sao armazenadas.
**Tests:** integration, default, constraints e claim concorrente no PostgreSQL.
**Gate:** Full
**Commit:** `feat(summary): persist global control and event report`

**Gate result:** `verify -Pintegration` passed (73 tests, 0 failures/errors/skips); focused schema verification passed (5 tests, 0 failures/errors/skips).

**Test Adequacy Review:**

| Done-when criterion / spec AC / listed edge case | `file:line` + assertion expression | Spec-defined outcome | Covered? |
| --- | --- | --- | --- |
| Global control is created disabled and singleton | `ExecutiveSummarySchemaIT.java:44` - `.isFalse()`; `:45` - `.isEqualTo(1L)`; `:47` - duplicate insert throws `DataAccessException` | One row, `enabled=false`; second singleton row rejected | Yes |
| One report per event; only allowed delivery states | `ExecutiveSummarySchemaIT.java:55` - duplicate event insert throws; `:57` - `SENDING` insert throws | Duplicate `event_id` and states outside `NOT_CONFIGURED/SENT/FAILED/UNKNOWN` rejected | Yes |
| Numeric facts cannot be negative | `ExecutiveSummarySchemaIT.java:64` - negative `accepted_tickets` insert throws | Nonnegative check constraints reject negative values | Yes |
| Timestamps and counts use required types | `ExecutiveSummarySchemaIT.java:69` - `first_available_zero_at` is `timestamp with time zone`; `:70` - `as_of` is `timestamp with time zone`; `:71` - `accepted_tickets` is `bigint` | PostgreSQL preserves instants and wide ticket counts | Yes |
| No PII, Discord URL or raw prompt in report table | `ExecutiveSummarySchemaIT.java:82` - `doesNotContain("customer_id", "customer_name", "customer_email", "webhook_url", "prompt")` | Report table has no customer identity, secret URL or prompt column | Yes |

*Check C - Necessary:* all five assertions/groups above map to the T01 `Done when` constraints and corresponding EXECSUM requirements; no unclaimed tests added.

**Verdict:** all T01 schema criteria have assertion-level evidence; no shallow or out-of-scope assertions.

### T02: Expor ativacao manual global

**Status:** Complete
**What:** Implementar `PUT /executive-summary/activation`, sem endpoint ou propriedade de ativacao por evento.
**Where:** command API, controller, servico e testes HTTP
**Depends on:** T01
**Requirement:** EXECSUM-01, EXECSUM-02, EXECSUM-03, EXECSUM-04, EXECSUM-05, EXECSUM-27, EXECSUM-28, EXECSUM-29
**Done when:** `true` abre uma janela global e define `enabledAt` apenas na transicao off->on; repeticoes nao reiniciam janela; `false` impede novos claims; corpo invalido retorna `400` sem alteracao; resposta apresenta o estado persistido.
**Tests:** integration para transicoes, repeticao, validacao e concorrencia com fechamento.
**Gate:** Full
**Commit:** `feat(summary): add global report activation command`

**Gate result:** Focused `verify -Pintegration -Dit.test=ExecutiveSummaryActivationIT` passed; 63 unit tests and 4 focused integration tests passed (0 failures/errors/skips).

**Test Adequacy Review:**

| Done-when criterion / spec AC / listed edge case | `file:line` + assertion expression | Spec-defined outcome | Covered? |
| --- | --- | --- | --- |
| Enabling returns persisted global state and timestamp | `ExecutiveSummaryActivationIT.java:54` - PUT `enabled=true`, response `enabled=true`, `enabledAt` present | Operator can see persisted activation state | Yes |
| Repeating enable does not reopen the window | `ExecutiveSummaryActivationIT.java:63` - capture DB timestamp; `:72-73` - DB timestamp remains equal | Repeated PUT is idempotent for activation window | Yes |
| Disabling closes current window and persists disabled state | `ExecutiveSummaryActivationIT.java:77` - PUT false; `:85` - timestamp is null; `:88-91` - DB off/null assertions | No current active window remains | Yes |
| Missing, null, and non-boolean input returns 400 without mutation | `ExecutiveSummaryActivationIT.java:96` - three invalid request bodies; `:109` - enabledAt unchanged | Validation failure does not alter the global control | Yes |
| Concurrent enable requests preserve one global window | `ExecutiveSummaryActivationIT.java:114` - four concurrent PUTs; `:131` - enabled is true; DB singleton from T01 | Concurrent calls cannot create per-event state or lose activation | Yes |

*Check C - Necessary:* all five assertion groups map to T02 `Done when` and EXECSUM-02/03/07; no unclaimed tests added.

### T03: Registrar marco de inventario e horario de aceite

**Status:** Complete
**What:** Registrar o primeiro zero enquanto a flag global esta ligada e usar o relogio PostgreSQL da decisao de aceite em `reservation.created_at` e expiracao.
**Where:** `JdbcInventoryOperations`, `CreateReservationService` e testes existentes
**Depends on:** T02
**Requirement:** EXECSUM-14, EXECSUM-31, EXECSUM-38
**Done when:** Marco e decremento sao atomicos, nao mudam apos reposicao, nao sao gravados com flag desligada, e espera por lock nao adianta o inicio da reserva ou seu prazo.
**Tests:** integration para capacidade, reposicao, flag, limite temporal e concorrencia.
**Gate:** Full
**Commit:** `fix(summary): record authoritative inventory timestamps`

**Gate result:** Focused `verify -Pintegration -Dtest=CreateReservationServiceTest -Dit.test=InventoryConcurrencyIT` passed; 4 unit tests and 6 PostgreSQL integration tests passed (0 failures/errors/skips).

**Test Adequacy Review:**

| Done-when criterion / spec AC / listed edge case | `file:line` + assertion expression | Spec-defined outcome | Covered? |
| --- | --- | --- | --- |
| Atomic decrement returns accepted decision time and rejects insufficient capacity | `InventoryConcurrencyIT.java:50` - accepted decrement is present, insufficient decrement is empty; capacity checked after each | Inventory result is aligned with the database update | Yes |
| First zero is recorded only while global activation is enabled | `InventoryConcurrencyIT.java:108` - zero while disabled leaves marker null; enabling then decrementing records it | Disabled flag incurs no event marker; enabled flag captures first exhaustion | Yes |
| Marker is stable after inventory is replenished and exhausted again | `InventoryConcurrencyIT.java:119-132` - stored marker equals first captured timestamp after restock and second decrement | “First” exhaustion remains the first even if seats return | Yes |
| Acceptance time is after waiting for event-row lock | `InventoryConcurrencyIT.java:135` - hold `FOR UPDATE`; `:163` - returned time is at least 450 ms later | Lock waiting cannot backdate the acceptance | Yes |
| Reservation start and expiry use acceptance time | `CreateReservationServiceTest.java:62-63` - `createdAt == acceptedAt`, `expiresAt == acceptedAt + holdDuration` | Hold period starts after inventory acceptance | Yes |

*Check C - Necessary:* all five assertions map to T03 `Done when` and EXECSUM-14/31/38; no unclaimed tests added.

### T04: Apurar fatos no encerramento

**Status:** Complete
**What:** Criar leitura JDBC por evento com corte temporal em `endsAt`.
**Where:** adapter JDBC de fatos e testes PostgreSQL
**Depends on:** T03
**Requirement:** EXECSUM-11, EXECSUM-12, EXECSUM-13, EXECSUM-15, EXECSUM-16, EXECSUM-17, EXECSUM-18, EXECSUM-26, EXECSUM-32, EXECSUM-35, EXECSUM-37
**Done when:** Fluxo acumulado e validade no fechamento sao distintos; cancelamentos/vencimentos posteriores nao alteram o fechamento; pico, primeiros cinco minutos, ausencia de reservas e divergencia retornam valores definidos pela spec; eventos nao se misturam.
**Tests:** integration cobrindo cenarios temporais, dois eventos, zero, quantidade maior que um e apuracao parcial.
**Gate:** Full
**Commit:** `feat(summary): aggregate event facts at close`

**Gate result:** Focused `verify -Pintegration -Dtest=CreateReservationServiceTest -Dit.test=ExecutiveSummaryFactsIT` passed; 4 unit tests and 4 PostgreSQL integration tests passed (0 failures/errors/skips).

**Test Adequacy Review:**

| Done-when criterion / spec AC / listed edge case | `file:line` + assertion expression | Spec-defined outcome | Covered? |
| --- | --- | --- | --- |
| Volume, valid reservations, cancellations and expiry reconstructed at `endsAt` | `ExecutiveSummaryFactsIT.java:33` - four reservations include pre-close cancel, expires by close, and post-close cancel; `:47-55` - exact counts/tickets asserted | Post-close changes do not rewrite the closing snapshot; volume and validity remain distinct | Yes |
| Peak minute and first-five-minute count use quantities and deterministic minute grouping | `ExecutiveSummaryFactsIT.java:53-55` - peak timestamp/tickets and first-five count asserted | Quantities larger than one contribute to rhythm and minute peak | Yes |
| Aggregates remain scoped to one event; short/empty events omit rhythm | `ExecutiveSummaryFactsIT.java:61` - two separate events; `:77-85` - zero/one-event totals and null rhythm asserted | No cross-event mixing; omit first-five for <10 minutes and no activity | Yes |
| Inconsistent close partition is explicitly incomplete | `ExecutiveSummaryFactsIT.java:90` - impossible expired state; `:98-101` - accepted total differs from close partition and `complete=false` | Downstream renderer can omit inconsistent numbers and avoid Bedrock | Yes |
| Event without `endsAt` and missing event do not produce facts | `ExecutiveSummaryFactsIT.java:105` - both `read` results are empty | Only events with a commercial close can be summarized | Yes |

*Check C - Necessary:* all five test groups map to T04 `Done when` and EXECSUM-11/12/13/15/16/17/18/26/32/35/37; no unclaimed tests added.

### T05: Renderizar template e leitura opcional

**Status:** Complete
**What:** Implementar o template deterministico em pt-BR, leitura curta Bedrock e consulta limitada de sinais CloudWatch.
**Where:** renderer e adapters AWS de analytics, com testes unitarios
**Depends on:** T04
**Requirement:** EXECSUM-19, EXECSUM-20, EXECSUM-21, EXECSUM-22, EXECSUM-23, EXECSUM-25, EXECSUM-30, EXECSUM-36
**Done when:** Resultado e numeros nao dependem da IA; prompt tem ate 2 KB e nao contem PII/log/alarme; no maximo uma chamada LLM por evento com reservas, saida validada ate 120 tokens e sem retry; falhas nao apagam fatos; alertas sao rotulados como contexto do ambiente e nunca atribuicao causal.
**Tests:** unit com payloads explicitamente verificados, sucesso, vazio, falha, limite e fallback.
**Gate:** Quick
**Commit:** `feat(summary): add bounded narrative and alert context`

**Gate result:** Focused `test -Dtest=ExecutiveSummaryRendererTest,BedrockExecutiveNarrativeTest,CloudWatchOperationalSignalsReaderTest,ExecutiveSummaryPropertiesTest` passed (11 tests, 0 failures/errors/skips).

**Test Adequacy Review:**

| Done-when criterion / spec AC / listed edge case | `file:line` + assertion expression | Spec-defined outcome | Covered? |
| --- | --- | --- | --- |
| Deterministic output contains closing result, tempo, caveat and pt-BR timestamps | `ExecutiveSummaryRendererTest.java:14-36` - exact capacity, accepted/valid volume, first zero, peak share, caveat, Sao Paulo time | The report is understandable without reading internal statuses | Yes |
| No transitions omits operation; partial history explains the limitation | `ExecutiveSummaryRendererTest.java:38-50` - `EMPTY` omits section and `PARTIAL` includes fixed note | Silence means no transition observed; partial query is not presented as no alarm | Yes |
| Inconsistent facts omit numbers; operation displays at most two friendly labels | `ExecutiveSummaryRendererTest.java:53-72` - third signal omitted and incomplete facts omit metrics/narrative | No invented conclusion or raw alarm details | Yes |
| Bedrock receives only permitted aggregates and one bounded low-randomness request | `BedrockExecutiveNarrativeTest.java:33-54` - model id, 120 token cap, temperature, prompt byte limit and PII/name/log exclusions asserted | One short optional interpretation; no PII, alarms or logs in prompt | Yes |
| Invalid or unsafe Bedrock text is discarded; zero activity skips inference | `BedrockExecutiveNarrativeTest.java:56-79` - digits, purchase, causality, alarm/failure language, 3 sentences and 121 output tokens rejected; empty/incomplete facts not sent | Bedrock cannot override numbers or add unsupported claims | Yes |
| CloudWatch queries are bounded and only ALARM transitions become friendly signals | `CloudWatchOperationalSignalsReaderTest.java:29-53` - `STATE_UPDATE`, 10 record bound, interval, friendly label, OK omitted | Only configured transition labels and times reach the report | Yes |
| Empty, paginated/partial, malformed and unavailable histories remain distinguishable | `CloudWatchOperationalSignalsReaderTest.java:55-88` - status `EMPTY/PARTIAL/UNAVAILABLE` asserted | Missing coverage is never reported as no alerts | Yes |
| Model, timeout and alarm allowlist bind with validation | `ExecutiveSummaryPropertiesTest.java:17-45` - model, 12-second override, alarm label; zero and 61-second timeout fail | Configuration is typed, externally overridable and bounded | Yes |

*Check C - Necessary:* all eight evidence groups map to T05 `Done when` and EXECSUM-19/20/21/22/23/25/30/36; no unclaimed tests added.

### T06: Gerar automaticamente no fechamento

**Status:** Complete
**What:** Adicionar varredura do worker, elegibilidade global, claim unico, persistencia do relatorio e transicoes `PARTIAL/READY`.
**Where:** servico/scheduler de resumo e testes unitarios e de integracao
**Depends on:** T05
**Requirement:** EXECSUM-03, EXECSUM-06, EXECSUM-07, EXECSUM-08, EXECSUM-09, EXECSUM-10, EXECSUM-27, EXECSUM-33, EXECSUM-34
**Done when:** So eventos iniciados durante a ativacao atual e encerrados com flag ligada sao elegiveis; antes de `endsAt` nao ha chamadas externas; workers concorrentes nao duplicam claim; GET/varreduras nao repetem inferencia; queda conserva Markdown parcial consultavel.
**Tests:** integration com relogio de banco controlado, eventos elegiveis/ineligiveis, concorrencia e falhas.
**Gate:** Full
**Commit:** `feat(summary): generate one report after event close`

**Gate result:** `verify -Pintegration -Dit.test=EventSummarySchedulerIT -Dtest=ExecutiveSummaryRendererTest` passou (3 testes de integracao PostgreSQL + 3 testes unitarios; 0 falhas/erros/skips).

**Test Adequacy Review:**

| Done-when criterion / spec AC / listed edge case | `file:line` + assertion expression | Spec-defined outcome | Covered? |
| --- | --- | --- | --- |
| Flag desligada e evento ainda aberto nao consultam servicos externos | `EventSummarySchedulerIT.java:68-79` - zero reports; `verify(signals, never())`; `verify(narrative, never())` antes e depois da ativacao, com `ends_at` futuro | Nenhum resumo/custo antes do fechamento ou com a chave global desligada | Yes |
| Duas varreduras concorrentes produzem um claim/relatorio e uma tentativa de cada adapter | `EventSummarySchedulerIT.java:82-112` - duas chamadas paralelas; `reportCount == 1`, `status == READY`; `verify(signals).read`; `verify(narrative).write` apos varredura adicional | Uma linha por evento e no maximo uma inferencia/consulta por reivindicacao vencedora | Yes |
| Markdown comercial e claim sao persistidos antes da entrega | `EventSummarySchedulerIT.java:97-109` - estado final e Markdown persistido com fatos/leitura; `delivery_status == NOT_CONFIGURED`; sem linha Discord no conteudo | Conteudo publico nao depende de confirmacao de entrega e ja e duravel antes de T07 | Yes |
| Falha externa preserva Markdown parcial e nao e repetida | `EventSummarySchedulerIT.java:114-132` - excecao simulada, segunda varredura, `status == PARTIAL`, `error_code`, reader uma vez | Claim parcial continua consultavel sem retry/custo duplicado | Yes |
| Renderer nao expoe estado de entrega | `ExecutiveSummaryRendererTest.java:13-33` - `doesNotContain("Discord:", "SENT", ...)` | O mesmo texto salvo pode ser publicado sem status especulativo | Yes |

*Check C - Necessary:* os cinco grupos de evidencia cobrem elegibilidade, unicidade, persistencia anterior a efeitos externos, falha e estabilidade do conteudo Discord; nenhuma assercao fora da tarefa.

**Verdict:** criterios T06 demonstrados com PostgreSQL real em Testcontainers e adapters externos simulados; nenhuma chamada AWS real.

### T07: Publicar no Discord com segredo configuravel

**Status:** Complete
**What:** Implementar publicador Discord, leitura lazy de Secrets Manager, IAM restrito e configuracao Terraform por ARN.
**Where:** adapter HTTP, propriedades, `infra/modules/compute/main.tf`, testes Java e Terraform
**Depends on:** T06
**Requirement:** EXECSUM-39, EXECSUM-40, EXECSUM-41, EXECSUM-42, EXECSUM-43, EXECSUM-44
**Done when:** URL fica apenas em `SecretString`; o arquivo local `demo.tfvars` guarda somente ARN; sem ARN nao consulta AWS; tamanho nao excede 2.000 caracteres; o sistema persiste `UNKNOWN` antes do POST e nunca repete; resultado confirmado/falha atualiza estado; IAM permite leitura apenas do segredo escolhido.
**Tests:** unit com Secrets Manager/HTTP simulados e `terraform test` para IAM/config.
**Gate:** Infra
**Commit:** `feat(summary): publish reports through configured Discord webhook`

**Gate result:** 9 testes unitarios passaram; `ExecutiveSummaryDeliveryIT` e `EventSummarySchedulerIT` passaram (4 integracao PostgreSQL); `terraform test` no modulo compute passou (2 runs; 0 falhas).

**Test Adequacy Review:**

| Done-when criterion / spec AC / listed edge case | `file:line` + assertion expression | Spec-defined outcome | Covered? |
| --- | --- | --- | --- |
| ARN vazio nao consulta o publisher/segredo | `ExecutiveSummaryDeliveryServiceTest.java:31-36` - `verify(markDeliveryNotConfigured)`, `never(beginDelivery)`, `never(publish)` | Entrega opcional nao faz chamada Secrets Manager/Discord sem configuracao | Yes |
| `UNKNOWN` e persistido antes do unico POST e o resultado confirmado persiste | `ExecutiveSummaryDeliveryServiceTest.java:40-50` - `InOrder(beginDelivery, publish, finishDelivery)` | Mensagem nao e repetida apos resultado ambiguo | Yes |
| Reivindicacao de entrega concorrente nao inicia outro POST | `ExecutiveSummaryDeliveryServiceTest.java:54-61` - `beginDelivery` vazio e `never(publish/finishDelivery)` | Estado nao-`NOT_CONFIGURED` impede qualquer segunda tentativa | Yes |
| PostgreSQL marca a tentativa como UNKNOWN e confirma SENT uma unica vez | `ExecutiveSummaryDeliveryIT.java:44-70` - primeira chamada retorna Markdown, estado UNKNOWN; segunda retorna vazio; SENT grava `delivery_confirmed_at` | Invariante de entrega protegida atomicamente por estado persistido | Yes |
| URL invalida e timeout tem resultados seguros; segredo e lido sob demanda e cacheado | `SecretsManagerDiscordSummaryPublisherTest.java:31-68` - uma leitura para duas mensagens; URI HTTPS Discord; invalido nao envia; IOException vira UNKNOWN | Nenhum host arbitrario, segredo nao e carregado antes da publicacao e timeout ambiguo nao causa retry | Yes |
| Mensagem longa preserva resultado, esgotamento, ritmo e ressalva sob 2.000 caracteres | `DiscordSummaryMessageFormatterTest.java:12-26` - `hasSizeLessThanOrEqualTo(2_000)` e secao opcional removida | Texto continua util em publico Discord | Yes |
| Terraform passa so o ARN e restringe IAM; sem ARN nao concede GetSecretValue | `compute.tftest.hcl:97-105, 136-140` - variavel do container corresponde ao ARN e `Resource` e lista de uma entrada; sem ARN `anytrue` falso | Nenhum webhook URL no ambiente e nenhum acesso a segredo nao configurado | Yes |

*Check C - Necessary:* os sete grupos provam configuracao opcional, sequenciamento, unicidade, classificacao de resultado, limite de mensagem e menor privilégio AWS; sem assertions estranhas ao comportamento.

**Verdict:** T07 cobre a publicacao com adapters simulados, estado PostgreSQL concorrente e Terraform mocked, sem publicar em um Discord real nem acessar AWS.

### T08: Expor consulta e documentar operacao

**Status:** Complete
**What:** Expor `GET /events/{id}/executive-summary`, publicar as rotas via API Gateway IAM e documentar ativacao, encerramento e setup do Discord.
**Where:** query API, infraestrutura de borda e runbook operacional da demo
**Depends on:** T07
**Requirement:** EXECSUM-06, EXECSUM-24, EXECSUM-28, EXECSUM-29, EXECSUM-40, EXECSUM-44
**Done when:** GET retorna relatorio e `deliveryStatus` persistidos, `404` para evento inexistente, sem chamadas externas ou recomputacao; rotas usam IAM/SigV4; README explica onde criar/configurar webhook, como informar ARN no `demo.tfvars` e como ativar/desativar a flag global.
**Tests:** HTTP para estados e `404`; Terraform para rotas/autenticacao; `git diff --check` para documentacao.
**Gate:** Build
**Commit:** `feat(summary): expose report status and document operations`

**Gate result:** `clean verify -Pintegration` passou (80 unitarios + 90 integracao, 0 falhas/erros/skips); `terraform fmt -check -recursive` passou; `terraform test` passou no modulo `compute` (2 runs) e `edge-observability` (1 run); `git diff --check` passou.

**Test Adequacy Review:**

| Done-when criterion / spec AC / listed edge case | `file:line` + assertion expression | Spec-defined outcome | Covered? |
| --- | --- | --- | --- |
| GET reports `DISABLED`, `SCHEDULED`, `NOT_ELIGIBLE`, and persisted report/status/timestamps without recomputing Markdown | `ExecutiveSummaryQueryIT.java:50-85` - JSON status/body assertions; after changing event source, stored Markdown remains `# Saved report` | Reader shows current eligibility or durable report; a read does not trigger work or rewrite history | Yes |
| Missing event returns `404` | `ExecutiveSummaryQueryIT.java:89-92` - `status().isNotFound()` | Unknown event is not represented as an empty report | Yes |
| Query endpoint reads report and reports missing rows through the application error mapping | `ExecutiveSummaryQueryController.java:17-20`; `GetExecutiveSummaryService.java:16-20`; `JdbcExecutiveSummaryReportReader.java:24-44` - single read-only SELECT, empty result raises `ResourceNotFoundException` | GET has no command side effects or external adapter dependency | Yes |
| Both endpoints use IAM and VPC Link; GET/PUT reach the intended internal services and deploy trigger includes them | `edge-observability.tftest.hcl:72-79, 81-94` - IAM, VPC Link, listener target, method/path/integration and deployment trigger assertions | SigV4 protected routes reach query-api/command-api through the private ALB | Yes |
| Operator can configure Discord without storing webhook URL and can manually open/close the global window | `infra/environments/demo/README.md:7-18, 20-49` - SecretString, ARN-only `demo.tfvars`, SigV4 enable/GET/disable commands; `:51` confirms no live calls | Human-readable runbook supports setup and safe local verification without real publication | Yes |

*Check C - Necessary:* the five evidence groups cover every T08 criterion and EXECSUM-06/24/28/29/40/44; no assertions beyond the agreed behavior.

**Verdict:** consulta HTTP e rotas passaram em PostgreSQL/Testcontainers e Terraform simulado. Nenhum deploy, acesso AWS real ou publicacao Discord foi executado.

### T09: Fechar lacunas de evidencia e neutralidade dos alertas

**Status:** Complete
**What:** Demonstrar limites da janela de ativacao, espera de transacoes de inventario, datas/fallback do template, caminhos sem reservas/fatos inconsistentes e consulta sem efeitos; qualificar alertas como sinais do ambiente sem relacao causal confirmada.
**Where:** testes PostgreSQL/HTTP e renderer executivo
**Depends on:** T08
**Requirement:** EXECSUM-02, EXECSUM-04, EXECSUM-05, EXECSUM-08, EXECSUM-11, EXECSUM-17, EXECSUM-20, EXECSUM-25, EXECSUM-26, EXECSUM-27, EXECSUM-30, EXECSUM-33, EXECSUM-34, EXECSUM-36
**Done when:** Testes verificam que ativacao nao cria relatorio, eventos anteriores/retirados da janela nao sao recuperados, claim espera commit de reserva concorrente, inicio/fim/apuracao aparecem em America/Sao_Paulo e inicio usa `createdAt` quando `startsAt` e nulo; saida cobre zero reservas, fatos inconsistentes sem Bedrock, claim persistido antes de falha abrupta, sinais comuns a dois eventos sem atribuicao, e GET nao chama adapters externos.
**Tests:** unit e PostgreSQL integration direcionados; `clean verify -Pintegration`, gates Terraform afetados e validadores TLC.
**Gate:** Build
**Commit:** `test(summary): prove close timing and neutral reporting`

**Gate result:** `clean verify -Pintegration` passou (81 unitarios + 95 integracao em 22 grupos; 0 falhas/erros/skips); `terraform fmt -check -recursive` passou; `terraform test` nos modulos compute (2 runs) e edge-observability (1 run) passou; `validate_tasks.py --strict` e `git diff --check` passaram.

**Test Adequacy Review:**

| Done-when criterion / spec AC / listed edge case | `file:line` + assertion expression | Spec-defined outcome | Covered? |
| --- | --- | --- | --- |
| Activation alone creates no report; an event closed while disabled and events started before later activation are not recovered | `ExecutiveSummaryActivationIT.java:54-63` - report row count is zero; `EventSummarySchedulerIT.java:124-146` - event remains unreported while open, is closed after disabling, then remains unreported after reactivation; prior-start event remains unreported and `verify(signals/narrative, never())` | A reactivated global flag only permits events that began during the current window | Yes |
| Close claim waits for an inventory transaction and includes its committed reservation | `EventSummarySchedulerIT.java:148-197` - scan future remains incomplete while the transaction holds the event row, then persisted `accepted_tickets == 1` and Markdown includes the reservation | First post-close scan observes accepted inventory work that held the event row before claim | Yes |
| Report displays all three timestamps in Sao Paulo time and falls back to `createdAt` | `ExecutiveSummaryRendererTest.java:29-31` - exact `Início/Fim/Apurado` local timestamps; `ExecutiveSummaryFactsIT.java:113-130` - null `starts_at` resolves to stored `created_at` and renders local start/end/as-of | No scheduled start remains understandable and time values use the specified zone | Yes |
| No activity renders zero without rhythm indicators; inconsistent facts preserve partial output and skip all external adapters | `ExecutiveSummaryRendererTest.java:38-45` - zero-reservation wording and no peak/first-five note; `EventSummarySchedulerIT.java:202-232` - `PARTIAL`, incomplete note, inconsistent metric absent, signals/narrative `never()` | Do not imply absent demand, and do not infer or call AI from inconsistent aggregates | Yes |
| Shared operational signal is explicitly neutral for either event; a committed claim remains durable across worker interruption | `ExecutiveSummaryRendererTest.java:76-91` - same signal rendered for two event instances with environment/no-confirmed-relation text; `EventSummarySchedulerIT.java:235-258` - prior claim remains one `PARTIAL` Markdown and later scan calls no adapters/delivery | Environment alarms do not claim event causality; persisted claim is not recomputed/retried | Yes |
| Repeated GET does not call analysis or delivery adapters | `ExecutiveSummaryQueryIT.java:99-104` - `never().read/write/publish` after repeated GETs | GET only reads the saved snapshot and causes no external cost | Yes |

*Check C - Necessary:* os seis grupos de evidencia enderecam as lacunas do primeiro Verifier para EXECSUM-02/04/05/08/11/17/20/25/26/27/30/33/34/36; sem assertions alheias ao contrato.

**Verdict:** as correcoes de evidencia passaram unitarios e PostgreSQL/Testcontainers. Os registros externos continuam mockados; nenhum deploy, chamada AWS real ou publicacao Discord foi executado.

### T10: Publicar a demo e ativar a janela global

**Status:** Complete
**What:** Publicar a imagem verificada e o webhook configurado na demo; ajustar a tolerancia de bootstrap do worker observada no primeiro rollout e ativar a chave global somente depois de estabilizar os tres servicos.
**Where:** Modulo Terraform `infra/modules/compute`, ambiente demo e AWS CLI
**Depends on:** T09
**Requirement:** EXECSUM-01..05, EXECSUM-08..10, EXECSUM-39..44
**Done when:** Query e command usam a imagem `summary-7c23aec` e estao estaveis; worker usa a mesma imagem com `startPeriod=120` e task `HEALTHY`; o metodo PUT existe no API Gateway e responde `200` com `enabled=true` e `enabledAt`. Nenhum POST de teste e enviado ao Discord.
**Tests:** `terraform fmt -check`, `terraform validate` e `terraform test` do modulo compute; plano remoto limitado; `aws ecs wait services-stable`, `describe-tasks` e `apigateway test-invoke-method`.
**Gate:** Infra + operacional
**Commit:** `fix(infra): allow worker startup before health checks`

**Gate result:** `terraform validate` passou; `terraform test` passou 2/2. O plano corretivo remoto alterou somente a task definition e o servico worker. ECS confirmou worker revision 10 `HEALTHY` e servicos estaveis. O PUT retornou `200`, `enabled=true` e `enabledAt=2026-09-29T07:07:45.090815Z`. Bedrock e Discord reais ainda nao foram exercitados, pois requerem um evento iniciado depois da ativacao e encerrado durante ela.

## Traceability Review

| Requirement group | Tasks | Evidence planned |
| --- | --- | --- |
| EXECSUM-01..05, 27..29 | T01, T02, T06, T08, T09 | Persistencia e API global, elegibilidade e erros |
| EXECSUM-06..10, 33..34 | T01, T06, T08, T09 | Claim, fechamento, estados e consulta sem efeitos externos |
| EXECSUM-11..19, 26, 31..32, 35, 37..38 | T03, T04, T05, T09 | Marco, horario de aceite, agregacao e template |
| EXECSUM-20..23, 25, 30, 36 | T05, T09 | Alertas contextuais, limites Bedrock, falha e ausencia de reserva |
| EXECSUM-39..44 | T01, T07, T08, T10 | Estado, configuracao, segredo, entrega unica, limite de mensagem e infraestrutura publicada |

## Test Co-location Validation

| Tasks | Layer | Matrix requires | Planned | Result |
| --- | --- | --- | --- | --- |
| T01-T04, T06, T09 | JDBC/worker | integration | integration in each task | Match |
| T05, T07, T09 | AWS adapters/renderer | unit | unit in each task | Match |
| T07-T08 | Terraform | static | `terraform test` in affected modules | Match |
| T02, T08 | HTTP | integration | controller integration tests | Match |
