# Pesquisa de skills Terraform e AWS

Data: 2026-09-27. Escopo: recomendação e registro de procedência. `terraform-style-guide` e `aws-well-architected-review` foram instaladas globalmente nas revisões auditadas; nenhuma alteração de infraestrutura ou decisão de arquitetura foi feita.

## Contexto e critério

O contexto recebido da análise local é Java 21/Spring Boot 3.5, JDBC, monólito modular com papéis query/command/worker em ECS Fargate; API Gateway IAM/SigV4, WAF, ALB, PostgreSQL, Valkey, SQS e SES. Terraform usa quatro módulos e testes nativos com mocks. A consulta local `terraform version` confirmou 1.16.1/windows_amd64; `terraform test -help` foi consultado sem executar testes ou provisionamento.

Prioridade significa aderência a esse contexto, complementaridade com skills já instaladas e qualidade do conteúdo inspecionado. Popularidade não foi usada como prova de qualidade. Skills são instruções para agentes, não substitutos de lint, testes, plano revisado ou evidências de operação.

## Procedência e manutenção verificadas

| Repositório | Revisão observada | Licença declarada | Situação |
|---|---|---|---|
| `hashicorp/agent-skills` | `516354c484b43fa5469567485113dd0c769c3d24`, commit em 2026-09-24 | MPL-2.0 | Não arquivado; catálogo marca as três skills abaixo como active |
| `aws/agent-toolkit-for-aws` | `dda61487cb4fa8b6dbc75ad4e27c2181adc9d058`, commit em 2026-09-26 | Apache-2.0 | Não arquivado; toolkit oficial AWS com suporte a Codex |

