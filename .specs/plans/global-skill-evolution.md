# Evolução controlada das skills globais

> Build this with **tlc-implement**.
> Every criterion below becomes a check with a proof, referenced by its number. Nothing under
> `Unresolved` gets settled while building.

## Intent

A máquina tem orientações próprias e skills de fornecedores instaladas, mas ainda não possui uma forma reproduzível de comparar alternativas, registrar procedência e provar que uma melhoria ajuda sem introduzir comandos inválidos ou conflito com decisões locais. O responsável paga com avaliações refeitas e corre o risco de editar conteúdo de fornecedor sem conseguir atualizar ou reverter.

O catálogo global passa a ter inventário, fixtures por preocupação, critérios de promoção e histórico de alterações. Skills semelhantes do mercado são comparadas antes de entrar no catálogo. Melhorias próprias preservam a origem e são revalidadas antes de substituir uma versão instalada.

8 critérios em 3 slices · 2 one-way doors · 1 open, 0 blocks

## Criteria

### Catálogo auditável

1. Quando o inventário global for gerado, então cada skill instalada registra nome, preocupação, origem, revisão ou versão, licença, trigger, status `adopted`, `pilot`, `rejected` ou `custom`, data da última avaliação e sobreposição conhecida. Metadados não comprovados ficam como `não identificado`; avaliação não realizada fica como `não avaliada`, sem data ou aprovação inventadas. Estar instalada não é evidência de promoção por avaliação.
2. Quando duas skills cobrirem a mesma preocupação, então o inventário identifica uma orientação padrão para aquele contexto, a contribuição complementar de cada especialista e a precedência em caso de conflito. A baseline Java/Spring pode coexistir com especialistas em transações, configuração, testes, Maven e JVM, mesmo com sobreposição de triggers. Duas generalistas podem ser mantidas para contextos diferentes, desde que o roteamento diga qual usar em cada um; orientações contraditórias não são aplicadas simultaneamente.
3. Se uma skill de fornecedor receber correção local, então a versão instalada preserva a origem e a revisão upstream, registra o patch local e oferece rollback para a última revisão aprovada; diretórios de fornecedor não são a fonte autoritativa da correção.

### Avaliação comparável

4. Quando uma candidata de Java/Spring, Maven, JVM, Terraform ou AWS for avaliada, então ela e a baseline executam as mesmas duas fixtures da preocupação, e o relatório registra correção técnica, respeito ao escopo, validade dos comandos, qualidade das evidências e correções humanas necessárias.
5. Se uma candidata inventar comando ou argumento, contrariar uma decisão ativa aplicável ao cenário, ou exigir mais correções críticas que a baseline nas mesmas fixtures, então a revisão avaliada é `rejected` e não substitui a skill adotada. Uma ferramenta documentada, mas ausente na máquina, ou uma falha de rede é limitação do ambiente, não comando inexistente: a execução fica `inconclusiva`, sem promoção. Uma revisão corrigida pode ser avaliada novamente, preservando o resultado anterior.
6. Quando uma candidata completar todas as fixtures sem comando inválido nem conflito com decisão ativa e não exigir mais correções críticas que a baseline, então seu status pode mudar de `pilot` para `adopted`, com a evidência vinculada no inventário. Critérios não observados permanecem `não avaliados`; ausência de revisão humana não equivale a zero correções humanas.

### Melhoria com regressão controlada

7. Quando uma skill própria ou um patch local mudar, então todas as fixtures retidas para sua preocupação são reexecutadas e o relatório compara o resultado com a última versão aprovada antes da instalação global.
8. Se uma fixture antes aprovada falhar após a mudança, então a versão global permanece na última revisão aprovada e a candidata registra a regressão sem substituir o diretório ativo.

## Out of scope

- Atualização automática de skills ou execução agendada sem revisão humana - frequência permanece aberta em `Unresolved 1`.
- Instalar pacotes completos porque pertencem a um fornecedor conhecido - cada diretório é avaliado pela preocupação que cobre.
- Editar diretamente uma skill de fornecedor e perder a referência upstream - patches precisam de procedência e rollback.
- Usar popularidade, número de estrelas ou volume de texto como critério de promoção - a comparação usa fixtures e correções observadas.
- Criar uma skill genérica que substitua `tlc-*`, regras locais ou skills especializadas - precedência e escopo permanecem explícitos.
- Alterar código, infraestrutura ou conta AWS durante uma avaliação - fixtures devem ser read-only ou operar em scratch descartável.

## Observable

| Surface | Decision | Landing |
| --- | --- | --- |
| documento de inventário | estrutura: procedência, trigger, status, avaliação e sobreposição | 1 |
| documento de inventário | tom: fatos observados separados de inferências e recomendações | 1, 4 |
| documento de inventário | próximo passo: manter, rejeitar, testar ou substituir | 5, 6 |
| coleção de skills | agrupamento: Java/Spring, Maven/JVM, Terraform e AWS | 1, 4 |
| coleção de skills | naming: nome publicado pelo fornecedor; nome novo somente para fork ou skill própria | 1, 3 |
| coleção de skills | ordering: `adopted` antes de `pilot`, depois `rejected` por preocupação | 1 |
| coleção de skills | duplicates: uma orientação padrão por contexto; alternativas com roteamento explícito | 2 |
| coleção de skills | complementaridade: sobreposição de trigger permitida; responsabilidade e precedência documentadas | 2 |
| relatório de avaliação | saída: baseline e candidata lado a lado nas mesmas duas fixtures | 4 |
| relatório de avaliação | falha parcial: nenhuma promoção quando falta resultado de uma fixture | 4, 5, 6 |

