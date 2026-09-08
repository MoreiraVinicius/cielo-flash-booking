# Pesquisa: skills para modelagem relacional com PostgreSQL

Este levantamento não instalou nem executou nenhuma skill.

## Conclusão

A melhor candidata encontrada é a skill oficial do repositório da Timescale/TigerData, [`design-postgres-tables`](https://github.com/timescale/pg-aiguide/tree/main/skills/design-postgres-tables), ou o roteador mais amplo [`postgres`](https://github.com/timescale/pg-aiguide/tree/main/skills/postgres). Ela é específica para PostgreSQL, tem licença Apache-2.0 e cobre o fluxo que mais influencia a qualidade do modelo: entidades/tabelas, normalização, tipos, constraints, relacionamentos, índices, JSONB e evolução de schema.

A skill deve ser uma camada de procedimento e revisão, não a fonte normativa. Decisões dependentes de versão, como recursos de particionamento, precisam ser validadas na [documentação da versão de PostgreSQL em uso](https://www.postgresql.org/docs/current/).

## Skills encontradas

| Opção | Evidência primária e escopo | Avaliação |
| --- | --- | --- |
| [`timescale/pg-aiguide` — `design-postgres-tables`](https://github.com/timescale/pg-aiguide/tree/main/skills/design-postgres-tables) | O [`SKILL.md`](https://raw.githubusercontent.com/timescale/pg-aiguide/main/skills/design-postgres-tables/SKILL.md) declara gatilhos para modelar tabelas/schemas PostgreSQL e escolher tipos, constraints e índices; inclui normalização, chaves, FKs, JSONB e particionamento. O arquivo declara licença Apache-2.0 e autoria `tigerdata`. | **Recomendada.** É a mais direta para desenho ou revisão de modelos relacionais em Postgres. O roteador [`postgres`](https://raw.githubusercontent.com/timescale/pg-aiguide/main/skills/postgres/SKILL.md) agrega a skill de modelagem a referências para migrações, extensões, busca e séries temporais. |
| [`timescale/pg-aiguide` — `postgres-database-migration`](https://github.com/timescale/pg-aiguide/tree/main/skills/postgres-database-migration) | Skill complementar de evolução segura: locks de DDL, índices concorrentes, validação de constraints, backfill e rollback. | **Usar junto** quando o modelo será aplicado a uma base existente ou de grande porte; não substitui a skill de modelagem. |
| [`wshobson/agents` — `postgresql-table-design`](https://github.com/wshobson/agents/tree/main/plugins/database-design/skills/postgresql-table-design) | O [`SKILL.md`](https://raw.githubusercontent.com/wshobson/agents/main/plugins/database-design/skills/postgresql-table-design/SKILL.md) cobre schema/revisão PostgreSQL, 3FN, tipos, constraints, índices, particionamento e JSONB. O [README do marketplace](https://raw.githubusercontent.com/wshobson/agents/main/README.md) declara suporte a Codex e distribuição de skills via instaladores Agent Skills; o repositório usa MIT. | **Alternativa direta, de terceiro.** O escopo é muito bom e o formato é instalável, mas não é mantida pelo projeto PostgreSQL nem pela OpenAI. Fixar um commit e auditar o conteúdo antes de adotar. |
| [`anthropics/knowledge-work-plugins` — `explore-data`](https://github.com/anthropics/knowledge-work-plugins/tree/main/data/skills/explore-data) | A [skill oficial](https://raw.githubusercontent.com/anthropics/knowledge-work-plugins/main/data/skills/explore-data/SKILL.md) perfila esquema e dados existentes: grain, PK, nulos, cardinalidade, chaves naturais duplicadas, candidatos a FK, integridade referencial e redundâncias. | **Complementar para revisão.** Não desenha um novo modelo PostgreSQL, mas é valiosa para descobrir falhas e documentar uma base já existente antes da remodelagem. |
| [`anthropics/knowledge-work-plugins` — `data-context-extractor`](https://github.com/anthropics/knowledge-work-plugins/tree/main/data/skills/data-context-extractor) | A [skill oficial](https://raw.githubusercontent.com/anthropics/knowledge-work-plugins/main/data/skills/data-context-extractor/SKILL.md) orienta descoberta de entidades, relações 1:1/1:N/N:N, IDs que fazem vínculos e a distinção entre PK e business keys. | **Complementar para descoberta de domínio.** Foi feita para criar contexto analítico interno, não para emitir DDL PostgreSQL. |
| [`borghei/Claude-Skills` — `database-schema-designer`](https://github.com/borghei/Claude-Skills/tree/main/engineering/database-schema-designer) | O [`SKILL.md`](https://raw.githubusercontent.com/borghei/Claude-Skills/main/engineering/database-schema-designer/SKILL.md) cobre requisitos até 3FN, ERD, RLS, índices e migrações para PostgreSQL, MySQL e SQLite. | **Alternativa ampla, não padrão.** É útil para geração de artefatos, porém declara `MIT + Commons Clause`; revisar a compatibilidade de licença antes de copiar ou distribuir. |
| [`prisma/skills` — `prisma-postgres-setup`](https://github.com/prisma/skills/tree/main/prisma-postgres-setup) | O [`SKILL.md`](https://github.com/prisma/skills/blob/main/prisma-postgres-setup/SKILL.md) é para provisionar e conectar Prisma Postgres. | **Não indicada para modelagem.** Serve a setup/conexão; explicitamente não é para tarefas de schema/migration em banco já conectado. |

Não identifiquei, nas fontes oficiais/curadas verificadas da OpenAI, uma skill pública e específica para modelagem relacional em PostgreSQL. A skill oficial [`skill-creator`](https://github.com/openai/skills/tree/main/skills/.system/skill-creator) é relevante como critério para criar uma skill interna enxuta, mas não substitui uma expertise de banco pronta.

## Critérios de qualidade para selecionar ou criar uma skill

Priorizar uma skill que:

1. peça requisitos e declare suposições antes de escrever DDL: entidades, cardinalidades, invariantes, ciclo de vida, volume, consultas críticas, retenção e fronteiras de tenant;
2. transforme regras de negócio em invariantes no banco com `PRIMARY KEY`, `NOT NULL`, `UNIQUE`, `CHECK`, `FOREIGN KEY` e, quando adequado, `EXCLUDE`, em vez de depender só da aplicação;
3. modele muitos-para-muitos por tabela associativa e trate ações referenciais (`ON DELETE`/`ON UPDATE`) como decisão explícita;
4. escolha tipos pelo significado e pela precisão: por exemplo, `numeric` para valores exatos e `timestamptz` quando o instante e fuso importam;
5. derive índices de consultas, junções e ações referenciais reais — chaves primárias e unicidade já ganham índice, mas FKs do lado referenciante não são indexadas automaticamente;
6. só proponha desnormalização após identificar a leitura, custo e trade-off mensuráveis; mantenha o desenho normalizado como padrão inicial;
7. inclua plano de migração, validação e rollback, com estratégia de baixo bloqueio para tabelas em produção;
8. mantenha instruções curtas e referências carregadas sob demanda. A [orientação oficial de criação de skills da OpenAI](https://raw.githubusercontent.com/openai/skills/main/skills/.system/skill-creator/SKILL.md) recomenda esse desenho, com `SKILL.md` conciso e referências separadas.

## Base técnica para auditar as recomendações

A documentação oficial do PostgreSQL sustenta os pontos essenciais acima:

- [Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html): a maior parte das colunas de um modelo deve ser `NOT NULL`; PK implica unicidade e não nulidade; FKs mantêm integridade referencial; para exclusão/atualização no lado pai, frequentemente convém indexar as colunas referenciantes, pois PostgreSQL não cria esse índice automaticamente.
- [CREATE TABLE](https://www.postgresql.org/docs/current/sql-createtable.html): FKs apontam para PK, constraint `UNIQUE` ou índice único não parcial; as ações referenciais e a possibilidade de `DEFERRABLE` são parte do contrato do modelo.
- [Tipos de dados](https://www.postgresql.org/docs/current/datatype.html): PostgreSQL oferece tipos nativos ricos; a escolha deve obedecer ao domínio, não apenas à conveniência da aplicação.
- [Identity columns](https://www.postgresql.org/docs/current/ddl-identity-columns.html): `GENERATED ... AS IDENTITY` fornece valores automáticos para chaves quando essa estratégia for apropriada.
- [Particionamento](https://www.postgresql.org/docs/current/ddl-partitioning.html): só deve entrar no modelo por necessidade de acesso/manutenção; PK/`UNIQUE` de tabela particionada precisam incluir a chave de particionamento. A matriz oficial confirma que [uma FK pode referenciar tabela particionada](https://www.postgresql.org/about/featurematrix/detail/foreign-key-references-for-partitioned-tables/), portanto recomendações em skills que afirmem o contrário estão defasadas.

## Recomendação prática

Para projetos novos ou revisões: avaliar a instalação da skill `design-postgres-tables` da Timescale/TigerData, fixando uma revisão do repositório e mantendo a documentação oficial do PostgreSQL como gate de revisão. Para produção, complementar com `postgres-database-migration` e exigir que toda alteração de DDL declare versão-alvo, impacto de lock, índice de FK, dados existentes, validação e rollback.