Fontes: APIs públicas de [metadados HashiCorp](https://api.github.com/repos/hashicorp/agent-skills), [commit HashiCorp](https://api.github.com/repos/hashicorp/agent-skills/commits/516354c484b43fa5469567485113dd0c769c3d24), [metadados AWS](https://api.github.com/repos/aws/agent-toolkit-for-aws), [commit AWS](https://api.github.com/repos/aws/agent-toolkit-for-aws/commits/dda61487cb4fa8b6dbc75ad4e27c2181adc9d058), [licença HashiCorp](https://github.com/hashicorp/agent-skills/blob/516354c484b43fa5469567485113dd0c769c3d24/LICENSE), [licença AWS](https://github.com/aws/agent-toolkit-for-aws/blob/dda61487cb4fa8b6dbc75ad4e27c2181adc9d058/LICENSE). Esses fatos indicam manutenção recente, não certificação de todos os exemplos.

## Ranking contextual de candidatos

### 1. terraform-style-guide — recomendada

Origem: `hashicorp/agent-skills`. Caminho exato: `plugins/terraform/skills/terraform-style-guide`.

O SKILL.md cobre convenções HCL, variáveis e outputs tipados/documentados, validação, versionamento, fmt/validate e checks adicionais. Seu `SECURITY.md` orienta rede privada, menor privilégio, criptografia e prioriza integração nativa com gerenciadores de segredos. É o preenchimento mais direto da lacuna Terraform local. A declaração AWS provider `~> 6.0` do exemplo combina com o projeto.

Ressalvas: manter os pins e decisões locais em vez de interpretar a orientação de versões recentes como autorização de upgrade. Não renomear arquivos/módulos existentes só para copiar o layout do exemplo. `sensitive = true` é redação de saída, não elimina armazenamento em state; observar as instruções adicionais para segredos.

Fontes: [SKILL.md](https://github.com/hashicorp/agent-skills/blob/516354c484b43fa5469567485113dd0c769c3d24/plugins/terraform/skills/terraform-style-guide/SKILL.md), [SECURITY.md](https://github.com/hashicorp/agent-skills/blob/516354c484b43fa5469567485113dd0c769c3d24/plugins/terraform/skills/terraform-style-guide/SECURITY.md).

### 2. aws-well-architected-review — recomendada para revisões explícitas

Origem: `aws/agent-toolkit-for-aws`. Caminho exato: `skills/core-skills/aws-well-architected-review`.

Inspeciona código e IaC, obtém o framework AWS atual e relaciona constatações a evidências. A calibração aceita soluções mais simples para workloads de menor criticidade, útil para comparar demo e high-load sem promover toda demo a arquitetura cara. Disponibiliza modos quick, completo e por pilares. AWS MCP é recomendado, mas há fallback HTTPS para documentação.

Ressalvas: revisão completa exige inventário e avaliação extensos, incluindo material de referência e relatório inline obrigatório. Deve ser acionada para uma revisão cloud delimitada, não para qualquer edição Terraform. Resultados alimentam o fluxo `.specs/`; a skill não substitui as decisões do projeto nem autoriza remediação automática.

Fonte: [SKILL.md](https://github.com/aws/agent-toolkit-for-aws/blob/dda61487cb4fa8b6dbc75ad4e27c2181adc9d058/skills/core-skills/aws-well-architected-review/SKILL.md). Arquivo lido integralmente via HTTPS; referências listadas identificadas, mas não foi executado o workflow de revisão.

### 3. terraform-test — forte aderência, adoção condicionada à correção de exemplos

Origem: `hashicorp/agent-skills`. Caminho exato: `plugins/terraform/skills/terraform-test`.

Complementa os testes existentes com mocks, asserts, casos negativos e isolamento de estado. O SKILL.md favorece plan e mocks, mas também inclui exemplos apply que podem criar recursos reais.

Problemas concretos observados: exemplos mostram filtro por nome de run e arquivo como argumento posicional; a CLI 1.16.1 e documentação usam `-filter=<arquivo>`. Também recomenda `-no-cleanup`, opção ausente na ajuda da CLI instalada e na referência oficial consultada. Recomendar uso somente após corrigir/sobrepor esses exemplos e preservar mocks no CI padrão. Uma instalação intacta e acrítica espalharia instruções incorretas.

Fontes: [SKILL.md](https://github.com/hashicorp/agent-skills/blob/516354c484b43fa5469567485113dd0c769c3d24/plugins/terraform/skills/terraform-test/SKILL.md), [CLI test oficial, incluindo sintaxe PowerShell](https://developer.hashicorp.com/terraform/cli/commands/test). Nenhum teste apply foi executado.

### 4. aws-containers — candidata complementar, verificar carregamento

Origem: `aws/agent-toolkit-for-aws`. Caminho exato: `skills/core-skills/aws-containers`.

Direciona para referências ECS/Fargate, task definitions, papéis IAM, injeção de segredos, ECR e diagnóstico de deploy. Ajusta-se diretamente aos três papéis ECS do projeto. Instalar o diretório completo é necessário: `references/ecs.md` encaminha a arquivos adicionais, incluindo `ecs-workloads.md` e `ecs-managing-compute.md`.

Ressalvas: também contém EKS/Beanstalk, que não justificam mudança de plataforma. As instruções pedem referências da skill como fonte principal; as decisões locais e documentação atual devem prevalecer em conflitos. Na leitura raw observou-se uma descrição de front matter dividida em linhas sem indentação evidente; validar o YAML/carregamento no Codex antes da adoção. Essa possível falha de empacotamento não foi confirmada com parser YAML nesta pesquisa.

Fontes: [SKILL.md](https://github.com/aws/agent-toolkit-for-aws/blob/dda61487cb4fa8b6dbc75ad4e27c2181adc9d058/skills/core-skills/aws-containers/SKILL.md), [roteador ECS](https://github.com/aws/agent-toolkit-for-aws/blob/dda61487cb4fa8b6dbc75ad4e27c2181adc9d058/skills/core-skills/aws-containers/references/ecs.md).

### 5. refactor-module — opcional e condicionada; menor ganho imediato

Origem: `hashicorp/agent-skills`. Caminho exato: `plugins/terraform/skills/refactor-module`.

Útil para contratos de entrada/saída, responsabilidades, blocos moved e preservação de endereços. O projeto já tem módulos, portanto só se justifica ao identificar acoplamento ou duplicação reais.

Ressalvas: o exemplo de versionamento combina `source` Git com `version`, combinação que a referência oficial restringe a módulos de registry. Há exemplos de `state mv` e `apply`: não executá-los só porque a skill lista esses passos. Inspeção de state pode revelar segredos; não despejar o JSON inteiro em logs. Não recomendo instalar intacta como política universal de refatoração.

Fontes: [SKILL.md](https://github.com/hashicorp/agent-skills/blob/516354c484b43fa5469567485113dd0c769c3d24/plugins/terraform/skills/refactor-module/SKILL.md), [module/version oficial](https://developer.hashicorp.com/terraform/language/block/module#version).

## Não priorizar agora

- Pacote completo HashiCorp: inclui desenvolvimento de providers, Packer, Azure e Stacks, sem uso identificado neste projeto. O [catálogo oficial](https://github.com/hashicorp/agent-skills/blob/516354c484b43fa5469567485113dd0c769c3d24/SKILLS.md) permite selecionar skills individualmente.
- Pacote AWS inteiro: adiciona CDK, CloudFormation e diversas áreas fora do escopo. O [catálogo AWS](https://github.com/aws/agent-toolkit-for-aws/blob/dda61487cb4fa8b6dbc75ad4e27c2181adc9d058/skills/README.md) distingue núcleo e especializadas.
- `aws-messaging-and-streaming`: avaliada, porém adiada. É um roteador amplo; o resumo exactly-once/FIFO precisa qualificação para não ser interpretado como garantia end-to-end do outbox/SES. A recomendação genérica de chave KMS gerenciada pelo cliente deve ser ponderada com custo/necessidade da demo. [Fonte inspecionada](https://github.com/aws/agent-toolkit-for-aws/blob/dda61487cb4fa8b6dbc75ad4e27c2181adc9d058/skills/core-skills/aws-messaging-and-streaming/SKILL.md).

## Instalação global e práticas executáveis

AWS documenta suporte ao Codex em `~/.codex/skills/`; na máquina atual corresponde a `C:\Users\vinic\.codex\skills`. HashiCorp publica integração Codex. `terraform-style-guide` e `aws-well-architected-review` foram instaladas com diretório completo e revisão fixa e já aparecem no catálogo desta sessão. Os pacotes são majoritariamente Markdown/HCL, mas exemplos Bash/jq precisam ser adaptados a PowerShell quando usados. Registrar procedência e alterações locais em futuras atualizações. Fontes: [AWS skills](https://github.com/aws/agent-toolkit-for-aws/blob/dda61487cb4fa8b6dbc75ad4e27c2181adc9d058/skills/README.md), [Terraform bundle](https://github.com/hashicorp/agent-skills/blob/516354c484b43fa5469567485113dd0c769c3d24/plugins/terraform/README.md).

Escopo da inspeção: lidos os cinco SKILL.md ranqueados, o SKILL.md de messaging, o `SECURITY.md` de estilo e o roteador `references/ecs.md`. As referências profundas de WAR, mocks/CI e ECS foram identificadas pelos respectivos roteadores, mas não integralmente auditadas. As duas skills instaladas incluem seus arquivos de referência. WAR depende de acesso à documentação live e de scratch local para o inventário. Compatibilidade declarada pelos mantenedores foi distinguida de execução local: somente versão/ajuda Terraform e descoberta das skills foram verificadas nesta máquina. Os workflows, a infraestrutura AWS e a qualidade comparativa ainda não foram avaliados.

TFLint, Trivy e Checkov foram excluídos do ciclo atual pelo responsável. São ferramentas executáveis, não skills. Manter `fmt`, `validate` e `test` já existentes; reabrir essa decisão somente diante de um defeito ou requisito que os checks atuais não detectem.
