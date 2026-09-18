# Audit Remediation Tasks

## Execution Protocol

Executar em ordem. Cada tarefa termina com o gate indicado, atualiza rastreabilidade e gera commit atomico.

**Design:** `.specs/features/audit-remediation/design.md`
**Status:** Locally validated; remote CI evidence pending
**Task count:** 6

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| PostgreSQL | integration | Relogio, identidade, leases e retencao reais | `src/test/**/*IT.java` | `mvn -Pintegration verify` |
| HTTP/cache | unit + MVC | Limites, replay e falhas observaveis | `src/test/**/*.java` | `mvn test` |
| Domain/build | unit + static | Contratos preservados e JPA ausente | `src/test/**/*.java`, `pom.xml` | `mvn verify` |
| Terraform | terraform test | Health, escala, metricas e alarm actions | `infra/**/*.tftest.hcl` | `terraform test` por modulo |
| Documentation/CI | static | Comandos e alegacoes reproduziveis | `README.md`, `docs/`, `.github/` | `scripts/validate-readme.ps1` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Unidade isolada | `mvn test` |
| Full | PostgreSQL ou transacao | `mvn -Pintegration verify` |
| Build | Fechamento Java | `mvn verify` e `terraform fmt -check -recursive` |
| Terraform | Infraestrutura | `terraform test` por modulo e `terraform validate` nos roots |

## Execution Plan

```text
Phase 1: Application correctness
T01 -> T02

Phase 2: Operational simplicity
T01 -> T03 -> T04

Phase 3: Cloud and delivery
T03 -> T05
T02 -> T06
T04 -> T06
T05 -> T06
```

## Task Breakdown

## Phase 1: Application Correctness

### T01: Tornar tempo e identidade persistidos autoritativos

**Status:** Complete; Full gate passed.
**What:** Obter o instante de criacao no PostgreSQL, usar timestamp estavel na busca de expirados, preservar o cliente ja persistido e limitar Idempotency-Key.
**Where:** servico/ports/adaptador JDBC de reserva, migration V2, testes de persistencia
**Depends on:** none
**Requirement:** REM-01
**Done when:** Os quatro criterios de REM-01 passam contra PostgreSQL real e a API rejeita chaves acima de 128 caracteres.
**Tests:** deadline por relogio do banco, selecao temporal, reuso imutavel do cliente e limites 128/129
**Gate:** Full
**Commit:** `fix(persistence): use database time and bounded identities`

### T02: Reforcar fronteira HTTP e comportamento do cache

**Status:** Complete; Build gate passed.
**What:** Contar corpos transmitidos, reidratar correlation ID de replay, registrar throwable e separar cache miss de outage.
**Where:** filtro HTTP, resposta idempotente, handler, cache e testes focados
**Depends on:** T01
**Requirement:** REM-02
**Done when:** Os quatro criterios de REM-02 possuem testes com resultados exatos.
**Tests:** corpo sem Content-Length acima do limite, replay, resposta 500 e miss/falha do circuito
**Gate:** Build
**Commit:** `fix(api): enforce request limits and replay context`

## Phase 2: Operational Simplicity

### T03: Limitar trabalho agendado e notificacoes

**Status:** Complete; Full gate passed.
**What:** Configurar scheduler concorrente, reconciliacao com vazao suficiente, claim com lease fora da chamada SES e limpeza terminal limitada.
**Where:** configuracao, reconciliador, notification store/service, cleaner, migration e testes
**Depends on:** T01
**Requirement:** REM-03
**Done when:** Nenhuma chamada de provedor ocorre em transacao, jobs independentes nao compartilham thread unica e limpeza preserva registros ativos.
**Tests:** binding do scheduler, defaults do reconciliador, estado transacional da chamada e SQL de lease/retencao
**Gate:** Full
**Commit:** `fix(worker): bound scheduled and notification work`

### T04: Remover caminhos mortos de dominio e JPA

**Status:** Complete; Build gate passed.
**What:** Usar JDBC na persistencia de eventos, retirar JPA e remover transicoes em memoria sem consumidor de runtime.
**Where:** persistencia de evento, dominio de reserva, pom, Dockerfile e testes afetados
**Depends on:** T03
**Requirement:** REM-04
**Done when:** O build nao resolve Hibernate/JPA, os contratos de evento/reserva passam e o Dockerfile executa testes.
**Tests:** suites existentes de evento e reserva e inspecao da arvore de dependencias
**Gate:** Build
**Commit:** `refactor(core): remove unused jpa and domain paths`

## Phase 3: Cloud and Delivery

### T05: Reparar deploy e alarmes Terraform

**Status:** Complete; Terraform gate passed.
**What:** Declarar backend, ordenar bootstrap da imagem, adicionar health checks/autoscaling e alarmes SNS para sinais reais.
**Where:** roots/modulos/testes Terraform e runbook
**Depends on:** T03
**Requirement:** REM-05
**Done when:** Testes Terraform provam backend, health, escala, alarm actions e ausencia da metrica Throttle.
**Tests:** testes de modulos, fmt e validate local sem apply
**Gate:** Terraform
**Commit:** `fix(terraform): make deployment observable and reproducible`

### T06: Automatizar gates e alinhar evidencia

**Status:** Complete locally; remote CI execution pending.
**What:** Criar GitHub Actions, corrigir README/runbook e fechar rastreabilidade e validacao.
**Where:** `.github/workflows`, README, docs e `.specs`
**Depends on:** T02, T04, T05
**Requirement:** REM-06
**Done when:** Workflow executa gates Java/Terraform e documentos nao tratam 503 como sucesso.
**Tests:** parsing do workflow, validador do README e gates completos
**Gate:** Build, Terraform
**Commit:** `ci: verify java and terraform delivery gates`
