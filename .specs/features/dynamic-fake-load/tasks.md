# Tarefas da carga com dados fake dinâmicos

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow, including the per-task cycle, adequacy review, Verifier and discrimination sensor.

**If the skill cannot be activated, STOP and tell the user. Do not proceed without it.**

---

**Design:** `.specs/features/dynamic-fake-load/design.md`
**Status:** Draft

---

## Test Coverage Matrix

> Generated from `AGENTS.md`, the acceptance criteria, `pom.xml`, `compose.yaml`, `performance/demo/` and existing Java integration tests. The workload scripts are tests; their own contracts are verified by k6 inspection, deterministic contract scenarios and disposable Compose execution. No Java production layer changes in this plan.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Fake-data generator | contract | Same seed produces same sequence; all API bounds; 10,000 picks meet 60/30/10 ±2 pp; unique keys <=128 chars | `performance/dynamic-load/lib/*.js`, `performance/dynamic-load/contracts/*.js` | `k6 run performance/dynamic-load/contracts/fake-data-contract.js` |
| Profile/config policy | contract | Every profile validates required inputs, rejects invalid bounds and non-loopback URLs, and renders the expected executor shape | `performance/dynamic-load/lib/*.js`, `performance/dynamic-load/contracts/*.js` | `k6 run performance/dynamic-load/contracts/profile-contract.js` |
| k6 workload | performance integration | All routes in scope; technical/business classification; query/command distribution; complete smoke lifecycle | `performance/dynamic-load/workload.js` | `k6 inspect performance/dynamic-load/workload.js` then `./performance/dynamic-load/run.ps1 -Profile smoke -Workload mixed` |
| Runner and telemetry | integration | Compose lifecycle, two command processes, sampling cadence, partial evidence and cleanup on success/failure; CloudWatch is read-only and does not generate remote traffic | `performance/dynamic-load/*.ps1` | `./performance/dynamic-load/run.ps1 -Profile smoke -Workload mixed` |
| CloudWatch correlation adapter | contract | Valid query requires UTC window and declared resources; only accepted metrics/dimensions are requested; missing metric response becomes incomplete evidence | `performance/dynamic-load/cloudwatch.ps1`, `performance/dynamic-load/contracts/fixtures/` | `./performance/dynamic-load/cloudwatch.ps1 -ContractTest` |
| Normalized evidence | contract | Required provenance, per-route metrics, telemetry completeness, audit and truthful labels; malformed fixtures fail | `performance/dynamic-load/normalize.ps1`, `performance/dynamic-load/contracts/fixtures/` | `./performance/dynamic-load/normalize.ps1 -ContractTest` |
| Documentation | review | Commands, taxonomy, interpretation, safety and limitations match spec and executable defaults | `performance/dynamic-load/README.md`, `README.md` | `./scripts/validate-readme.ps1` plus directed review against spec |
| Java regression | unit + integration | Existing behavior remains unchanged and all current tests pass | `src/test/java/**/*Test.java`, `src/test/java/**/*IT.java` | `./mvnw.cmd clean verify -Pintegration` |

## Gate Check Commands

> Generated from the existing Maven, Compose and k6 harness. The new commands become authoritative only after task approval and implementation.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Contract | JavaScript, CloudWatch or normalizer contracts | `k6 run performance/dynamic-load/contracts/fake-data-contract.js`; `k6 run performance/dynamic-load/contracts/profile-contract.js`; `./performance/dynamic-load/cloudwatch.ps1 -ContractTest`; `./performance/dynamic-load/normalize.ps1 -ContractTest` |
| Inspect | Any k6 script change | `k6 inspect performance/dynamic-load/workload.js` |
| Smoke | Workload, telemetry or runner change | `./performance/dynamic-load/run.ps1 -Profile smoke -Workload mixed` |
| Performance | Profile acceptance | `./performance/dynamic-load/run.ps1 -Profile <profile> -Workload <workload>` |
| Full | Phase completion | Contract + Inspect + Smoke + `./mvnw.cmd clean verify -Pintegration` |
| Documentation | Documentation change | `./scripts/validate-readme.ps1` plus directed review against `.specs/features/dynamic-fake-load/` |

---

## Execution Plan

Phases and tasks execute sequentially. No phase starts before the previous phase passes its full gate.

### Phase 1: Workload Core

```text
T01 -> T02 -> T03
```

### Phase 2: Orchestration and Evidence

```text
T03 -> T04 -> T05 -> T06
```

### Phase 3: Operation and Baseline

```text
T06 -> T07 -> T08
```

---

## Task Breakdown

