# Skills globais para engenharia Java, Spring, Terraform e AWS

Pesquisa em 2026-09-27, após inspeção do projeto. Status: quatro skills globais instaladas; mudanças de engenharia ainda não executadas. Não altera as decisões em `.specs/STATE.md`.

## Conclusão

Manter a base Java/Spring existente e complementar principalmente Terraform e revisão de soluções AWS. `terraform-style-guide`, `aws-well-architected-review`, `maven-dependency-upgrader` e `jvm-incident-triage` foram instaladas globalmente nas revisões auditadas. Não instalar coleções inteiras nem substituir as regras locais por um papel genérico de “arquiteto Java”.

A aplicação de `tlc-discover` levou à alternativa menor: reutilizar o que já existe e preencher lacunas específicas. Esta é uma pesquisa para apoiar a escolha do usuário, não um design de implementação aprovado. Não há medição comparativa de produtividade dessas skills no projeto; o ranking expressa aderência e qualidade do material inspecionado, não superioridade comprovada por benchmark.

## 1. Análise do projeto

| Área | Evidência local | Consequência para a seleção |
|---|---|---|
| Linguagem e framework | [pom.xml](C:/Users/vinic/projetos/cielo/pom.xml): Java 21, Spring Boot 3.5.0, Maven e AWS SDK BOM 2.30.1 | Preservar compatibilidade com Boot 3; avaliar upgrades por conjunto de dependências, sem migração automática para Boot 4 |
| Persistência | Spring JDBC, PostgreSQL e Flyway; AD-020 estabelece JDBC como tecnologia única de runtime | Skills centradas em JPA/Hibernate não são o padrão deste projeto; Spring Data JDBC também não é sinônimo de JdbcTemplate |
| Arquitetura | AD-002 e AD-016: monólito modular hexagonal; query-api, command-api e worker executam a mesma imagem | Melhorar contratos e dependências internas; não transformar deployments separados em justificativa para microserviços |
| Evolução | AD-006: high-load é arquitetura-alvo documentada, sem implementação e validação executáveis próprias | Separar avaliações da demo atual de hipóteses sobre alta carga |
| Cloud | ECS Fargate, API Gateway IAM/SigV4, WAF, ALB, RDS, Valkey, SQS e SES | Priorizar AWS/ECS, falhas, segurança e custo; não EKS, CDK ou autenticação JWT como novos padrões |
| Terraform | [versions.tf](C:/Users/vinic/projetos/cielo/infra/environments/demo/versions.tf): CLI ~> 1.16.0, AWS provider ~> 6.0; quatro módulos com testes nativos e mocks | Skills de HCL, contratos de módulos e testes têm aplicação direta |
| Verificação | [CI](C:/Users/vinic/projetos/cielo/.github/workflows/ci.yml): Maven verify com integração; Terraform fmt, validate e test | Já há base de testes. No POM/CI inspecionados não há configuração de ArchUnit, Spotless, Maven Enforcer, TFLint ou scanner IaC |

A inspeção incluiu controllers e serviços de reserva, publicador outbox, configuração, Dockerfile e inventário de testes. Há testes voltados a concorrência, idempotência, expiração e mensageria. Não foi executada a suíte nesta pesquisa, nem foi feita auditoria exaustiva de corretude ou segurança.

Fontes das decisões: [STATE.md](C:/Users/vinic/projetos/cielo/.specs/STATE.md), especialmente AD-002, AD-004, AD-006, AD-014, AD-016 e AD-020. O snapshot de código consultado foi HEAD `8c2d159`; alterações locais preexistentes foram preservadas.

### Cobertura global que já existe

Foram inspecionados os diretórios de skills do usuário, sem instalar ou alterar conteúdo:

- `java-spring-engineering`: baseline proporcional de Java/Spring, arquitetura e verificação.
- `codebase-design` e `domain-modeling`: design de módulos e vocabulário do domínio.
- `configuration-properties`, `transactional-patterns`, `flyway-migrations` e `design-postgres-tables`: preocupações delimitadas de configuração e persistência.
- `testing-pyramid`, `tdd`, `diagnosing-bugs` e `code-review`: testes, diagnóstico e revisão.
- Fluxo TLC: descoberta, especificação, implementação e validação já têm orientação local.

Existe também `improve-codebase-architecture` em disco, mas não aparece no catálogo disponível desta sessão: presença no filesystem não prova disponibilidade de invocação. Não foi usada nesta pesquisa.

Não foi identificada skill dedicada a Terraform nos diretórios pessoais inspecionados. `spring-data-jpa`, `spring-security-jwt` e `oauth2-resource-server` já instaladas continuam úteis para outros projetos ou decisões específicas, não como gatilhos indiscriminados aqui.

## 2. Seleção recomendada

### Prioridade 1 — duas adições oficiais

| Skill | Origem e diretório | Ganho esperado neste projeto |
|---|---|---|
| `terraform-style-guide` | `hashicorp/agent-skills` → `plugins/terraform/skills/terraform-style-guide` | Consistência HCL, entradas/saídas documentadas, validação, segurança e revisão dos módulos existentes |
| `aws-well-architected-review` | `aws/agent-toolkit-for-aws` → `skills/core-skills/aws-well-architected-review` | Revisões fundamentadas de custo, segurança, operação e resiliência; comparar demo e high-load respeitando suas finalidades |

