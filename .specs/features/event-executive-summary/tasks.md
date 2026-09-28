# Tarefas do resumo executivo da performance do evento

## Execution Protocol

Execute aprovado pelo usuario. Implementar uma tarefa por vez, testar, atualizar rastreabilidade e criar commit atomico antes da proxima. Isto autoriza apenas alteracoes locais e commits. Nao fazer deploy, chamadas AWS reais ou publicacao Discord real.

**Design:** `.specs/features/event-executive-summary/design.md`
**Status:** Approved for local execution
**Task count:** 8

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Schema e estado JDBC | integration | Flag default off, atualizacao global, claim unico e invariantes | `src/test/java/**/*IT.java` | `./mvnw.cmd verify -Pintegration` |
| Comando e inventario | integration | API global, idempotencia, horario PostgreSQL, limites `startsAt`/`endsAt` | `src/test/java/**/*IT.java` | `./mvnw.cmd verify -Pintegration` |
| Agregacao comercial | integration | Corte em `endsAt`, fluxo, validade, ritmo, eventos isolados | `src/test/java/**/*IT.java` | `./mvnw.cmd verify -Pintegration` |
| Texto e adapters externos | unit | Template, prompt limitado, validacao, falha/fallback, payload Discord | `src/test/java/**/*Test.java` | `./mvnw.cmd test` |
| Worker e consulta HTTP | integration | Fechamento automatico, concorrencia, uma tentativa, status e GET sem efeitos externos | `src/test/java/**/*IT.java` | `./mvnw.cmd verify -Pintegration` |
| Terraform e runbook | static | IAM minimo, ARN, rotas IAM e passos operacionais | `infra/modules/**/*.tftest.hcl` | `terraform test` no modulo alterado |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Java puro | `./mvnw.cmd test` |
| Full | PostgreSQL ou HTTP | `./mvnw.cmd verify -Pintegration` |
| Infra | Terraform | `terraform fmt -check -recursive` e `terraform test` nos modulos alterados |
| Build | Ultima tarefa | `./mvnw.cmd clean verify -Pintegration`, `terraform fmt -check -recursive`, `terraform test` nos modulos alterados e `git diff --check` |

## Execution Plan

