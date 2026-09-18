# Flash Booking High Load Tasks

## Execution Protocol

Estas tarefas descrevem uma evolução futura e permanecem `Draft`. Execute-as com a skill `tlc-spec-driven` somente quando a implementação high-load for autorizada. É proibido tratar o desenho atual como código, infraestrutura, capacidade ou failover entregues. Uma tarefa futura termina após testes e gate e gera um commit atômico.

**Design:** `.specs/features/flash-booking-high-load/design.md`
**Status:** Draft
**Task count:** 21

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Cache compartilhado | integration da demo + static | Contrato herdado de hit, miss, TTL, invalidação e falha; alta carga só valida infraestrutura | `src/test/java/**/*Cache*Test.java`, `infra/**/*.tf` | `./mvnw verify -Pintegration` e `terraform test` |
| Reserva concorrente | integration da demo + plano de performance | Oversell zero é herdado da demo; SLO sob rajada é hipótese a validar depois | `src/test/java/**/*ConcurrencyIT.java`, `performance/high-load/` | `./mvnw verify -Pintegration` e review gate |
| Publisher da outbox | integration PostgreSQL + SQS | Dois publishers dividem claims; lease abandonado é recuperado; confirmação exige token; I/O SQS não mantém transação aberta | `src/test/java/**/*Outbox*IT.java` | `./mvnw verify -Pintegration` |
| Autoscaling/infra | static + terraform test | Multi-AZ, limites, políticas e rollback | `infra/**/*.tf` | `terraform fmt -check -recursive && terraform validate` |
| Resiliência | plano de failure injection | Falha de task, Valkey, writer e AZ, sem execução remota atual | `performance/high-load/mixed/` | review gate |
| Documentação | review | Gatilho, custo e rollback de cada evolução | `docs/` | review gate |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Unidade | `./mvnw test` |
| Full | Integração | `./mvnw verify -Pintegration` |
| Resilience | Falhas controladas | `./mvnw verify -Presilience` |
| Infra | Terraform | `terraform fmt -check -recursive && terraform validate` |
| Build | Fechamento | `./mvnw clean verify -Pintegration` |

## Execution Plan

```text
Phase 1
T01 -> T02
T02 -> T03
T02 -> T04
T03 -> T04

Phase 2
T05 -> T06
T06 -> T07
T06 -> T09
T03 -> T08
T08 -> T09

Phase 3
T09 -> T10
T10 -> T11
T09 -> T12
T12 -> T13
T07 -> T14
T10 -> T14
T13 -> T14
T07 -> T15
T08 -> T15
T12 -> T15
T10 -> T16
T11 -> T16
T12 -> T16
T13 -> T16
T14 -> T16
T15 -> T16

Phase 4
T16 -> T17
T16 -> T18
T17 -> T19
T18 -> T19
T19 -> T20
T20 -> T21
```

## Task Breakdown

## Phase 1: Capacity Baseline

### T01: Fixar o envelope da demo

**What:** Consolidar TPS sustentável, p95, p99, erros, conexões, hit rate, backlog e lock waits da demo por serviço.
**Where:** `performance/baselines/`
**Depends on:** Demo validation PASS
**Requirement:** SCALE-04
**Done when:** Há envelopes separados para query-api, command-api, worker e carga mista com oversell zero.
**Tests:** performance
**Gate:** Build
**Commit:** `test(performance): record demo capacity envelope`

### T02: Definir SLOs e gatilhos

**What:** Registrar SLOs, limiar de 60% do envelope, janelas e donos das decisões.
**Where:** `docs/capacity-policy.md`
**Depends on:** T01
**Requirement:** SCALE-04
**Done when:** Cada sinal mapeia para uma evolução e rollback específicos.
**Tests:** none, review gate only
**Gate:** Build
**Commit:** `docs: define capacity promotion policy`

### T03: Parametrizar modos de escala

**What:** Criar variáveis Terraform independentes para cache, HA, mínimos, máximos e scheduled scaling.
**Where:** `infra/environments/high-load/`
**Depends on:** T02
**Requirement:** SCALE-01, SCALE-02, SCALE-03
**Done when:** Cada capacidade pode ser habilitada sem alterar módulos não relacionados.
**Tests:** static e terraform test, incluídos
**Gate:** Infra
**Commit:** `infra: add independent scaling controls`

### T04: Separar métricas de consultas e reservas

