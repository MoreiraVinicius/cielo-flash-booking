# AWS Eventual Consistency Visuals Tasks

## Execution Protocol

Executar as tarefas em ordem. Cada tarefa atualiza este arquivo, passa pelo gate e produz um commit atômico. Nenhuma vista pode transformar a arquitetura high-load ou um efeito best effort em evidência executada.

**Design:** `.specs/features/eventual-consistency-aws-visuals/design.md`
**Status:** Approved
**Task count:** 6

## Test Coverage Matrix

| Camada | Tipo de teste | Expectativa | Local | Comando |
| --- | --- | --- | --- | --- |
| Verdade funcional | static review | Fronteira forte/eventual coincide com spec e código | SVG de consistência | contrato PowerShell dirigido |
| Topologia demo | static review | Recursos coincidem com Terraform | SVG demo | contrato PowerShell dirigido |
| Topologia high-load | static review | Estado planejado e componentes coincidem com decisões | SVG high-load | contrato PowerShell dirigido |
| Render | visual | XML válido, texto legível e setas sem sobreposição | três SVGs | Chrome headless + inspeção |
| README | contract | Links, alt text, seção e detalhes completos | `README.md` | `scripts/validate-readme.ps1` |
| Assets | inventory | Nenhum arquivo órfão em `docs/images` | `docs/images/*` | inventário de referências |

## Gate Check Commands

| Gate | Uso | Comando |
| --- | --- | --- |
| Spec | Planejamento | `validate_spec.py` e `validate_tasks.py` |
| Visual | Cada SVG | parse XML, contrato de tokens, render e inspeção |
| Documentation | README e assets | `powershell -File scripts/validate-readme.ps1` |
| Quick | Cada commit | `git diff --check` |
| Final | Fechamento | Documentation + inventário + Verifier |

## Execution Plan

```text
T01 -> T02 -> T03 -> T04 -> T05 -> T06
```

## Task Breakdown

### T01: Explicar a fronteira de consistência

**Status:** Pending
**What:** Criar uma vista que separe transação autoritativa, disponibilidade em cache e efeitos assíncronos.
**Where:** `docs/images/flash-booking-aws-eventual-consistency.svg`
**Depends on:** none
**Requirement:** AWSVIS-01, AWSVIS-02
**Done when:** O SVG mostra commit antes do `201`, Valkey somente no GET de evento, outbox pós-commit, duas filas, DLQs, SES, reconciliador e os três limites de convergência.
**Tests:** contrato de tokens derivado dos seis critérios e inspeção renderizada
**Gate:** Visual
**Commit:** `docs(visual): explain eventual consistency on AWS`

### T02: Redesenhar a topologia AWS demo

**Status:** Pending
**What:** Criar uma vista compacta dos recursos realmente provisionados, separada por edge, compute, dados e operação.
**Where:** `docs/images/flash-booking-aws-demo.svg`
**Depends on:** T01
**Requirement:** AWSVIS-03
**Done when:** A vista contém todas as peças exigidas em AWSVIS-03.1, topologia econômica correta e status aplicada, validada e destruída.
**Tests:** contrato de recursos contra os módulos Terraform e inspeção renderizada
**Gate:** Visual
**Commit:** `docs(visual): redraw demo AWS topology`

### T03: Redesenhar a arquitetura-alvo high-load

**Status:** Pending
**What:** Criar uma vista comparável à demo com Multi-AZ e escala independente, sem alegar execução.
**Where:** `docs/images/flash-booking-aws-high-load.svg`
**Depends on:** T02
**Requirement:** AWSVIS-03
**Done when:** O status não provisionado é inequívoco e a vista contém ECS Multi-AZ, Aurora, RDS Proxy, Valkey Multi-AZ, filas separadas e sinais de escala.
**Tests:** contrato de status/componentes contra STATE e inspeção renderizada
**Gate:** Visual
**Commit:** `docs(visual): redraw high-load AWS target`

### T04: Integrar as vistas no README

**Status:** Pending
**What:** Adicionar explicação textual curta, manter a consistência aberta e colocar as duas topologias em detalhes recolhíveis.
**Where:** `README.md`
**Depends on:** T03
**Requirement:** AWSVIS-01, AWSVIS-02, AWSVIS-03
**Done when:** A seção responde ao requisito original, referencia três imagens com alt text e não interrompe a narrativa do último ingresso.
**Tests:** leitura dirigida, contagem de imagens, detalhes balanceados e links locais
**Gate:** Documentation
**Commit:** `docs(readme): present AWS consistency architecture`

### T05: Validar o contrato das novas vistas

**Status:** Pending
**What:** Fazer o gate rejeitar fatos ausentes, status high-load incorreto, SVG inválido e detalhe desbalanceado, sem confundir menção no próprio validador com uso documental.
**Where:** `scripts/validate-readme.ps1`
**Depends on:** T04
**Requirement:** AWSVIS-03
**Done when:** O gate passa no estado correto, falha sob mutações dirigidas dos limites e status e continua exigindo que os quatro assets obsoletos não sejam referenciados pelo README.
**Tests:** execução positiva e sensores em cópias temporárias
**Gate:** Documentation
**Commit:** `test(docs): validate AWS consistency visuals`

### T06: Remover assets obsoletos e fechar rastreabilidade

**Status:** Pending
**What:** Excluir os quatro diagramas substituídos, provar que todos os assets restantes são usados e preparar a feature para o Verifier.
**Where:** `docs/images/`, `.specs/features/eventual-consistency-aws-visuals/`, `.specs/STATE.md`
**Depends on:** T05
**Requirement:** AWSVIS-03
**Done when:** Os quatro alvos não existem, o inventário não contém órfãos, todas as tarefas estão completas e o handoff aponta para verificação independente.
**Tests:** inventário, Documentation, validate_spec, validate_tasks e diff check
**Gate:** Final
**Commit:** `chore(docs): remove obsolete architecture assets`