### T01: Implementar o gerador fake determinístico

**Status:** Complete

**What:** Criar o módulo de PRNG, eventos, clientes, chaves idempotentes e seleção hot/warm/cold, com seu contrato executável.
**Where:** `performance/dynamic-load/lib/fake-data.js` e contrato co-localizado
**Depends on:** None
**Reuses:** Limites de `CreateEventRequest`, `CreateReservationRequest` e `IdempotencyCommand`.
**Requirement:** LOAD-01, LOAD-08

**Tools:**

- MCP: NONE
- Skills: `tlc-spec-driven`, `java-spring-engineering`, `testing-pyramid`

**Done when:**

- [ ] A mesma seed e identificadores produzem hash idêntico em duas execuções.
- [ ] Dez mil seleções ficam em 60/30/10 com tolerância de 2 pontos percentuais.
- [ ] Nomes, e-mails, quantidades e chaves respeitam todos os limites da API.
- [ ] Toda chave mutável é única no contrato e tem no máximo 128 caracteres.
- [ ] O Contract gate passa sem rede.

**Tests:** contract k6 no mesmo task
**Gate:** Contract
**Commit:** `test(performance): add deterministic fake data generator`
**Result:** O contrato k6 aprovou 8/8 checks: sequência reproduzível, limites de evento/cliente, `example.com`, quantidade positiva, chaves únicas de no máximo 128 caracteres e distribuição 60/30/10 em 10.000 seleções.

### T02: Definir perfis e guardrails de carga

**What:** Implementar a configuração declarativa de smoke, stress, spike, capacity, load e soak, incluindo validação de loopback e do forecast.
**Where:** `performance/dynamic-load/lib/profile-config.js` e contrato co-localizado
**Depends on:** T01
**Reuses:** Thresholds de `performance/demo/*.js` e decisões da spec.
**Requirement:** LOAD-02, LOAD-03, LOAD-04, LOAD-05, LOAD-06, LOAD-08

**Tools:**

- MCP: NONE
- Skills: `tlc-spec-driven`, `testing-pyramid`

**Done when:**

- [ ] Cada perfil produz o executor, duração e thresholds definidos na spec.
- [ ] `load` falha sem taxa, mix e fonte de forecast.
- [ ] Perfis não smoke falham com menos de duas command-api.
- [ ] URLs não loopback, taxas não positivas e tetos fora do limite falham antes de HTTP.
- [ ] O Contract gate passa sem rede.

**Tests:** contract k6 no mesmo task
**Gate:** Contract
**Commit:** `test(performance): define load profiles and safety guards`

### T03: Implementar o engine de workloads k6

**What:** Criar um engine modular que prepara eventos, executa query-heavy, command-heavy e mixed e separa falha técnica de rejeição de negócio.
**Where:** `performance/dynamic-load/workload.js`
**Depends on:** T02
**Reuses:** Endpoints, checks e round-robin de `performance/demo/query.js`, `command.js` e `mixed.js`.
**Requirement:** LOAD-01, LOAD-02, LOAD-03, LOAD-04, LOAD-05, LOAD-06, LOAD-07

**Tools:**

- MCP: NONE
- Skills: `tlc-spec-driven`, `testing-pyramid`

**Done when:**

- [ ] Setup cria 12 eventos com capacidade derivada do perfil.
- [ ] O workload mixed realiza a proporção 70/20/5/5 e mantém IDs de reserva no VU que os criou.
- [ ] Mutações são distribuídas explicitamente entre duas command-api.
- [ ] Métricas distinguem rota, fase, target, falha técnica e rejeição de negócio.
- [ ] `k6 inspect` e o smoke lifecycle passam.

**Tests:** performance integration no mesmo task
**Gate:** Inspect + Smoke
**Commit:** `test(performance): add stateful dynamic workload engine`

### T04: Coletar telemetria local, CloudWatch e auditar invariantes

**What:** Criar os samplers locais, a auditoria PostgreSQL e o coletor CloudWatch somente leitura para correlacionar uma janela AWS autorizada externamente.
**Where:** `performance/dynamic-load/telemetry.ps1` e adaptador CloudWatch co-localizado
**Depends on:** T03
**Reuses:** Comandos de Valkey/PostgreSQL de `performance/demo/run.ps1` e constraints de `V1__create_flash_booking_schema.sql`.
**Requirement:** LOAD-06, LOAD-07, LOAD-08, LOAD-09

**Tools:**

- MCP: `MCP_DOCKER` quando disponível; Docker CLI somente se o MCP não expuser a amostragem necessária
- Skills: `tlc-spec-driven`, `design-postgres-tables`, `testing-pyramid`

