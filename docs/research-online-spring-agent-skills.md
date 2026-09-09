# Skills públicas para Spring Boot, Data e Security

Pesquisa em 9 de setembro de 2026. O objetivo é selecionar instruções reutilizáveis para agentes (arquivos `SKILL.md`), não bibliotecas Java. Estrelas e forks são apenas sinais públicos de adoção — não são uma avaliação de correção ou segurança. Antes de instalar qualquer pacote, leia seu `SKILL.md`, fixe uma revisão e execute a validação do projeto.

O projeto atual usa Spring Boot **3.5.0** e Java 21; portanto, quando houver variantes, a escolha deve ser a linha Boot 3.

## Recomendadas — essenciais

| Coleção / skills a selecionar | Cobertura e por que entra | Indicadores públicos verificados |
| --- | --- | --- |
| [rrezartprebreza/spring-boot-skills](https://github.com/rrezartprebreza/spring-boot-skills) — `spring-data-jpa`, `spring-security-jwt`, `oauth2-resource-server`, `transactional-patterns`, `testing-pyramid` da árvore `spring-boot-3` | É a seleção mais completa e diretamente aplicável: JPA (modelagem, relações, N+1, projeções, paginação e escrita em lote), transações, JWT/RBAC e OAuth2 resource server. O repositório separa explicitamente Boot 3/Security 6 de Boot 4/Security 7, documenta suporte a Codex e declara que as integrações rápidas foram verificadas em agosto de 2026. | 183 estrelas e 35 forks no snapshot pesquisado; 13 commits. A compatibilidade explícita com Boot 3 + Java 17+ reduz o risco de copiar APIs de Boot 4 para este projeto. [Catálogo e versões](https://github.com/rrezartprebreza/spring-boot-skills#-skills); [dados, security e testes](https://github.com/rrezartprebreza/spring-boot-skills#-data--persistence). |
| [Amplicode/spring-skills](https://github.com/Amplicode/spring-skills) — `spring-explore` e `spring-data-jpa` | Boa escolha para explorar a aplicação antes de alterar entidades/repositórios e para respeitar convenções locais em JPA, projeções e transações. O projeto é mantido pelas equipes Amplicode e Spring AIO e possui manifestos de plugin, inclusive para Codex. | 45 commits e 12 forks no snapshot; o catálogo marca `spring-explore` e `spring-data-jpa` como **Ready**. Não o usar como fonte principal de Security ainda: `spring-security-configuration` está marcado **In progress**. [Manutenção, objetivo e integração](https://github.com/Amplicode/spring-skills#amplicode-spring-skills); [status de cada skill](https://github.com/Amplicode/spring-skills#whats-inside). |
| [sivaprasadreddy/sivalabs-agent-skills — `spring-boot`](https://github.com/sivaprasadreddy/sivalabs-agent-skills) | É a escolha equilibrada quando a equipe quer **uma** skill transversal de Boot que cubra MVC, JPA, Security, Modulith e testes, sem combinar vários pacotes. | 180 estrelas, 41 forks e 37 commits no snapshot; licença MIT e instruções explícitas para Codex. O catálogo público não separa Data/Security em skills próprias, portanto ela é abrangente, não tão especializada. [Conteúdo e instalação](https://github.com/sivaprasadreddy/sivalabs-agent-skills#sivalabs-skills-for-ai-coding-agents). |

**Combinação indicada para este repositório:** usar a árvore `spring-boot-3` da primeira coleção para Data/Security e, se a equipe adotar a integração IDE/MCP do Amplicode, acrescentar somente `spring-explore` e `spring-data-jpa`. Não instalar dois conjuntos genéricos concorrentes de JPA/Security sem definir precedência: instruções conflitantes degradam a consistência do agente.

## Recomendadas — opcionais, por cenário

| Coleção / skill | Quando agrega valor | Evidência e ressalva |
| --- | --- | --- |
| [Jiangxianze/spring-agent-skills — `spring-boot-failure-analyzer`, `jpa-query-reviewer`, `flyway-safe-migration`](https://github.com/Jiangxianze/spring-agent-skills) | Para investigar falhas de inicialização, revisar N+1/paginação/fronteiras transacionais e avaliar migrations de produção. | Diferencia-se por fixtures Maven defeituros, validação estrutural e rubricas de avaliação; a skill de JPA usa Hibernate Statistics como evidência. Também declara que a ativação não executa rede nem altera o projeto. É promissora, mas a própria roadmap diz que a primeira versão pública `v0.1.0` ainda depende de testes cegos em projetos distintos; adote seletivamente e revise o conteúdo. |
| [full-stack-skills/spring-skills — `spring-boot`, `spring-data-jpa`, `spring-security`](https://github.com/full-stack-skills/spring-skills) | Alternativa simples, padronizada no formato Agent Skills, quando se precisa de um trio mínimo pronto para vários agentes. | Cobre exatamente Boot, Data JPA e Security (incluindo autenticação, autorização, OAuth2 e JWT) e cita Codex como compatível. Contudo, o snapshot mostra só 2 estrelas, 3 forks e 10 commits; a manutenção/adoção pública ainda é pequena. Não é a primeira escolha para um backend sensível. |
| [giuseppe-trisciuoglio/developer-kit — `spring-data-jpa`, `spring-boot-security-jwt` e `spring-boot-test-patterns`](https://github.com/giuseppe-trisciuoglio/developer-kit) | Para priorizar o sinal público de instalações e obter uma biblioteca grande de padrões Java/Spring (JPA, JWT, testes, Actuator, cache e resiliência). | O projeto declara suporte a Codex CLI, 51 skills Java/Spring e validação própria; o diretório skills.sh registra cerca de 2,6 mil instalações individuais para JPA e JWT, em um pacote de 130 skills/269,9 mil instalações. Use seletivamente: é um bundle generalista, e telemetria de instalação não demonstra qualidade por tarefa. Revise cada `SKILL.md` e o resultado de auditoria antes de habilitar Security. [Pacote e instalações](https://www.skills.sh/giuseppe-trisciuoglio/developer-kit); [suporte e validação](https://github.com/giuseppe-trisciuoglio/developer-kit#validation--quality). |

## Itens que não recomendo como base hoje

[rynr/spring-skills](https://github.com/rynr/spring-skills) tem um catálogo amplo (13 skills, incluindo Boot, Data JPA e Security) e boa estrutura documental, mas o próprio repositório informa que é documentação, sem build, testes ou código executável; no snapshot não tinha forks. Pode servir de referência de conteúdo, não de baseline de equipe sem uma revisão manual mais profunda.

## Critério de decisão e segurança operacional

1. Prefira skills com variante compatível com a versão efetiva do framework; para este código, Boot 3/Security 6, não a árvore Boot 4.
2. Instale somente skills pontuais no escopo do repositório e fixe a revisão/commit após revisar os arquivos. Um `SKILL.md` influencia um agente com as permissões do usuário.
3. Use a skill como checklist de implementação; a fonte normativa continua sendo a documentação e os testes oficiais de [Spring Boot](https://docs.spring.io/spring-boot/) e [Spring Security](https://docs.spring.io/spring-security/). Valide migrations, autorização e caminhos de erro por testes no projeto.
4. Meça valor em tarefas reais: compare PRs com e sem a skill (N+1, limites transacionais, negação por padrão, testes) antes de padronizá-la globalmente.

Nenhuma skill foi instalada ou alterou a configuração do projeto nesta pesquisa.