## Swept

- validation: 4, 5, 6, 7, 8
- failure modes: 3, 5, 8
- idempotency and retry: 7, 8
- authorization: existing - escrita em `C:\Users\vinic\.codex\skills` continua exigindo autorização explícita fora do repositório de trabalho
- concurrency and ordering: n/a - avaliações são executadas uma candidata por vez contra a mesma baseline
- data lifecycle: 1, 3, 8
- external-dependency failure: 5 - comando inventado reprova a revisão; indisponibilidade do ambiente deixa a avaliação inconclusiva e não permite promoção
- state transitions: 5, 6, 8
- observability: 1, 4, 7

## Impact

| Front | What changes |
|---|---|
| catálogo global | skills deixam de ser apenas diretórios instalados e passam a ter procedência, status e evidência de avaliação |
| guidance Java/Spring | `C:\Users\vinic\java-spring-agent-guidance` é uma referência existente; confirmar a relação com a skill instalada antes de tratá-la como fonte autoritativa de patches |
| skills de fornecedor | versões instaladas permanecem reproduzíveis por repo, path e revisão; correções locais deixam de ser alterações sem origem |
| projetos consumidores | regras locais e `.specs/STATE.md` continuam com precedência sobre orientação global |
| stored data | nothing to migrate |

## Decided

| Decision | Shape | Alternative rejected |
|---|---|---|
| A promoção é baseada em avaliação comparável | duas fixtures retidas por preocupação; baseline e candidata recebem a mesma entrada; cinco dimensões registradas | popularidade e leitura estática não provam comportamento em uso |
| Conteúdo de fornecedor permanece rastreável | repo + path + revisão upstream + patch local separado + rollback para última revisão aprovada | editar `C:\Users\vinic\.codex\skills\<nome>` como fonte destrói procedência e dificulta atualização |

## Implementation guidance

- A avaliação compara tarefas concretas, não uma contagem de regras. Usar os mesmos cenários, versões, permissões e entradas para baseline e candidata, registrando limitações do ambiente.
- Classificar como crítica uma correção necessária para impedir alteração de contrato, violação de decisão aplicável, perda de dados, exposição de segredo, execução não autorizada ou conclusão técnica incorreta. Preferência de estilo e redundância textual não são falhas críticas.
- O erro técnico da própria skill e a indisponibilidade de Docker, credencial, rede ou ferramenta externa têm resultados diferentes. Não executar ação remota para transformar uma avaliação inconclusiva em aprovada.
- Avaliar compatibilidade com as decisões do projeto consumidor. As decisões específicas do Cielo não se tornam proibições universais em skills globais; por exemplo, JDBC neste projeto não torna JPA inválido em outro projeto.
- Reaproveitar a baseline Java/Spring como orientação transversal. Acionar especialistas somente quando a tarefa tocar sua responsabilidade. Não ativar todas as skills apenas porque estão instaladas.
- Manter a versão global ativa durante a avaliação de candidatas em scratch. Correções pontuais podem ser reapresentadas sem banir permanentemente o fornecedor ou instalar silenciosamente a versão corrigida.

## Sources

- `.specs/research/global-engineering-skills.md` - define seleção contextual, piloto comparável e critérios de rejeição.
- `.specs/research/terraform-aws-skills-research.md` - registra procedência, revisões fixadas e erros encontrados até em skills oficiais.
- `C:\Users\vinic\java-spring-agent-guidance\AGENTS.md` - estabelece a orientação Java/Spring existente e sua manutenção por fonte única.
- `C:\Users\vinic\java-spring-agent-guidance\RESEARCH.md` - registra a base primária e os julgamentos de engenharia da orientação existente.
- conversa de 2026-09-27 - pede pesquisa contínua de skills semelhantes e melhoria das já existentes.
- conversa de 2026-09-27, revisão autorizada: "aplique as suas orientações para não serem tão restristivas". User delegated: permitir complementaridade e tornar a avaliação proporcional, sem eliminar rastreabilidade ou provas de regressão.

Este plano registra a decisão dentro de `.specs/`, conforme AD-022. Uma divergência factual deve ser corrigida com evidência; nova capacidade ou conflito com uma decisão ativa exige decisão explícita antes de construir.

## Unresolved

| # | Kind | Question | Until answered |
|---|---|---|---|
| 1 | open | Com qual frequência o radar de mercado deve rodar? | Default: sob demanda, quando uma nova stack entra no projeto ou uma skill adotada falha numa tarefa real; nenhuma automação recorrente será criada sem confirmação. |
