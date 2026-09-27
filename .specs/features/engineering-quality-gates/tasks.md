# Gates de engenharia e revisão AWS - tarefas

## Execution Protocol

Executar T01–T03 em ordem. Cada tarefa fecha após seu gate, atualiza status e recebe commit Conventional Commit próprio. Os arquivos de `.specs/` são a fonte atual; os arquivos fora dela não repetem decisões.

**Spec:** `.specs/features/engineering-quality-gates/spec.md`
**Design:** `.specs/features/engineering-quality-gates/design.md`
**Checklist:** `.specs/features/engineering-quality-gates/checks.md`
**Status:** In progress
**Task count:** 3

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Arquitetura Java | ArchUnit + prova negativa | Domínio puro, cobertura de módulos, ciclos e direção de adapters | `src/test/java/**/architecture/*Test.java` | `mvnw.cmd --batch-mode -Dtest=ArchitectureTest test` |
| Regressão Java | unit + integration | Nenhum contrato ou fluxo mudado pelo pacote | `src/test/java/**/*Test.java`, `*IT.java` | `mvnw.cmd --batch-mode clean verify -Pintegration` |
| Formatação | plugin check/apply | Java inteiro; falha por arquivo divergente; idempotência | `src/main/java/**/*.java`, `src/test/java/**/*.java` | `mvnw.cmd --batch-mode spotless:check` |
| Revisão AWS | auditoria documental | Seis pilares, duas arquiteturas, evidências por linha e lacunas explícitas | `.specs/research/aws-well-architected-quick-review.md` | conferir inventário AWS validado e cada evidência citada |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Teste arquitetural isolado | `mvnw.cmd --batch-mode -Dtest=ArchitectureTest test` |
| Full | Fechamento de alteração Java | `mvnw.cmd --batch-mode clean verify -Pintegration` |
| Format | Plugin de formatação | `mvnw.cmd --batch-mode spotless:check` e prova negativa em scratch |
| Review | Relatório AWS | Inventário validado + checagem de seis pilares, linhas e decisões |

## Execution Plan

```text
Phase 1: Arquitetura executável
T01 -> T02

Phase 2: Evidência cloud
T02 -> T03
```

## Task Breakdown

## Phase 1: Arquitetura e build

### T01: Organizar módulos e adicionar regras ArchUnit

**Status:** Complete
**What:** Corrigir o wrapper Windows, mover classes de reconciliação, expiração e limpeza para responsabilidades coerentes e adicionar regras de domínio, ciclo, cobertura e adapters.
**Where:** `mvnw.cmd`, `pom.xml`, classes e testes Java tocados pelos movimentos, `.specs/features/engineering-quality-gates/`, `.specs/plans/`.
**Depends on:** none
**Requirement:** GATE-01
**Done when:** AC1–AC4 e C1–C4 passam, inclusive provas negativas, sem mudar contratos ou valores de configuração.
**Tests:** `ArchitectureTest`, testes existentes e integração quando Docker estiver disponível.
**Gate:** Full
**Commit:** `test(architecture): enforce modular boundaries`
**Result:** `clean verify -Pintegration` passou com 61 testes unitários e 68 de integração; `ArchitectureTest` passou 7/7, incluindo fixtures que violam domínio puro, ciclos e direção de adapters. Wrapper Windows executa `--version`. Nenhum contrato HTTP, schema ou valor de propriedade mudou.

### T02: Ligar Spotless ao build

**Status:** Planned
**What:** Fixar plugin e formatter, aplicar somente à árvore Java e provar check, falha e segunda aplicação sem diff.
**Where:** `pom.xml`, `src/main/java/`, `src/test/java/`, `.specs/features/engineering-quality-gates/`.
**Depends on:** T01
**Requirement:** GATE-02
**Done when:** AC5–AC7 e C5–C7 passam, sem alterar comportamento.
**Tests:** `spotless:check`, prova negativa em scratch e hashes após aplicar duas vezes.
**Gate:** Format, Full
**Commit:** `build(java): enforce reproducible formatting`

## Phase 2: Revisão AWS

### T03: Registrar revisão Well-Architected quick

**Status:** Planned
**What:** Descobrir a arquitetura no código e no Terraform, adquirir o corpus atual do framework e registrar conclusões proporcionais por pilar e arquitetura.
**Where:** `.specs/research/aws-well-architected-quick-review.md`, `.specs/features/engineering-quality-gates/`.
**Depends on:** T02
**Requirement:** GATE-03
**Done when:** AC8 e C8 têm evidência, classificações e lacunas corretas; nenhum achado é implementado automaticamente.
**Tests:** revisão das fontes, inventário vivo validado e validação independente final.
**Gate:** Review
**Commit:** `docs(aws): record evidence-backed architecture review`