**Done when:**

- [ ] CPU/RSS são amostrados a cada 10 segundos e PostgreSQL/Valkey a cada 5 segundos.
- [ ] As consultas PostgreSQL são curtas, somente leitura e não mantêm transação durante a carga.
- [ ] O audit verifica disponibilidade não negativa, quantidades positivas e balanço de inventário.
- [ ] Falha de um sampler marca evidência incompleta sem impedir teardown.
- [ ] O coletor CloudWatch aceita somente janela UTC e identidade de recursos autorizados, usa leitura de métricas e nunca chama a API de negócio ou cria recurso AWS.
- [ ] A correlação obtém API Gateway, ECS, RDS, Valkey e SQS quando as séries existem; cada lacuna é registrada como incompleta.
- [ ] O Contract e o Smoke gate passam e produzem séries e audit válidos.

**Tests:** integration e contract CloudWatch no mesmo task
**Gate:** Contract + Smoke
**Commit:** `test(performance): collect runtime and database telemetry`

### T05: Normalizar resultados e emitir verdict

**What:** Combinar summaries, telemetria local ou CloudWatch, audit e proveniência em JSON canônico e relatório Markdown com regras de verdict por perfil.
**Where:** `performance/dynamic-load/normalize.ps1`
**Depends on:** T04
**Reuses:** Estrutura de `performance/demo/baseline.json` e thresholds da spec.
**Requirement:** LOAD-03, LOAD-04, LOAD-05, LOAD-06, LOAD-07, LOAD-08, LOAD-09

**Tools:**

- MCP: NONE
- Skills: `tlc-spec-driven`, `testing-pyramid`

**Done when:**

- [ ] O contrato exige todos os metadados, métricas por rota/fase, telemetria, audit, verdict e limitações.
- [ ] Stress deriva o último degrau sustentável; spike mede recuperação; capacity e soak aplicam suas janelas.
- [ ] Resultado generator-bound, telemetria incompleta ou sold-out acidental não produz claim de capacidade.
- [ ] Evidência AWS marca o seu intervalo UTC, recursos e lacunas CloudWatch e não é apresentada como equivalente a p95/p99 do k6.
- [ ] Fixtures malformadas e rótulos de produção indevidos falham no Contract gate.
- [ ] O Contract gate passa para fixtures válidas de todos os perfis.

**Tests:** contract PowerShell no mesmo task
**Gate:** Contract
**Commit:** `test(performance): normalize load evidence and verdicts`

### T06: Orquestrar execução e publicação segura

**What:** Criar o runner que controla Compose, preflight, k6, sampler, normalização, evidência parcial, teardown e publicação canônica.
**Where:** `performance/dynamic-load/run.ps1` e regra de ignore coesa para resultados brutos
**Depends on:** T05
**Reuses:** `performance/demo/run.ps1` e perfil `concurrency` de `compose.yaml`.
**Requirement:** LOAD-02, LOAD-03, LOAD-04, LOAD-05, LOAD-06, LOAD-07, LOAD-08, LOAD-09

**Tools:**

- MCP: `MCP_DOCKER` preferido para inspeção; CLI para Compose/k6 quando necessário ao harness
- Skills: `tlc-spec-driven`, `testing-pyramid`

**Done when:**

- [ ] Smoke sempre precede qualquer perfil longo e bloqueia a sequência quando falha.
- [ ] Ao menos duas URLs command-api distintas são descobertas e registradas.
- [ ] Interrupção, threshold failure e sucesso sempre encerram Compose e preservam artefatos disponíveis.
- [ ] Publicação canônica falha em worktree suja; execução descartável continua permitida.
- [ ] Contract, Inspect, Smoke e regressão Maven passam.

**Tests:** integration e regressão no mesmo task
**Gate:** Full
**Commit:** `test(performance): orchestrate safe dynamic load runs`

### T07: Documentar operação e interpretação

**What:** Documentar pré-requisitos, perfis, dados fake, comandos, custos de tempo, guardrails, resultados e limites de evidência; adicionar a feature à navegação principal.
**Where:** `performance/dynamic-load/README.md` e navegação documental relacionada
**Depends on:** T06
**Reuses:** Estrutura de `performance/demo/README.md` e tabela de fonte de verdade do `README.md`.
**Requirement:** LOAD-02, LOAD-03, LOAD-04, LOAD-05, LOAD-06, LOAD-07, LOAD-08

**Tools:**

- MCP: NONE
- Skills: `tlc-spec-driven`

**Done when:**