**What:** Criar métricas e dashboards separados para query-api, command-api, worker, rota, resultado e dependência.
**Where:** `infra/modules/observability/`
**Depends on:** T02, T03
**Requirement:** SCALE-04
**Done when:** Consultas, comandos, expiração e notificação possuem TPS, p95, p99, erros, conexões, hit rate, backlog e saturação separados.
**Tests:** terraform test, incluído
**Gate:** Infra
**Commit:** `infra: split query and reservation telemetry`

## Phase 2: Read Scaling

### T05: Documentar contrato único de cache

**What:** Validar que o contrato cache-aside de `GET /events/{id}` está integralmente na demo e registrar as configurações que Terraform fornece ao mesmo binário.
**Where:** `docs/architecture/cache-runtime-contract.md`
**Depends on:** T01
**Requirement:** SCALE-01
**Done when:** Documento lista chave de evento, TTL, invalidações, timeout, circuito, fallback e variáveis de runtime; registra que consulta de reserva não usa cache.
**Tests:** review gate only
**Gate:** Build
**Commit:** `docs: record shared cache runtime contract`

### T06: Parametrizar runtime do cache

**What:** Configurar Terraform para injetar modo `query-api`/`command-api`/`worker`, endpoints read-only/read-write, TLS, TTL, timeout, circuito e limite de fallback no mesmo container Java.
**Where:** `infra/modules/compute/`
**Depends on:** T05
**Requirement:** SCALE-01, SCALE-05
**Done when:** Alterar modo, endpoint ou capacidade gera nova configuração ECS sem mudar imagem, controllers, schema, eventos ou variáveis de negócio.
**Tests:** terraform test, incluído
**Gate:** Infra
**Commit:** `infra: inject shared cache runtime configuration`

### T07: Provisionar Valkey Multi-AZ

**What:** Criar replication group Valkey privado, criptografado, com primary, réplica, failover e parâmetros de nó, shards e réplicas.
**Where:** `infra/modules/cache/`
**Depends on:** T03, T06
**Requirement:** SCALE-01
**Done when:** Terraform valida segurança, Multi-AZ e topologia parametrizada; a topologia não requer mudanças no código Java.
**Tests:** static e terraform test, incluídos
**Gate:** Infra
**Commit:** `infra: add multi-az valkey cache`

### T08: Provisionar Aurora Serverless

**What:** Criar Aurora PostgreSQL Serverless com writer, ao menos um reader, ACUs parametrizados, backup e endpoints compatíveis com o mesmo JDBC/Flyway.
**Where:** `infra/modules/aurora/`
**Depends on:** T03
**Requirement:** SCALE-02, SCALE-03
**Done when:** Terraform valida Multi-AZ, ACUs, backup e saída de endpoint; não cria migração ou código de banco exclusivo.
**Tests:** static e terraform test, incluídos
**Gate:** Infra
**Commit:** `infra: add aurora serverless topology`

### T09: Adicionar RDS Proxy

**What:** Provisionar endpoints RDS Proxy read-write e read-only e implementar o adaptador que roteia disponibilidade para leitura eventual e reserva para leitura autoritativa.
**Where:** `infra/modules/database-proxy/`, `src/main/java/com/cielo/flashbooking/adapter/out/persistence/`
**Depends on:** T06, T08
**Requirement:** SCALE-02
**Done when:** Disponibilidade pode usar read-only; consulta de reserva, comandos e worker usam read-write; limites são separados por serviço e nenhuma capacidade é declarada sem teste remoto.
**Tests:** integration contra dois datasources + terraform test
**Gate:** Full + Infra
**Commit:** `feat(persistence): route high-load query datasources`

## Phase 3: Purchase and Availability Scaling

### T10: Configurar autoscaling independente de consultas e comandos

**What:** Criar políticas distintas: query-api por requisições/p95/CPU/hit rate e command-api por requisições/p95/CPU/conexões, com máximos parametrizados.
**Where:** `infra/modules/compute/api-scaling.tf`
**Depends on:** T01, T03, T09
**Requirement:** SCALE-01, SCALE-02, SCALE-05
**Done when:** Terraform expõe mínimos, máximos e metas independentes sem mudar Java; escalar uma API não altera desired count da outra; o máximo de comandos respeita o envelope do writer.
**Tests:** terraform test, incluído
**Gate:** Infra
**Commit:** `infra: autoscale api service`

### T11: Configurar pré-escala de flash sale

