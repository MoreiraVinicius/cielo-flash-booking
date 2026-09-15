# AWS Eventual Consistency Visuals Tasks

## Execution Protocol

Executar as tarefas em ordem. Cada tarefa atualiza este arquivo, passa pelo gate e produz um commit atômico. Nenhuma vista pode transformar a arquitetura high-load ou um efeito best effort em evidência executada.

**Design:** `.specs/features/eventual-consistency-aws-visuals/design.md`
**Status:** Approved
**Task count:** 7

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
T01 -> T02 -> T03 -> T04 -> T05 -> T06 -> T07
```

## Task Breakdown

### T01: Explicar a fronteira de consistência

**Status:** Complete
**What:** Criar uma vista que separe transação autoritativa, disponibilidade em cache e efeitos assíncronos.
**Where:** `docs/images/flash-booking-aws-eventual-consistency.svg`
**Depends on:** none
**Requirement:** AWSVIS-01, AWSVIS-02
**Done when:** O SVG mostra commit antes do `201`, Valkey somente no GET de evento, outbox pós-commit, duas filas, DLQs, SES, reconciliador e os três limites de convergência.
**Tests:** contrato de tokens derivado dos seis critérios e inspeção renderizada
**Gate:** Visual
**Commit:** `docs(visual): explain eventual consistency on AWS`
**Result:** SVG XML válido, contrato de 13 fatos aprovado e render nativo inspecionado após correção de contraste.

### T02: Redesenhar a topologia AWS demo

**Status:** Complete
**What:** Criar uma vista compacta dos recursos realmente provisionados, separada por edge, compute, dados e operação.
**Where:** `docs/images/flash-booking-aws-demo.svg`
**Depends on:** T01
**Requirement:** AWSVIS-03
**Done when:** A vista contém todas as peças exigidas em AWSVIS-03.1, topologia econômica correta e status aplicada, validada e destruída.
**Tests:** contrato de recursos contra os módulos Terraform e inspeção renderizada
**Gate:** Visual
**Commit:** `docs(visual): redraw demo AWS topology`
**Result:** Topologia provisionada condensada em quatro zonas; XML e 21 fatos validados, render nativo inspecionado e conexões operacionais ambíguas removidas.

### T03: Redesenhar a arquitetura-alvo high-load

**Status:** Complete
**What:** Criar uma vista comparável à demo com Multi-AZ e escala independente, sem alegar execução.
**Where:** `docs/images/flash-booking-aws-high-load.svg`
**Depends on:** T02
**Requirement:** AWSVIS-03
**Done when:** O status não provisionado é inequívoco e a vista contém ECS Multi-AZ, Aurora, RDS Proxy, Valkey Multi-AZ, filas separadas e sinais de escala.
**Tests:** contrato de status/componentes contra STATE e inspeção renderizada
**Gate:** Visual
**Commit:** `docs(visual): redraw high-load AWS target`
**Result:** Vista comparável à demo, com Multi-AZ compacto, escala separada e status não provisionado repetido; XML, 18 fatos e render nativo aprovados.

### T04: Integrar as vistas no README

**Status:** Complete
**What:** Adicionar explicação textual curta, manter a consistência aberta e colocar as duas topologias em detalhes recolhíveis.
**Where:** `README.md`
**Depends on:** T03
**Requirement:** AWSVIS-01, AWSVIS-02, AWSVIS-03
**Done when:** A seção responde ao requisito original, referencia três imagens com alt text e não interrompe a narrativa do último ingresso.
**Tests:** leitura dirigida, contagem de imagens, detalhes balanceados e links locais
**Gate:** Quick + leitura dirigida; o gate Documentation é ampliado em T05
**Commit:** `docs(readme): present AWS consistency architecture`
**Result:** Seção inserida após o fluxo do último ingresso; 13 imagens e 6 blocos details válidos, com as três novas referências existentes e alt text descritivo.

### T05: Validar o contrato das novas vistas

**Status:** Complete
**What:** Fazer o gate rejeitar fatos ausentes, status high-load incorreto, SVG inválido e detalhe desbalanceado, sem confundir menção no próprio validador com uso documental.
**Where:** `scripts/validate-readme.ps1`
**Depends on:** T04
**Requirement:** AWSVIS-03
**Done when:** O gate passa no estado correto, falha sob mutações dirigidas dos limites e status e continua exigindo que os quatro assets obsoletos não sejam referenciados pelo README.
**Tests:** execução positiva e sensores em cópias temporárias
**Gate:** Documentation
**Commit:** `test(docs): validate AWS consistency visuals`
**Result:** Gate positivo aprovado com 13 imagens e 6 details; sensores em cópias temporárias mataram alterações do TTL e do status high-load (exit 1 em ambos).

### T06: Remover assets obsoletos e fechar rastreabilidade

**Status:** Complete
**What:** Excluir os quatro diagramas substituídos e fazer o inventário documental rejeitar arquivos órfãos.
**Where:** `docs/images/`, `scripts/validate-readme.ps1`, `.specs/features/eventual-consistency-aws-visuals/`, `.specs/STATE.md`
**Depends on:** T05
**Requirement:** AWSVIS-03
**Done when:** Os quatro alvos não existem, o inventário não contém órfãos e o handoff registra a revisão semântica final antes do Verifier.
**Tests:** inventário, Documentation, validate_spec, validate_tasks e diff check
**Gate:** Final
**Commit:** `chore(docs): remove obsolete architecture assets`
**Result:** Quatro assets removidos; 13/13 restantes usados no README. O gate rejeita assets obsoletos existentes e arquivos sem link documental. Revisão final de rotas registrada em T07.

### T07: Refinar rotas e fronteiras semânticas

**Status:** Pending
**What:** Mostrar as rotas HTTP e nomes de eventos completos, separar serviços regionais da VPC e encaminhar acessos ao banco, consumo SQS e solicitação SES sem cruzar cartões.
**Where:** `docs/images/flash-booking-aws-eventual-consistency.svg`, `docs/images/flash-booking-aws-demo.svg`, `docs/images/flash-booking-aws-high-load.svg`, `scripts/validate-readme.ps1`, `.specs/features/eventual-consistency-aws-visuals/`, `.specs/STATE.md`
**Depends on:** T06
**Requirement:** AWSVIS-01, AWSVIS-02, AWSVIS-03
**Done when:** Cache miss parte da Query API, o worker consome SQS e solicita SES, serviços regionais não aparecem dentro da VPC, as rotas/eventos estão completos e nenhum acesso ao RDS atravessa o Valkey.
**Tests:** contrato dirigido das rotas, parse XML, três renders nativos e inspeção de endpoints das setas
**Gate:** Visual + Documentation
**Commit:** `fix(docs): clarify AWS boundaries and event routes`
