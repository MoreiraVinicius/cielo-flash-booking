# README Visual Storytelling Tasks

## Execution Protocol

Executar as tarefas em ordem. Cada tarefa termina após o gate indicado, atualiza este arquivo e produz um commit atômico. Nenhuma métrica nova pode substituir o baseline canônico se o runner ou seus thresholds falharem.

**Design:** `.specs/features/readme-visual-storytelling/design.md`
**Status:** Approved
**Task count:** 7

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Verdade documental | static | Estados e números coincidem com validação/specs | `.specs/**/*.md`, `docs/**/*.md` | `python .../validate_spec.py` e review dirigido |
| Harness de carga | static + performance | Dois processos explícitos, thresholds e resumo reproduzível | `compose.yaml`, `performance/demo/*` | `docker compose config`, `k6 inspect`, `run.ps1` |
| Evidência canônica | schema + review | Campos, unidades, proveniência e limitações completos | `performance/demo/baseline.json` | `scripts/validate-readme.ps1` |
| Sistema visual | XML + render | SVG válido, legível e sem dependência externa | `docs/images/*.svg` | parse XML, render e inspeção visual |
| README | contract | Cinco endpoints, status verdadeiro, links e assets válidos | `README.md` | `scripts/validate-readme.ps1` |
| Aplicação existente | regression | Nenhum comportamento Java regrediu | `src/main`, `src/test` | `./mvnw.cmd clean verify -Pintegration` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Alteração documental isolada | `git diff --check` |
| Spec | Specs e tasks | `python C:\Users\vinic\.codex\skills\tlc-spec-driven\scripts\validate_spec.py .specs\features\readme-visual-storytelling\spec.md` e `validate_tasks.py` |
| Visual | SVG | carregar como XML, renderizar PNG e inspecionar |
| Performance | Harness/baseline | `docker compose config`, `k6 inspect` e `performance\demo\run.ps1 -PublishBaseline` |
| Documentation | README | `powershell -File scripts\validate-readme.ps1` |
| Build | Fechamento | `mvnw.cmd clean verify -Pintegration` seguido do gate Documentation |

## Execution Plan

```text
Phase 1: Truth and evidence
T01 -> T02 -> T03

Phase 2: Visual narrative
T03 -> T04
T03 -> T05

Phase 3: Editorial assembly
T04 -> T06
T05 -> T06
T06 -> T07
```

## Task Breakdown

## Phase 1: Truth and Evidence

### T01: Reconciliar o estado documental da entrega

**Status:** Complete
**What:** Atualizar documentos públicos e metadados históricos para refletir que a demo foi implementada, validada, aplicada e destruída, mantendo high-load como alvo não executado.
**Where:** `.specs/features/flash-booking-demo/`, `.specs/REVIEW.md`, `docs/case-requirements-evaluation.md`, `docs/demo-runbook.md`
**Depends on:** none
**Requirement:** README-02
**Done when:** Não há afirmação atual dizendo que o código não existe, que a demo aguarda execução ou que só houve validação AWS estática; documentos históricos são rotulados como tal.
**Tests:** validação estrutural das specs e busca dirigida por afirmações obsoletas
**Gate:** Spec
**Commit:** `docs: reconcile validated demo evidence`
**Result:** Estados da demo, checklist de sucesso, avaliação do case, runbook e registro histórico reconciliados com a validação independente de 2026-09-11.

### T02: Corrigir o harness de carga multiprocesso

**Status:** Complete
**What:** Fazer o perfil de concorrência publicar portas efêmeras, descobrir ao menos duas instâncias e distribuir VUs explicitamente entre elas; registrar metadados e impedir publicação após falha.
**Where:** `compose.yaml`, `performance/demo/`
**Depends on:** T01
**Requirement:** README-03
**Done when:** Os três workloads aceitam múltiplos command URLs; o runner falha com menos de duas réplicas, identifica o ambiente e só publica após todos os thresholds passarem.
**Tests:** `docker compose config`, `k6 inspect` dos três scripts e revisão do fluxo de falha
**Gate:** Performance
**Commit:** `test(performance): make local baseline multiprocess`
**Result:** Réplicas publicam portas efêmeras, o runner exige duas URLs únicas e os VUs de comandos/misto são distribuídos e marcados por processo; Compose, sintaxe PowerShell e os três `k6 inspect` passaram.