**What:** Criar scheduled scaling do command-api antes e depois da janela conhecida de abertura da venda, sem usar a agenda de infraestrutura para autorizar reservas.
**Where:** `infra/modules/compute/scheduled-scaling.tf`
**Depends on:** T10
**Requirement:** SCALE-02
**Done when:** Capacidade mínima somente do command-api sobe antes da venda e retorna após cooldown configurado, sem alteração de imagem ou regra de negócio; o teste/documentação confirma que `startsAt` e `endsAt` continuam verificados pelo decremento condicional no PostgreSQL.
**Tests:** terraform test, incluído
**Gate:** Infra
**Commit:** `infra: prescale scheduled flash sales`

### T12: Tornar o publisher da outbox concorrente

**What:** Evoluir o módulo compartilhado de outbox para adquirir lotes limitados por claim/lease PostgreSQL antes de publicar no SQS.
**Where:** `src/main/java/com/cielo/flashbooking/application/outbox/`, `src/main/java/com/cielo/flashbooking/adapter/out/persistence/outbox/`, `src/main/resources/db/migration/`, `src/test/java/com/cielo/flashbooking/adapter/out/messaging/publisher/`
**Depends on:** T09
**Requirement:** SCALE-06
**Done when:** A aquisição usa uma transação curta com `FOR UPDATE SKIP LOCKED`, token, lease pelo relógio PostgreSQL e incremento de tentativa; dois publishers não obtêm o mesmo lease vigente; a chamada SQS ocorre fora da transação; confirmação exige o token; processo interrompido devolve elegibilidade após o lease; resposta ambígua continua coberta por consumidores idempotentes sem alegação de exactly-once.
**Tests:** integração PostgreSQL + SQS com dois publishers sincronizados, expiração de lease, confirmação tardia rejeitada e falha ambígua
**Gate:** Full
**Commit:** `feat(outbox): claim events before concurrent publication`

### T13: Escalar workers por backlog

**What:** Configurar scaling por backlog por task e idade da mensagem mais antiga, com métricas separadas para expiração e notificação.
**Where:** `infra/modules/compute/worker-scaling.tf`
**Depends on:** T12
**Requirement:** SCALE-02, SCALE-06
**Done when:** Backlog aumenta workers somente depois do claim/lease estar validado; DLQ, idade excessiva de cada fila e claims vencidos disparam sinais separados; a configuração não exige nova alteração Java e não permite que notificação bloqueie expiração.
**Tests:** terraform test, incluído
**Gate:** Infra
**Commit:** `infra: autoscale expiration workers`

### T14: Tornar rede e edge altamente disponíveis

**What:** Adicionar NAT por AZ, mínimo de duas tasks por serviço, target groups separados e controles de admissão no API Gateway/WAF.
**Where:** `infra/environments/high-load/network-edge.tf`
**Depends on:** T07, T10, T13
**Requirement:** SCALE-02, SCALE-03
**Done when:** Terraform mantém API Gateway REST como único ponto público, roteia GET para query-api e POST/DELETE para command-api e declara distribuição Multi-AZ; o runbook registra que a comprovação remota é pendente.
**Tests:** terraform test e review do plano de failure injection, incluídos
**Gate:** Infra
**Commit:** `infra: harden high-load availability`

### T15: Documentar limites e variáveis de capacidade

**What:** Registrar variáveis Terraform para ACUs Aurora, topologia Valkey, mínimos/máximos por serviço, metas de autoscaling, agenda, throttling/WAF e teto de custo, vinculando cada valor ao baseline futuro.
**Where:** `docs/architecture/high-load-capacity-parameters.md`
**Depends on:** T07, T08, T12
**Requirement:** SCALE-01, SCALE-02, SCALE-03, SCALE-04, SCALE-05, SCALE-06
**Done when:** Cada ajuste tem valor inicial explicitamente provisório, limite, sinal de revisão e rollback; capacidade Terraform não altera regras de negócio ou contratos HTTP; lote e duração do lease da outbox são configuração operacional do mesmo binário e schema compartilhados.
**Tests:** review gate only
**Gate:** Build
**Commit:** `docs: define high-load capacity parameters`

### T16: Validar infraestrutura de alta carga sem apply

**What:** Criar testes Terraform com provider AWS mockado e registrar os comandos de `init -backend=false`, `fmt`, `validate` e `terraform test` que não criam recursos.
**Where:** `infra/environments/high-load/tests/`, `docs/architecture/high-load-static-validation.md`
**Depends on:** T04, T07, T08, T09, T10, T11, T12, T13, T14, T15
**Requirement:** SCALE-01, SCALE-02, SCALE-03, SCALE-06
**Done when:** Os testes verificam topologia, parâmetros e wiring de endpoint sem credenciais AWS nem `apply`; a evidência declara expressamente que não mede disponibilidade, desempenho ou failover remoto.
**Tests:** terraform test, incluído
**Gate:** Infra
**Commit:** `test(infra): validate high-load topology without apply`

