# Gates de engenharia e revisão AWS

## Problem Statement

O build atual não verifica as fronteiras do monólito nem a formatação Java. Uma revisão manual precisa descobrir dependências que cruzam responsabilidades e o contraste entre a demo AWS e a arquitetura high-load ainda carece de avaliação Well-Architected rastreável.

## Goals

- Tornar as fronteiras importantes executáveis sem impor camadas desnecessárias.
- Padronizar a formatação Java no build e na CI existente.
- Registrar evidências e incertezas da demo e do desenho high-load por pilar AWS.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Maven Enforcer, TFLint, Trivy e Checkov | Excluídos pelo responsável neste ciclo. |
| Microserviços ou nova topologia AWS | AD-002 e AD-006 permanecem. |
| Mudanças de contrato HTTP, schema, dados ou comportamento de reserva | Esta feature protege a implementação atual. |
| Apply/deploy, push ou alteração de recursos AWS | Uma revisão não autoriza mutação externa. |

## Assumptions & Open Questions

| Decisão | Valor | Origem |
| --- | --- | --- |
| Organização interna | Mover pacotes quando necessário para expressar responsabilidades; preservar contratos | Autorização do usuário em 2026-09-27 |
| Fronteiras | Domínio puro e adapters externos; aplicações de capacidade podem depender de portas e módulos a jusante | AD-002 e código atual |
| Formatação | Abranger Java de produção e teste | Plano de qualidade |
| Revisão AWS | `evidência insuficiente` é resultado válido; aceite de risco requer decisão explícita | Correção do usuário |

**Open questions:** none.

## User Stories

### P1: Detectar regressão arquitetural

**Acceptance Criteria:**

1. WHEN `mvnw.cmd --batch-mode clean verify -Pintegration` runs in an environment with its dependencies available THEN it SHALL run the architecture tests and exit `0` for the accepted code.
2. IF a class in `com.cielo.flashbooking.domain..` depends on Spring, Jakarta, Jackson, AWS SDK, JDBC, or an outer project package THEN the architecture test SHALL fail and name the dependency.
3. IF the explicitly enumerated first-level modules form a dependency cycle THEN the architecture test SHALL fail and name the cycle; every production class SHALL map to exactly one module or the explicitly designated application bootstrap.
4. IF a domain, application, controller, or consumer class depends on an outbound adapter implementation THEN the architecture test SHALL fail and name the dependency; explicit infrastructure wiring remains allowed.

### P1: Padronizar formatação Java

**Acceptance Criteria:**

5. WHEN `mvnw.cmd spotless:check` runs over `src/main/java/**/*.java` and `src/test/java/**/*.java` in the accepted tree THEN it SHALL exit `0`.
6. IF a Java file in scope violates the formatter THEN `mvnw.cmd verify` SHALL fail and print that file's path.
7. WHEN `mvnw.cmd spotless:apply` runs twice on the same tree THEN the second run SHALL leave the file contents unchanged and `spotless:check` SHALL exit `0`.

### P1: Revisar AWS com evidência

**Acceptance Criteria:**

8. WHEN the AWS Well-Architected quick review completes THEN `.specs/research/aws-well-architected-quick-review.md` SHALL cover the six framework pillars, separate demo and documented high-load, cite source locations, and classify findings as `atendido no escopo analisado`, `risco aceito`, `remediação proposta`, `não aplicável`, or `evidência insuficiente`; risk acceptance SHALL link to an explicit decision.

## Implicit-Requirement Dimensions

| Dimensão | Resolução |
| --- | --- |
| Validation | AC1–AC7, com provas positivas e negativas. |
| Failure modes | AC2–AC4 e AC6 falham com dependência ou arquivo nomeado. |
| Idempotency/retry | AC7 exige duas aplicações sem mudança na segunda. |
| Authorization | A revisão não altera IAM, API ou autenticação. |
| Concurrency/ordering | Análise de bytecode e de arquivos; sem alteração em fluxos concorrentes. |
| Data lifecycle | Schema e dados permanecem intactos. |
| External dependencies | Falha de resolução Maven e impossibilidade de consultar fonte AWS são reportadas, nunca convertidas em passe. |
| State transitions | Nenhum estado de reserva muda. |
| Observability | Erros dos gates mostram a dependência/arquivo; relatório mostra evidência e lacunas. |

## Requirement Traceability

| Requirement ID | Criteria | Status |
| --- | --- | --- |
| GATE-01 | 1–4 | Implemented, pending independent verification |
| GATE-02 | 5–7 | Implemented, pending independent verification |
| GATE-03 | 8 | Planned |

## Success Criteria

- [ ] Os três grupos de critérios têm provas nomeadas e resultados registrados em `validation.md`.
- [ ] O código e a CI não passam a exigir ferramentas explicitamente excluídas.