- [ ] A tabela responde quais dos cinco tipos são obrigatórios, condicionais ou P2 e por quê.
- [ ] Cada comando lista duração, pré-requisitos, saídas e critério de aprovação.
- [ ] O texto proíbe inferir produção, AWS high-load, failover ou heap leak a partir do resultado local.
- [ ] O texto explica que CloudWatch é correlação somente leitura de uma execução AWS autorizada e não autoriza o runner a gerar tráfego remoto.
- [ ] O gate Documentation passa sem links quebrados ou divergência da spec.

**Tests:** documentation validation no mesmo task
**Gate:** Documentation
**Commit:** `docs(performance): document dynamic load workflow`

### T08: Executar a matriz curta e publicar o baseline local

**What:** Executar smoke, stress, spike e capacity nos três workloads aplicáveis, publicar a evidência canônica e decidir se soak está promovido.
**Where:** `performance/dynamic-load/baseline/`
**Depends on:** T07
**Reuses:** Runner, normalizador e política de publicação implementados nas tasks anteriores.
**Requirement:** LOAD-02, LOAD-03, LOAD-04, LOAD-05, LOAD-06, LOAD-07, LOAD-08

**Tools:**

- MCP: `MCP_DOCKER` preferido para inspeção; CLI exigida pelo runner quando o MCP não cobrir Compose/k6
- Skills: `tlc-spec-driven`, `testing-pyramid`, `design-postgres-tables`

**Done when:**

- [ ] Smoke, stress query-heavy/command-heavy/mixed, spike mixed e capacity dos workloads medidos possuem artefatos e verdict.
- [ ] O relatório identifica o primeiro gargalo observado ou `inconclusivo` e separa generator-bound.
- [ ] A auditoria local de inventário passa e a regressão Maven permanece verde.
- [ ] O baseline declara commit, ambiente e limitações e nunca usa o rótulo capacidade de produção.
- [ ] Soak recebe uma decisão explícita: executar se todos os gates curtos passaram; manter pendente com o bloqueio observado caso contrário.
- [ ] O Full gate passa antes da publicação.

**Tests:** performance acceptance e regressão
**Gate:** Performance + Full
**Commit:** `test(performance): publish dynamic local capacity baseline`

---

## Phase Execution Map

```text
Phase 1 -> Phase 2 -> Phase 3

Phase 1: T01 -> T02 -> T03
Phase 2: T03 -> T04 -> T05 -> T06
Phase 3: T06 -> T07 -> T08
```

Execution is strictly sequential. The plan has eight tasks and fits one TLC task-budgeted batch, so Execute remains inline unless the plan grows beyond this boundary.

---

## Task Granularity Check

| Task | Deliverable | Status |
| --- | --- | --- |
| T01 | One fake-data module plus its contract | OK |
| T02 | One profile-policy module plus its contract | OK |
| T03 | One workload engine | OK |
| T04 | One observability and audit boundary | OK |
| T05 | One normalizer | OK |
| T06 | One orchestration boundary | OK |
| T07 | One operator guide and navigation update | OK |
| T08 | One canonical evidence package | OK |

## Diagram-Definition Cross-Check

| Task | Depends On | Diagram Shows | Status |
| --- | --- | --- | --- |
| T01 | None | Start | Match |
| T02 | T01 | T01 -> T02 | Match |
| T03 | T02 | T02 -> T03 | Match |
| T04 | T03 | T03 -> T04 | Match |
| T05 | T04 | T04 -> T05 | Match |
| T06 | T05 | T05 -> T06 | Match |
| T07 | T06 | T06 -> T07 | Match |
| T08 | T07 | T07 -> T08 | Match |

## Test Co-location Validation

| Task | Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T01 | Fake-data generator | contract | contract k6 | OK |
| T02 | Profile/config policy | contract | contract k6 | OK |
| T03 | k6 workload | performance integration | inspect + smoke | OK |
| T04 | Telemetry, audit and CloudWatch adapter | integration + contract | smoke integration + CloudWatch contract | OK |
| T05 | Evidence normalizer | contract | PowerShell contract | OK |
| T06 | Runner/orchestrator | integration + regression | Full | OK |
| T07 | Documentation | review | documentation validation | OK |
| T08 | Evidence package | performance acceptance | Performance + Full | OK |

---

## Tools for Execute

The plan defaults to the repository tools already in use: filesystem edits, k6, PowerShell, Maven and Docker. Per `AGENTS.md`, prefer `MCP_DOCKER` for Docker inspection when it exposes the needed operation; use Docker CLI for Compose lifecycle and measurements the MCP does not expose. No external plugin is required.