### T03: Publicar um baseline local rastreável

**Status:** Pending
**What:** Executar o harness corrigido quando Docker estiver disponível, versionar o resumo sanitizado e gerar um gráfico derivado; caso contrário, normalizar somente o baseline já versionado e declarar a limitação.
**Where:** `performance/demo/baseline.json`, `performance/demo/README.md`, `docs/images/flash-booking-performance.svg`
**Depends on:** T02
**Requirement:** README-03
**Done when:** O arquivo informa commit, ambiente, VUs, duração, req/s, p50/p95/p99, falhas e limitações; o SVG mostra apenas esses dados e chama o cenário misto de agregado.
**Tests:** validação do schema, XML, números e unidades contra o JSON
**Gate:** Performance, Visual
**Commit:** `docs(performance): publish reproducible local baseline`

## Phase 2: Visual Narrative

### T04: Criar identidade e invariante do produto

**Status:** Pending
**What:** Criar um hero original do projeto e uma explicação visual do último ingresso, reserva temporária e oversell zero.
**Where:** `docs/images/flash-booking-hero.svg`, `docs/images/flash-booking-last-ticket.svg`
**Depends on:** T03
**Requirement:** README-01
**Done when:** Os SVGs identificam o case como independente, não representam pagamento e tornam claro que apenas uma transação concorrente pode consumir o último ingresso.
**Tests:** XML, render e inspeção visual em resolução de README
**Gate:** Visual
**Commit:** `docs(visual): explain flash booking invariant`

### T05: Visualizar o processo e a evolução arquitetural

**Status:** Pending
**What:** Criar uma trilha do desenvolvimento orientado por especificação e uma comparação compacta entre demo e high-load.
**Where:** `docs/images/flash-booking-spec-driven.svg`, `docs/images/flash-booking-architecture-evolution.svg`
**Depends on:** T03
**Requirement:** README-02, README-03
**Done when:** A trilha separa a lane demo concluída da high-load planejada; a comparação preserva o mesmo core Java e distingue topologia, escala, resiliência e estado de validação.
**Tests:** XML, render, inspeção visual e revisão contra specs/ADRs
**Gate:** Visual
**Commit:** `docs(visual): compare delivery and target architecture`

## Phase 3: Editorial Assembly

### T06: Reescrever o README com divulgação progressiva

**Status:** Pending
**What:** Montar a narrativa final com hero, TL;DR, quick start, contrato HTTP, invariante, método, arquiteturas, evidências, resiliência, segurança, observabilidade, limitações e galeria recolhível.
**Where:** `README.md`
**Depends on:** T04, T05
**Requirement:** README-01, README-02, README-03
**Done when:** O fluxo principal é compreensível sem abrir documentos auxiliares, contém exatamente cinco endpoints e mantém os quatro diagramas detalhados em seções `<details>`.
**Tests:** revisão de leitura, links e termos obrigatórios
**Gate:** Documentation
**Commit:** `docs: rebuild readme around evidence and clarity`

### T07: Automatizar e fechar a validação documental

**Status:** Pending
**What:** Criar o gate determinístico do README, executar regressão completa, atualizar rastreabilidade e reconciliar o handoff do projeto.
**Where:** `scripts/validate-readme.ps1`, `.specs/features/readme-visual-storytelling/`, `.specs/STATE.md`
**Depends on:** T06
**Requirement:** README-01, README-02, README-03
**Done when:** O gate detecta as violações definidas na spec, Maven passa, todos os requisitos estão `Validated` e o handoff registra a feature concluída sem arquivos pendentes conhecidos.
**Tests:** gate documental, Maven build, validate_spec, validate_tasks, validate_state após verificação independente
**Gate:** Build
**Commit:** `test(docs): validate readme evidence contract`
