# Flash Booking High Load Tasks

## Execution Protocol

Execute estas tarefas com a skill `tlc-spec-driven` somente após a validação PASS da demo. Nesta entrega, executar somente documentação, validação estática e `terraform test` com providers mockados; é proibido aplicar o ambiente high-load ou alegar evidência de carga/failover remoto. Uma tarefa termina após testes e gate. Cada tarefa gera um commit atômico.

**Design:** `.specs/features/flash-booking-high-load/design.md`
**Status:** Draft
**Task count:** 20

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Cache compartilhado | integration da demo + static | Contrato herdado de hit, miss, TTL, invalidação e falha; alta carga só valida infraestrutura | `src/test/java/**/*Cache*Test.java`, `infra/**/*.tf` | `./mvnw verify -Pintegration` e `terraform test` |
| Reserva concorrente | integration da demo + plano de performance | Oversell zero é herdado da demo; SLO sob rajada é hipótese a validar depois | `src/test/java/**/*ConcurrencyIT.java`, `performance/high-load/` | `./mvnw verify -Pintegration` e review gate |
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
T03 -> T08
T08 -> T09

Phase 3
T09 -> T10
T10 -> T11
T09 -> T12
T07 -> T13
T10 -> T13
T12 -> T13
T07 -> T14
T08 -> T14
T10 -> T15
T11 -> T15
T12 -> T15
T13 -> T15
T14 -> T15

Phase 4
T15 -> T16
T15 -> T17
T16 -> T18
T17 -> T18
T18 -> T19
T19 -> T20
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

**What:** Validar que o contrato cache-aside dos dois GETs está integralmente na demo e registrar as configurações que Terraform fornece ao mesmo binário.
**Where:** `docs/architecture/cache-runtime-contract.md`
**Depends on:** T01
**Requirement:** SCALE-01
**Done when:** Documento lista chaves, TTL, invalidações, timeout, circuito, fallback e variáveis de runtime; não cria adaptador ou modelo de leitura exclusivo da alta carga.
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

**What:** Provisionar endpoints RDS Proxy read-write e read-only e rotear pools de consultas, comandos e worker sem alterar o cliente JDBC.
**Where:** `infra/modules/database-proxy/`
**Depends on:** T08
**Requirement:** SCALE-02
**Done when:** Disponibilidade pode usar read-only; consulta de reserva, comandos e worker usam read-write; limites são separados por serviço e nenhuma capacidade é declarada sem teste remoto.
**Tests:** terraform test, incluído
**Gate:** Infra
**Commit:** `infra: add database proxy connection control`

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

**What:** Criar scheduled scaling do command-api antes e depois da janela conhecida de abertura da venda.
**Where:** `infra/modules/compute/scheduled-scaling.tf`
**Depends on:** T10
**Requirement:** SCALE-02
**Done when:** Capacidade mínima somente do command-api sobe antes da venda e retorna após cooldown configurado, sem alteração de imagem ou regra de negócio.
**Tests:** terraform test, incluído
**Gate:** Infra
**Commit:** `infra: prescale scheduled flash sales`

### T12: Escalar workers por backlog

**What:** Configurar scaling por backlog por task e idade da mensagem mais antiga, com métricas separadas para expiração e notificação.
**Where:** `infra/modules/compute/worker-scaling.tf`
**Depends on:** T03, T09
**Requirement:** SCALE-02
**Done when:** Backlog aumenta workers e DLQ/idade excessiva de cada fila dispara alarme; a configuração não altera o worker Java nem permite que notificação bloqueie expiração.
**Tests:** terraform test, incluído
**Gate:** Infra
**Commit:** `infra: autoscale expiration workers`

### T13: Tornar rede e edge altamente disponíveis

**What:** Adicionar NAT por AZ, mínimo de duas tasks por serviço, target groups separados e controles de admissão no API Gateway/WAF.
**Where:** `infra/environments/high-load/network-edge.tf`
**Depends on:** T07, T10, T12
**Requirement:** SCALE-02, SCALE-03
**Done when:** Terraform mantém API Gateway REST como único ponto público, roteia GET para query-api e POST/DELETE para command-api e declara distribuição Multi-AZ; o runbook registra que a comprovação remota é pendente.
**Tests:** terraform test e review do plano de failure injection, incluídos
**Gate:** Infra
**Commit:** `infra: harden high-load availability`

### T14: Documentar limites e variáveis de capacidade

**What:** Registrar variáveis Terraform para ACUs Aurora, topologia Valkey, mínimos/máximos por serviço, metas de autoscaling, agenda, throttling/WAF e teto de custo, vinculando cada valor ao baseline futuro.
**Where:** `docs/architecture/high-load-capacity-parameters.md`
**Depends on:** T07, T08
**Requirement:** SCALE-01, SCALE-02, SCALE-03, SCALE-04, SCALE-05
**Done when:** Cada ajuste é classificado como alteração de infraestrutura, tem valor inicial explicitamente provisório, limite, sinal de revisão e rollback; o documento proíbe alterar Java, schema ou contratos HTTP para trocar capacidade.
**Tests:** review gate only
**Gate:** Build
**Commit:** `docs: define high-load capacity parameters`

