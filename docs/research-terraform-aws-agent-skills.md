# Pesquisa: skill global de Terraform e AWS

Pesquisa em 9 de setembro de 2026. O objetivo é escolher instruções reutilizáveis para agentes (`SKILL.md`), não instalar provedores, CLIs ou ferramentas de scan. Nenhuma skill foi instalada e nenhuma configuração foi alterada.

## Recomendação

Instalar globalmente **somente** [`terraform-style-guide` do repositório oficial `hashicorp/agent-skills`](https://github.com/hashicorp/agent-skills/tree/main/plugins/terraform/skills/terraform-style-guide), depois de fixar e revisar uma revisão do repositório.

O catálogo do mantenedor classifica a skill como `active`, descreve o caminho canônico de instalação e oferece instalação individual; o próprio bundle declara compatibilidade com Codex. A skill é específica para criar, manter e revisar HCL, portanto tem baixo risco de ativar fora desse contexto. Ela cobre organização, versões, variáveis, providers, módulos, outputs e referências à [style guide oficial do Terraform](https://developer.hashicorp.com/terraform/language/style). [Catálogo oficial](https://github.com/hashicorp/agent-skills/blob/main/SKILLS.md) · [compatibilidade e comando do fornecedor](https://github.com/hashicorp/agent-skills/blob/main/plugins/terraform/README.md).

Sua orientação de segurança é uma boa base para AWS: exige criptografia, rede privada quando aplicável, menor privilégio, logs, proíbe credenciais embutidas, exige outputs sensíveis e prioriza integrações nativas com gerenciadores de segredos, recursos efêmeros e atributos write-only. [Referência de segurança da skill](https://github.com/hashicorp/agent-skills/blob/main/plugins/terraform/skills/terraform-style-guide/SECURITY.md).

Para instalar posteriormente pelo fluxo de skills do Codex, a origem e o subdiretório são:

```text
hashicorp/agent-skills
plugins/terraform/skills/terraform-style-guide
```

Isso deve resultar em uma única pasta sob `~/.codex/skills/`; não instalar o bundle Terraform inteiro. O bundle contém 16 skills, incluindo desenvolvimento de providers, Azure, Packer e Stacks, que não são uma boa política global para todos os repositórios.

## AWS: complementar com fonte normativa, não com outra skill global

Não foi identificada uma skill Terraform para AWS, oficial e pronta para instalação, no toolkit da AWS. O catálogo oficial limita IaC a CDK e CloudFormation, e a proposta de uma skill Terraform permanece aberta. [Catálogo AWS](https://github.com/aws/agent-toolkit-for-aws/blob/main/skills/README.md) · [issue de Terraform](https://github.com/aws/agent-toolkit-for-aws/issues/115).

Por isso, a `terraform-style-guide` deve ser complementada pelas [boas práticas oficiais da AWS para o Terraform AWS Provider](https://docs.aws.amazon.com/prescriptive-guidance/latest/terraform-aws-provider-best-practices/introduction.html), sobretudo:

- roles IAM e credenciais temporárias, com menor privilégio;
- estado remoto em S3, criptografado, versionado e com acesso restrito; o guia atual recomenda `use_lockfile` e marca o bloqueio via DynamoDB como depreciado;
- Secrets Manager em vez de segredos no código/estado, e outputs sensíveis;
- scans e gates no CI antes de `apply`.

Fontes: [segurança](https://docs.aws.amazon.com/prescriptive-guidance/latest/terraform-aws-provider-best-practices/security.html) · [backend/state](https://docs.aws.amazon.com/prescriptive-guidance/latest/terraform-aws-provider-best-practices/backend.html) · [estrutura e validação](https://docs.aws.amazon.com/prescriptive-guidance/latest/terraform-aws-provider-best-practices/structure.html).

## Comparação e compatibilidade

| Opção | Avaliação | Decisão |
| --- | --- | --- |
| HashiCorp `terraform-style-guide` | Mantenedor do Terraform, `active`, instalação individual, documentação e segurança oficiais, com suporte explícito a Codex. | **Instalar globalmente.** |
| HashiCorp `terraform-test` | Também oficial e `active`, mas é especializada em testes Terraform; só agrega quando a equipe começar a criar/validar testes IaC regularmente. | Não instalar agora; avaliar por projeto. |
| `antonbabenko/terraform-skill` | Skill comunitária bastante adotada e abrangente, com testes, módulos, CI/CD e OpenTofu. Ainda assim, duplicaria a orientação de estilo/segurança de uma skill oficial e introduziria outra autoridade global. [Repositório](https://github.com/antonbabenko/terraform-skill). | Não combinar globalmente. |
| Skills AWS genéricas/bundle `aws-core` | A AWS recomenda o bundle como base ampla, mas o escopo IaC atual dele é CDK/CloudFormation, não Terraform. Também se sobreporia a skills AWS já disponíveis nesta sessão (IAM, Secrets Manager, serverless, containers, observabilidade e custos). | Não instalar para resolver Terraform. |

Não há colisão material esperada com as skills já disponíveis: `aws-cdk` e `aws-cloudformation` são explícitas para outras linguagens/ferramentas de IaC; `aws-iam`, `aws-secrets-manager` e as demais skills AWS tratam serviços e controles específicos; e as skills Java/Spring só ativam em código da aplicação. A regra prática é manter uma única autoridade global para HCL e consultar a documentação AWS para decisões de serviço, identidade, state e segurança.

Antes da instalação, revisar o `SKILL.md` e arquivos referenciados na revisão fixada. Depois, testar em um repositório Terraform não crítico com `terraform fmt`, `terraform validate`, plano sem credenciais estáticas e revisão humana do diff; uma skill orienta o agente, mas não substitui validação, policy-as-code nem revisão de infraestrutura.