## Phase 4: Validation and Handoff

### T17: Validar pico de leitura

**What:** Documentar carga futura de GET com cache frio, quente, stampede e Valkey indisponível.
**Where:** `performance/high-load/read/`
**Depends on:** T07, T14
**Requirement:** SCALE-01
**Done when:** O cenário, os SLOs e as métricas de Valkey/PostgreSQL estão documentados; o relatório declara que não houve execução remota nesta entrega.
**Tests:** review gate only
**Gate:** Build
**Commit:** `docs: define read peak validation evidence`

### T18: Validar pico de reservas

**What:** Documentar rajadas futuras de reservas com pré-escala ligada/desligada, múltiplas command-api e capacidade excedida.
**Where:** `performance/high-load/reservation/`
**Depends on:** T11, T14
**Requirement:** SCALE-02
**Done when:** O cenário mede oversell, disponibilidade acima da capacidade, lock waits, conexões e independência entre serviços; o relatório declara que não houve execução remota nesta entrega.
**Tests:** review gate only
**Gate:** Build
**Commit:** `docs: define reservation peak validation evidence`

### T19: Validar volatilidade e falhas

**What:** Documentar alternância futura de picos de consulta/reserva e injeção de falhas de task, Valkey, conexão, SES, filas e zona de disponibilidade.
**Where:** `performance/high-load/mixed/`
**Depends on:** T17, T18
**Requirement:** SCALE-01, SCALE-02, SCALE-03, SCALE-06
**Done when:** O plano de falhas de task, Valkey, conexão, publisher após claim, SES, fila e AZ está documentado; nenhuma recuperação é declarada como comprovada remotamente.
**Tests:** review gate only
**Gate:** Build
**Commit:** `docs: define high-load resilience evidence`

### T20: Documentar runbook e forks extremos

**What:** Registrar operação, custos, rollback, correlação futura com abertura da venda/proximidade do evento e critérios de nova ADR para DynamoDB, SQS FIFO ou EKS.
**Where:** `docs/ARCHITECTURE.md`
**Depends on:** T19
**Requirement:** SCALE-04
**Done when:** Cada evolução tem sinal, ação, impacto, rollback e responsável.
**Tests:** none, review gate only
**Gate:** Build
**Commit:** `docs: document high-load operations and evolution`

### T21: Verificar alta carga contra a especificação e o case

**What:** Executar gates, revisar ACs e realizar discrimination sensor.
**Where:** `.specs/features/flash-booking-high-load/validation.md`
**Depends on:** T20
**Requirement:** SCALE-01, SCALE-02, SCALE-03, SCALE-04, SCALE-05, SCALE-06
**Done when:** Validation registra PASS documental com evidência `file:line`, comprova que imagem, domínio, contratos e schema operacional permanecem compartilhados, verifica claim/lease antes do scale-out do publisher, confronta todos os requisitos de `Case BackEnd 1.md` e lista limites ainda não medidos remotamente.
**Tests:** full da demo, infra estática e review dos planos de performance/resiliência
**Gate:** Build + Infra
**Commit:** `test: validate high-load architecture`

## Dependency Cross-Check

| Phase | Tasks | Dependency status |
| --- | --- | --- |
| Baseline | T01-T04 | Baseline precede política, parâmetros e métricas. Match. |
| Read | T05-T09 | Contrato precede runtime; runtime precede Valkey; Aurora e configuração precedem proxy e adaptador de roteamento. Match. |
| Purchase/HA | T10-T16 | Proxy precede autoscaling; claim/lease precede scale-out do worker; Valkey, API e worker precedem HA; limites documentados precedem validação estática. Match. |
| Validation | T17-T21 | Validação estática precede planos de carga; planos precedem runbook e verificação documental. Match. |

## Test Co-location Validation

| Task group | Matrix requires | Plan | Status |
| --- | --- | --- | --- |
| T01-T04 | performance/static | Tests and review in same task | OK |
| T05-T09 | contrato/infra | Review e terraform test na mesma task; integração é herdada da demo | OK |
| T10-T16 | outbox/integration + infra/static | T12 inclui concorrência PostgreSQL/SQS; demais tasks incluem testes Terraform e review no mesmo trabalho | OK |
| T17-T19 | planos de performance/resiliência | Cenário documentado é o deliverable; sem execução remota | OK |
| T20 | review | No production behavior created | OK |
| T21 | documentação, integração e infra estática | Verificador registra claim/lease e limites pendentes de teste remoto | OK |
