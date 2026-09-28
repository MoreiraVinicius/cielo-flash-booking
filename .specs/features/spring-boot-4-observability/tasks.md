# Spring Boot 4 e observabilidade — tarefas

## Execution Protocol

Executar com `tlc-spec-driven`: gate verde e um commit atômico por tarefa; depois, Verifier independente. Nenhuma ação remota.

**Design:** `.specs/features/spring-boot-4-observability/design.md`
**Status:** In Progress

## Test Coverage Matrix

> Proveniência: `AGENTS.md`, `.github/workflows/ci.yml`, `pom.xml`, `README.md`, `src/test/java` e `scripts/compose-smoke.ps1`. Amostrados testes de controller, idempotência, inventário, SQS e configuração.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Contrato HTTP e JSON | integration | Cinco operações, payloads, headers, erros e idempotência preservados | `src/test/java/**/*IT.java` | Maven `-Pintegration verify` |
| Domínio e persistência | unit + integration | Sem regressão nas invariantes e edge cases existentes | `src/test/java/**/*Test.java`, `*IT.java` | Maven `-Pintegration verify` |
| Configuração de observabilidade | unit + integration | Exportadores off por padrão; on local; falha do collector não muda resposta | `src/test/java/**/*Test.java`, `*IT.java` | Maven `-Pintegration verify` |
| Compose/collector | smoke | Três modos saudáveis e recepção OTLP demonstrável | `scripts/compose-smoke.ps1` + teste específico | Docker Compose + script |
| Specs, docs, SVG | structural + visual | Referências coerentes, links resolvidos, SVG válido | `scripts/validate-readme.ps1`, revisão visual | PowerShell |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Alteração de configuração/teste isolado | `mvnw.cmd --batch-mode test` |
| Full | Migração de dependências/JSON/observabilidade | `mvnw.cmd --batch-mode -Pintegration verify` |
| Build | Fechamento de fase e empacotamento | `mvnw.cmd --batch-mode -Pintegration clean verify`; `scripts/validate-readme.ps1` quando docs mudarem |

## Execution Plan

### Phase 1: Plataforma

```text
T1 → T2 → T3
```

### Phase 2: Observabilidade

```text
T4 → T5
```

### Phase 3: Documentação e entrega

```text
T6 → T7
```

## Task Breakdown

### T1: Atualizar a linha 3.5

**What:** Mover o parent de 3.5.0 para 3.5.16 como degrau exigido pelo guia oficial.
**Where:** `pom.xml`
**Depends on:** None
**Reuses:** POM e testes existentes.
**Requirement:** BOOT4-01
**Done when:** parent 3.5.16; 54 testes rápidos e 68 integrações não diminuem; gate Full verde.
**Tests:** integration existentes
**Gate:** Full
**Commit:** `build(spring): update baseline to boot 3.5.16`
**Status:** Complete — 122 tests, 0 failures/errors/skips (54 rápidos + 68 integração).

### T2: Migrar o runtime e testes para Boot 4

**What:** Atualizar starters e imports de auto-configuração/testes, com ponte Jackson 2 temporária para manter a etapa verificável.
**Where:** `pom.xml` e imports de classes Spring Boot em `src/test/java`
**Depends on:** T1
**Reuses:** Testes de contrato e Testcontainers existentes.
**Requirement:** BOOT4-01
**Done when:** Boot 4.0.8 compila; Flyway inicia; contratos e invariantes permanecem; gate Full verde.
**Tests:** integration existentes, corrigidos sem enfraquecer
**Gate:** Full
**Commit:** `build(spring): migrate runtime to boot 4`
**Status:** Complete — compilação passou; suíte rápida 54/54; suíte de integração completa 68/68 sem falhas; ReservationControllerIT isolado 8/8 em 59,5 s.

### T3: Remover a ponte Jackson 2

**What:** Migrar os usos de Jackson para a API Jackson 3 e remover o módulo de compatibilidade.
**Where:** imports e chamadas Jackson em `src/main/java`, `src/test/java` e `pom.xml`
**Depends on:** T2
**Reuses:** Testes existentes de JSON, cache, idempotência, SQS e HTTP.
**Requirement:** BOOT4-01
**Done when:** não há dependência `spring-boot-jackson2` nem imports `com.fasterxml.jackson.databind` em produção; JSON público igual; gate Full verde.
**Tests:** integration existentes, ajustados para Jackson 3
**Gate:** Full
**Commit:** `refactor(json): migrate application to jackson 3`
**Status:** Complete — `mvn test` 54/54; `mvn -Pintegration verify` 122/122 (54 unit + 68 integração), sem falhas.