```text
T01 -> T02 -> T03 -> T04 -> T05 -> T06 -> T07 -> T08
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

**Status:** Planned
**What:** Implementar `PUT /executive-summary/activation`, sem endpoint ou propriedade de ativacao por evento.
**Where:** command API, controller, servico e testes HTTP
**Depends on:** T01
**Requirement:** EXECSUM-01, EXECSUM-02, EXECSUM-03, EXECSUM-04, EXECSUM-05, EXECSUM-27, EXECSUM-28, EXECSUM-29
**Done when:** `true` abre uma janela global e define `enabledAt` apenas na transicao off->on; repeticoes nao reiniciam janela; `false` impede novos claims; corpo invalido retorna `400` sem alteracao; resposta apresenta o estado persistido.
**Tests:** integration para transicoes, repeticao, validacao e concorrencia com fechamento.
**Gate:** Full
**Commit:** `feat(summary): add global report activation command`

### T03: Registrar marco de inventario e horario de aceite

**Status:** Planned
**What:** Registrar o primeiro zero enquanto a flag global esta ligada e usar o relogio PostgreSQL da decisao de aceite em `reservation.created_at` e expiracao.
**Where:** `JdbcInventoryOperations`, `CreateReservationService` e testes existentes
**Depends on:** T02
**Requirement:** EXECSUM-14, EXECSUM-31, EXECSUM-38
**Done when:** Marco e decremento sao atomicos, nao mudam apos reposicao, nao sao gravados com flag desligada, e espera por lock nao adianta o inicio da reserva ou seu prazo.
**Tests:** integration para capacidade, reposicao, flag, limite temporal e concorrencia.
**Gate:** Full
**Commit:** `fix(summary): record authoritative inventory timestamps`

### T04: Apurar fatos no encerramento

**Status:** Planned
**What:** Criar leitura JDBC por evento com corte temporal em `endsAt`.
**Where:** adapter JDBC de fatos e testes PostgreSQL
**Depends on:** T03
**Requirement:** EXECSUM-11, EXECSUM-12, EXECSUM-13, EXECSUM-15, EXECSUM-16, EXECSUM-17, EXECSUM-18, EXECSUM-26, EXECSUM-32, EXECSUM-35, EXECSUM-37
**Done when:** Fluxo acumulado e validade no fechamento sao distintos; cancelamentos/vencimentos posteriores nao alteram o fechamento; pico, primeiros cinco minutos, ausencia de reservas e divergencia retornam valores definidos pela spec; eventos nao se misturam.
**Tests:** integration cobrindo cenarios temporais, dois eventos, zero, quantidade maior que um e apuracao parcial.
**Gate:** Full
**Commit:** `feat(summary): aggregate event facts at close`

### T05: Renderizar template e leitura opcional

**Status:** Planned
**What:** Implementar o template deterministico em pt-BR, leitura curta Bedrock e consulta limitada de sinais CloudWatch.
**Where:** renderer e adapters AWS de analytics, com testes unitarios
**Depends on:** T04
**Requirement:** EXECSUM-19, EXECSUM-20, EXECSUM-21, EXECSUM-22, EXECSUM-23, EXECSUM-25, EXECSUM-30, EXECSUM-36
**Done when:** Resultado e numeros nao dependem da IA; prompt tem ate 2 KB e nao contem PII/log/alarme; no maximo uma chamada LLM por evento com reservas, saida validada ate 120 tokens e sem retry; falhas nao apagam fatos; alertas sao rotulados como contexto do ambiente e nunca atribuicao causal.
**Tests:** unit com payloads explicitamente verificados, sucesso, vazio, falha, limite e fallback.
**Gate:** Quick
**Commit:** `feat(summary): add bounded narrative and alert context`

### T06: Gerar automaticamente no fechamento

**Status:** Planned
**What:** Adicionar varredura do worker, elegibilidade global, claim unico, persistencia do relatorio e transicoes `PARTIAL/READY`.
**Where:** servico/scheduler de resumo e testes unitarios e de integracao
**Depends on:** T05
**Requirement:** EXECSUM-03, EXECSUM-06, EXECSUM-07, EXECSUM-08, EXECSUM-09, EXECSUM-10, EXECSUM-27, EXECSUM-33, EXECSUM-34
**Done when:** So eventos iniciados durante a ativacao atual e encerrados com flag ligada sao elegiveis; antes de `endsAt` nao ha chamadas externas; workers concorrentes nao duplicam claim; GET/varreduras nao repetem inferencia; queda conserva Markdown parcial consultavel.
**Tests:** integration com relogio de banco controlado, eventos elegiveis/ineligiveis, concorrencia e falhas.
**Gate:** Full
**Commit:** `feat(summary): generate one report after event close`

### T07: Publicar no Discord com segredo configuravel

**Status:** Planned
**What:** Implementar publicador Discord, leitura lazy de Secrets Manager, IAM restrito e configuracao Terraform por ARN.
**Where:** adapter HTTP, propriedades, `infra/modules/compute/main.tf`, testes Java e Terraform
**Depends on:** T06
**Requirement:** EXECSUM-39, EXECSUM-40, EXECSUM-41, EXECSUM-42, EXECSUM-43, EXECSUM-44
**Done when:** URL fica apenas em `SecretString`; o arquivo local `demo.tfvars` guarda somente ARN; sem ARN nao consulta AWS; tamanho nao excede 2.000 caracteres; o sistema persiste `UNKNOWN` antes do POST e nunca repete; resultado confirmado/falha atualiza estado; IAM permite leitura apenas do segredo escolhido.
**Tests:** unit com Secrets Manager/HTTP simulados e `terraform test` para IAM/config.
**Gate:** Infra
**Commit:** `feat(summary): publish reports through configured Discord webhook`

### T08: Expor consulta e documentar operacao

**Status:** Planned
**What:** Expor `GET /events/{id}/executive-summary`, publicar as rotas via API Gateway IAM e documentar ativacao, encerramento e setup do Discord.
**Where:** query API, `infra/modules/edge-observability/main.tf`, `infra/environments/demo/README.md` e exemplos Terraform
**Depends on:** T07
**Requirement:** EXECSUM-06, EXECSUM-24, EXECSUM-28, EXECSUM-29, EXECSUM-40, EXECSUM-44
**Done when:** GET retorna relatorio e `deliveryStatus` persistidos, `404` para evento inexistente, sem chamadas externas ou recomputacao; rotas usam IAM/SigV4; README explica onde criar/configurar webhook, como informar ARN no `demo.tfvars` e como ativar/desativar a flag global.
**Tests:** HTTP para estados e `404`; Terraform para rotas/autenticacao; `git diff --check` para documentacao.
**Gate:** Build
**Commit:** `feat(summary): expose report status and document operations`

## Traceability Review

| Requirement group | Tasks | Evidence planned |
| --- | --- | --- |
| EXECSUM-01..05, 27..29 | T01, T02, T06, T08 | Persistencia e API global, elegibilidade e erros |
| EXECSUM-06..10, 33..34 | T01, T06, T08 | Claim, fechamento, estados e consulta sem efeitos externos |
| EXECSUM-11..19, 26, 31..32, 35, 37..38 | T03, T04, T05 | Marco, horario de aceite, agregacao e template |
| EXECSUM-20..23, 25, 30, 36 | T05 | Alertas contextuais, limites Bedrock, falha e ausencia de reserva |
| EXECSUM-39..44 | T01, T07, T08 | Estado, configuracao, segredo, entrega unica e limite de mensagem |

## Test Co-location Validation

| Tasks | Layer | Matrix requires | Planned | Result |
| --- | --- | --- | --- | --- |
| T01-T04, T06 | JDBC/worker | integration | integration in each task | Match |
| T05, T07 | AWS adapters/renderer | unit | unit in each task | Match |
| T07-T08 | Terraform | static | `terraform test` in affected modules | Match |
| T02, T08 | HTTP | integration | controller integration tests | Match |