### T15: Validar infraestrutura de alta carga sem apply

**What:** Criar testes Terraform com provider AWS mockado e registrar os comandos de `init -backend=false`, `fmt`, `validate` e `terraform test` que não criam recursos.
**Where:** `infra/environments/high-load/tests/`, `docs/architecture/high-load-static-validation.md`
**Depends on:** T04, T07, T08, T09, T10, T11, T12, T13, T14
**Requirement:** SCALE-01, SCALE-02, SCALE-03
**Done when:** Os testes verificam topologia, parâmetros e wiring de endpoint sem credenciais AWS nem `apply`; a evidência declara expressamente que não mede disponibilidade, desempenho ou failover remoto.
**Tests:** terraform test, incluído
**Gate:** Infra
**Commit:** `test(infra): validate high-load topology without apply`

## Phase 4: Validation and Handoff

### T16: Validar pico de leitura

**What:** Documentar carga futura de GET com cache frio, quente, stampede e Valkey indisponível.
**Where:** `performance/high-load/read/`
**Depends on:** T07, T13
**Requirement:** SCALE-01
**Done when:** O cenário, os SLOs e as métricas de Valkey/PostgreSQL estão documentados; o relatório declara que não houve execução remota nesta entrega.
**Tests:** review gate only
**Gate:** Build
**Commit:** `docs: define read peak validation evidence`

### T17: Validar pico de reservas

**What:** Documentar rajadas futuras de reservas com pré-escala ligada/desligada, múltiplas command-api e capacidade excedida.
**Where:** `performance/high-load/reservation/`
**Depends on:** T11, T13
**Requirement:** SCALE-02
**Done when:** O cenário mede oversell, disponibilidade acima da capacidade, lock waits, conexões e independência entre serviços; o relatório declara que não houve execução remota nesta entrega.
**Tests:** review gate only
**Gate:** Build
**Commit:** `docs: define reservation peak validation evidence`

### T18: Validar volatilidade e falhas

**What:** Documentar alternância futura de picos de consulta/reserva e injeção de falhas de task, Valkey, conexão, SES, filas e zona de disponibilidade.
**Where:** `performance/high-load/mixed/`
**Depends on:** T16, T17
**Requirement:** SCALE-01, SCALE-02, SCALE-03
**Done when:** O plano de falhas de task, Valkey, conexão e AZ está documentado; nenhuma recuperação é declarada como comprovada remotamente.
**Tests:** review gate only
**Gate:** Build
**Commit:** `docs: define high-load resilience evidence`

### T19: Documentar runbook e forks extremos

**What:** Registrar operação, custos, rollback, correlação futura com abertura da venda/proximidade do evento e critérios de nova ADR para DynamoDB, SQS FIFO ou EKS.
**Where:** `docs/ARCHITECTURE.md`
**Depends on:** T18
**Requirement:** SCALE-04
**Done when:** Cada evolução tem sinal, ação, impacto, rollback e responsável.
**Tests:** none, review gate only
**Gate:** Build
**Commit:** `docs: document high-load operations and evolution`

### T20: Verificar alta carga contra a especificação e o case

**What:** Executar gates, revisar ACs e realizar discrimination sensor.
**Where:** `.specs/features/flash-booking-high-load/validation.md`
**Depends on:** T19
**Requirement:** SCALE-01, SCALE-02, SCALE-03, SCALE-04, SCALE-05
**Done when:** Validation registra PASS documental com evidência `file:line`, comprova que imagem/schema/contratos são os mesmos da demo, confronta todos os requisitos de `Case BackEnd 1.md` e lista limites ainda não medidos remotamente.
**Tests:** full da demo, infra estática e review dos planos de performance/resiliência
**Gate:** Build + Infra
**Commit:** `test: validate high-load architecture`

## Dependency Cross-Check

| Phase | Tasks | Dependency status |
| --- | --- | --- |
| Baseline | T01-T04 | Baseline precede política, parâmetros e métricas. Match. |
| Read | T05-T09 | Contrato precede runtime; runtime precede Valkey; Aurora precede proxy. Não há código exclusivo de alta carga. Match. |
| Purchase/HA | T10-T15 | Proxy precede autoscaling; Valkey, API e worker precedem HA; limites documentados precedem validação estática. Match. |
| Validation | T16-T20 | Validação estática precede planos de carga; planos precedem runbook e verificação documental. Match. |

## Test Co-location Validation

| Task group | Matrix requires | Plan | Status |
| --- | --- | --- | --- |
| T01-T04 | performance/static | Tests and review in same task | OK |
| T05-T09 | contrato/infra | Review e terraform test na mesma task; integração é herdada da demo | OK |
| T10-T15 | infra/static | Tests estáticos/mockados e review na mesma task | OK |
| T16-T18 | planos de performance/resiliência | Cenário documentado é o deliverable; sem execução remota | OK |
| T19 | review | No production behavior created | OK |
| T20 | documentação e infra estática | Verificador registra limites pendentes de teste remoto | OK |