### T4: Configurar OpenTelemetry opt-in

**What:** Adicionar starter, configuração off por padrão, profile local e testes da exportação/falha.
**Where:** `pom.xml`, `src/main/resources/application*.yml` e testes de configuração/HTTP.
**Depends on:** T3
**Reuses:** Actuator e X-Correlation-ID existentes.
**Requirement:** BOOT4-02
**Done when:** métricas/traces OTLP desligados por padrão; profile local exporta ambos; falha do receptor não altera HTTP; gate Full verde.
**Tests:** unit + integration co-localizados
**Gate:** Full
**Commit:** `feat(observability): add opt-in http telemetry`
**Status:** Complete — 56 testes unitários passaram; `ObservabilityExportResilienceIT` passou com métricas/traces habilitados e collector indisponível, mantendo criação HTTP da reserva.

### T5: Demonstrar recepção OTLP local

**What:** Adicionar collector local opt-in e procedimento de smoke com prova de métricas/traces.
**Where:** `compose.yaml`, configuração collector e script de smoke.
**Depends on:** T4
**Reuses:** Compose e smoke existentes.
**Requirement:** BOOT4-02
**Done when:** collector recebe uma chamada HTTP e produz evidência de métricas/traces; stack sem profile permanece igual; gate Build verde.
**Tests:** smoke
**Gate:** Build
**Commit:** `test(observability): prove local otlp export`

### T6: Registrar o plano AWS não aplicado

**What:** Registrar destino, IAM, custo, sampling, rollout e rollback AWS sem alterar Terraform.
**Where:** `.specs/features/spring-boot-4-observability/aws-plan.md`
**Depends on:** T5
**Reuses:** AD-023/024 e diagramas existentes.
**Requirement:** BOOT4-04
**Done when:** plano AWS contém IAM, custo, sampling, rollout/rollback e diferença entre coletor OTLP e Application Signals; revisão estrutural verde.
**Tests:** structural
**Gate:** Build
**Commit:** `docs(observability): plan aws telemetry integration`

### T7: Alinhar fontes de verdade e diagramas

**What:** Atualizar decisão, README, referências runtime e SVG sem alterar a evidência histórica da demo.
**Where:** `.specs/STATE.md` e documentação do runtime atual
**Depends on:** T6
**Reuses:** AD-023/024 e diagramas existentes.
**Requirement:** BOOT4-03
**Done when:** Boot 3 aparece só em contexto histórico; SVG válido; validação documental e Build verdes.
**Tests:** structural + visual
**Gate:** Build
**Commit:** `docs(architecture): align boot 4 references`

## Phase Execution Map

```text
Phase 1: T1 → T2 → T3
Phase 2: T4 → T5
Phase 3: T6 → T7
Handoff 1: T3 → T4
Handoff 2: T5 → T6
```

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1 | Parent Maven | OK |
| T2 | Plataforma Boot 4 e compatibilidade de testes | OK, mudança coesa |
| T3 | API JSON | OK, migração coesa |
| T4 | Exportação configurável | OK, config e testes co-localizados |
| T5 | Receptor local e smoke | OK, caminho demonstrável |
| T6 | Plano AWS | OK |
| T7 | Fonte de verdade e visual | OK, documentação coesa |

## Diagram-Definition Cross-Check

| Task | Depends On | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | None | OK |
| T2 | T1 | T1 → T2 | OK |
| T3 | T2 | T2 → T3 | OK |
| T4 | T3 | T3 → T4 | OK |
| T5 | T4 | T4 → T5 | OK |
| T6 | T5 | T5 → T6 | OK |
| T7 | T6 | T6 → T7 | OK |

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Build | integration existente | integration | OK |
| T2 | Runtime/contratos | integration | integration | OK |
| T3 | JSON/contratos | integration | integration | OK |
| T4 | Config/HTTP | unit + integration | unit + integration | OK |
| T5 | Compose | smoke | smoke | OK |
| T6 | Plano AWS | structural | structural | OK |
| T7 | Docs/SVG | structural + visual | structural + visual | OK |