Usar a primeira preservando versões e convenções locais. Acionar a segunda explicitamente para revisões delimitadas, começando pelo modo quick. Uma revisão não autoriza mudanças na conta AWS.

As revisões fixadas, licenças, dependências e ressalvas estão na [pesquisa Terraform/AWS](C:/Users/vinic/projetos/cielo/.specs/research/terraform-aws-skills-research.md). Fontes primárias: [HashiCorp](https://github.com/hashicorp/agent-skills/tree/516354c484b43fa5469567485113dd0c769c3d24/plugins/terraform/skills/terraform-style-guide) e [AWS](https://github.com/aws/agent-toolkit-for-aws/tree/dda61487cb4fa8b6dbc75ad4e27c2181adc9d058/skills/core-skills/aws-well-architected-review).

### Prioridade 2 — complementos Java em piloto

Ambas são comunitárias, do repositório `Jiangxianze/spring-agent-skills`, não publicações oficiais da Spring. A API pública confirmou licença Apache-2.0, repositório não arquivado e revisão `f228731681cc37e6abd83058a4d2a6311eba994f`, de 2026-08-08. O projeto declara fixtures e verificações estruturais, mas não benchmark estatisticamente significativo nem avaliação cega ampla. Isso justifica experimentação, não confiança irrestrita. [Repositório e limites declarados](https://github.com/Jiangxianze/spring-agent-skills), [revisão](https://github.com/Jiangxianze/spring-agent-skills/commit/f228731681cc37e6abd83058a4d2a6311eba994f).

**`maven-dependency-upgrader`**, diretório `skills/maven-dependency-upgrader`: inspeciona POM efetivo, dependências transitivas, parent e BOM antes de propor upgrades; verifica compatibilidade e resultado resolvido. Complementa o baseline local em alterações de Boot, AWS SDK e plugins. Seus comandos precisam usar `mvnw.cmd` no PowerShell quando apropriado. Não deve introduzir regras amplas de build em uma correção emergencial não relacionada. [Skill](https://github.com/Jiangxianze/spring-agent-skills/blob/f228731681cc37e6abd83058a4d2a6311eba994f/skills/maven-dependency-upgrader/SKILL.md), [playbook inspecionado](https://github.com/Jiangxianze/spring-agent-skills/blob/f228731681cc37e6abd83058a4d2a6311eba994f/skills/maven-dependency-upgrader/references/upgrade-playbook.md).

**`jvm-incident-triage`**, diretório `skills/jvm-incident-triage`: acrescenta investigação específica de CPU, GC, bloqueios, pools e memória a `diagnosing-bugs`. Exige correlação de evidências e cuidados com dumps. O runtime do [Dockerfile](C:/Users/vinic/projetos/cielo/Dockerfile) usa imagem JRE; não presumir disponibilidade de `jcmd` ou acesso de diagnóstico nas tasks ECS. Coleta invasiva e artefatos com dados sensíveis precisam de avaliação própria. [Skill](https://github.com/Jiangxianze/spring-agent-skills/blob/f228731681cc37e6abd83058a4d2a6311eba994f/skills/jvm-incident-triage/SKILL.md), [playbook inspecionado](https://github.com/Jiangxianze/spring-agent-skills/blob/f228731681cc37e6abd83058a4d2a6311eba994f/skills/jvm-incident-triage/references/triage-playbook.md).

### Condicionais, não instalar intactas agora

- `terraform-test` da HashiCorp: boa aderência, mas contém exemplos de CLI incompatíveis com a ajuda da versão instalada, inclusive filtro e `-no-cleanup`. Corrigir antes da adoção; manter testes com mocks separados de testes que provisionam recursos reais.
- `refactor-module` da HashiCorp: só quando houver problema concreto nos módulos. Um exemplo mistura source Git e argumento version; comandos de state e apply não são passos automaticamente autorizados.
- `aws-containers` da AWS: conteúdo ECS pertinente, mas há suspeita de front matter inválido a confirmar antes de instalação. Também requer seus diretórios de referências, não apenas SKILL.md.

Evidências e fontes específicas dessas ressalvas estão na nota Terraform/AWS vinculada acima.

## 3. Candidatas que não acrescentam o suficiente agora

| Candidata avaliada | Motivo para não priorizar |
|---|---|
| Jeffallan `java-architect` / `spring-boot-engineer` | Sobreposição extensa com a baseline local; pressupostos de JPA, microserviços e segurança não correspondem às decisões daqui. Exemplos não devem substituir contratos locais. [Java](https://github.com/Jeffallan/claude-skills/blob/main/skills/java-architect/SKILL.md), [Spring](https://github.com/Jeffallan/claude-skills/blob/main/skills/spring-boot-engineer/SKILL.md) |
| `spring-ai-community/spring-testing-skills` | O README declara foco em Boot 4 / Framework 7. Há ideias aproveitáveis, mas o pacote não é um default compatível com Boot 3.5. [Fonte](https://github.com/spring-ai-community/spring-testing-skills) |
| Amplicode `spring-explore` | Exige plugin IntelliJ e servidor Spring MCP; a detecção de persistência apresentada cobre JPA e Spring Data JDBC, não a persistência JdbcTemplate atual. Maior custo de integração do que a lacuna observada. [Fonte](https://github.com/Amplicode/spring-skills/blob/main/skills/spring-explore/SKILL.md) |
| `spring-boot-failure-analyzer` | Focada e potencialmente útil, porém cobre terreno já atendido por diagnóstico e configuração locais; reavaliar se falhas de startup se tornarem recorrentes. [Fonte](https://github.com/Jiangxianze/spring-agent-skills/blob/main/skills/spring-boot-failure-analyzer/SKILL.md) |

Não instalar pacotes completos só por popularidade: cada skill precisa de escopo, ganho incremental e compatibilidade verificáveis.

## 4. Boas práticas que merecem virar checks

Estas são propostas de engenharia futuras, não mudanças feitas nesta pesquisa. Skills orientam o agente; ferramentas e testes tornam parte das regras verificável.

1. **Arquitetura executável:** avaliar poucas regras ArchUnit para dependências do domínio, separação de adapters e ciclos entre módulos. As regras precisam refletir a arquitetura aceita, não impor um modelo genérico nem proibir Spring em toda aplicação. [ArchUnit](https://www.archunit.org/userguide/html/000_Index.html).
2. **Formatação reproduzível:** escolher um formatter via Spotless e verificar no CI. Introduzir com baseline ou escopo de arquivos alterados para evitar um diff global junto de mudança funcional. Isso automatiza estilo, não prova bom design. [Spotless Maven](https://github.com/diffplug/spotless/tree/main/plugin-maven).
3. **Revisão cloud proporcional:** classificar separadamente risco aceito na demo e requisito futuro de alta carga, com custo e evidência. Multi-AZ, EKS e serviços adicionais não são melhorias automáticas. O framework deve apoiar decisões, não revogar AD-006. [AWS review skill](https://github.com/aws/agent-toolkit-for-aws/tree/dda61487cb4fa8b6dbc75ad4e27c2181adc9d058/skills/core-skills/aws-well-architected-review).

Decisão posterior do responsável: executar ArchUnit, Spotless e a revisão AWS. Maven Enforcer, TFLint, Trivy e Checkov ficam fora deste ciclo por não justificarem o custo adicional no projeto atual.

## 5. Instalação global futura nesta máquina

“Global” significa disponível entre projetos deste usuário, não para todos os usuários Windows. Nesta sessão, as skills pessoais existentes são carregadas de `C:\Users\vinic\.codex\skills`; o instalador local também usa esse destino por padrão. A documentação atual mostra ainda `C:\Users\vinic\.agents\skills` como escopo de usuário. Não duplicar as mesmas skills nos dois locais nem migrar as existentes sem necessidade. [Documentação oficial de skills](https://learn.chatgpt.com/docs/build-skills).

As quatro skills selecionadas foram instaladas com o `skill-installer`, usando repo, diretório completo, `--ref` fixado e destino explícito. Preservar LICENSE/referências, revisar scripts e metadados antes de atualizar. Registrar eventuais correções locais para que atualizações não as apaguem. Não executar instaladores remotos opacos nem adicionar dependências ao POM da aplicação só para instalar instruções do agente.

Recomendação de ativação: estilo Terraform por escopo; revisão AWS e triagem JVM por solicitação explícita. `agents/openai.yaml` suporta `policy.allow_implicit_invocation: false` no Codex. Validar o carregamento real após a instalação: o formato SKILL.md ajuda a portabilidade, mas não garante que comandos de outro agente funcionem aqui. [Documentação oficial](https://learn.chatgpt.com/docs/build-skills).

## 6. Como decidir se vale manter

Piloto proposto, ainda não executado: comparar baseline e candidata nas mesmas tarefas pequenas — revisão de módulo Terraform, análise de upgrade Maven e diagnóstico de um incidente JVM com evidências sanitizadas. Registrar correções humanas necessárias, respeito ao escopo e comandos válidos; não usar quantidade de texto produzido como qualidade.

A candidata deve produzir recomendações rastreáveis, nenhuma violação das decisões locais e verificações reproduzíveis. Rejeitar a adoção se introduzir migrações não solicitadas, comandos inválidos ou mais correções humanas do que a baseline. Expandir o catálogo só depois de demonstrar ganho; uma coleção maior só se justifica quando mais stacks e tarefas realmente a exigirem.

## Limites da pesquisa

- Pesquisa documental e inspeção estática, não certificação de segurança das skills.
- Conteúdo externo pode mudar; revisões fixadas tornam a recomendação auditável.
- As quatro skills foram instaladas e descobertas pelo catálogo do Codex; seus workflows ainda não foram avaliados nos pilotos propostos.
- Nenhum recurso AWS foi alterado, nenhuma suíte de aplicação foi executada e nenhum commit/push foi feito.
- Criadas apenas esta nota e a pesquisa Terraform/AWS. Código e alterações locais anteriores permanecem intactos.
