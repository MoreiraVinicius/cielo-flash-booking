# README Visual Storytelling Specification

## Problem Statement

O repositório já contém uma implementação validada, decisões arquiteturais e evidências de execução, mas o README ainda apresenta a solução como trabalho futuro e empilha diagramas extensos antes de explicar o produto. Um avaliador precisa distinguir rapidamente o que foi construído, o que foi medido e o que permanece como arquitetura-alvo.

## Goals

- [ ] Explicar o produto, a regra de não vender além da capacidade e os cinco endpoints em leitura rápida.
- [ ] Separar visualmente a demo validada da arquitetura high-load planejada.
- [ ] Tornar rastreáveis o desenvolvimento orientado por especificação, os testes e o baseline de carga.
- [ ] Organizar resiliência, segurança e observabilidade sem alegações acima da evidência disponível.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Alterar regras de negócio ou contratos HTTP | A feature é documental e de evidência. |
| Implementar compra, pagamento ou emissão de ingresso | O domínio entregue termina na reserva temporária. |
| Provisionar ou testar a arquitetura high-load na AWS | AD-006 mantém essa topologia como alvo não aplicado. |
| Reexecutar a demo na AWS | A validação remota existente é suficiente para esta revisão. |
| Usar a marca oficial da Cielo como identidade do projeto | A arte deve ser original e indicar que este é um case técnico independente. |

## Assumptions & Open Questions

| Tema | Decisão | Justificativa | Confirmada? |
| --- | --- | --- | --- |
| Idioma | Português, preservando nomes técnicos conhecidos | É o idioma usado pelo projeto e pelo avaliador. | yes |
| Leitura | Resumo progressivo antes dos detalhes | Reduz carga cognitiva e mantém profundidade disponível. | yes |
| Identidade | Símbolo original de ingresso, raio e tempo | Comunica flash sale sem reproduzir logotipo corporativo. | yes |
| Fonte da demo | `.specs/features/flash-booking-demo/validation.md` | É a evidência independente consolidada com 43/43 critérios. | yes |
| Fonte de alta carga | Spec e design high-load | Não há ambiente high-load aplicado nem validação remota. | yes |
| Carga | Baseline local versionado, identificado em `req/s` | Evita apresentar um snapshot local como capacidade de produção. | yes |
| Diagramas existentes | Permanecem como aprofundamento recolhível | As vistas C4 e de sequência são úteis, mas não pertencem ao primeiro minuto de leitura. | yes |

**Open questions:** none. O usuário aprovou a aplicação do plano editorial e visual, incluindo o commit das mudanças.

## User Stories

### P1: Entender o produto rapidamente

**História:** Como avaliador técnico, quero entender em poucos minutos o problema, o limite do domínio e como executar a solução.

**Acceptance Criteria:**

1. WHEN a reviewer opens `README.md` THEN the repository SHALL identify Flash Booking as an independent Cielo backend case for temporary ticket reservations and SHALL state that payment and purchase confirmation are outside the implemented domain.
2. WHEN a reviewer looks for the public contract THEN `README.md` SHALL list exactly the five case endpoints with their purpose and SHALL provide a local quick-start path for Docker Compose.
3. WHEN a reviewer follows the primary narrative THEN the README SHALL present compact product and architecture visuals before placing the detailed C4 and sequence diagrams inside optional deep-dive sections.

**Teste independente:** Ler somente o resumo, o contrato HTTP e o quick start e explicar o produto sem abrir outro documento.

### P1: Distinguir entrega comprovada de evolução planejada

**História:** Como arquiteto, quero saber o que foi validado e o que é apenas arquitetura-alvo para não confundir intenção com evidência.

**Acceptance Criteria:**

1. WHEN the README presents delivery status THEN it SHALL label the demo as independently validated with `43/43` acceptance criteria and `94` automated tests passing, and SHALL label high-load as planned, not provisioned, and not remotely validated.
2. WHEN the README compares demo and high-load THEN it SHALL preserve the shared Java domain and explicitly distinguish Single-AZ economical runtime from the Multi-AZ target topology.
3. WHEN resilience, security, or observability is described THEN each pattern SHALL be paired with its operational signal or evidence source, and the text SHALL not claim deterministic throttling, high-load failover, or production capacity.

**Teste independente:** Conferir cada número e estado do resumo contra as specs, ADRs e a validação da demo.

### P1: Avaliar método e desempenho com evidência rastreável

**História:** Como entrevistador, quero enxergar como a solução foi especificada, verificada e medida.

**Acceptance Criteria:**

1. WHEN the README explains development workflow THEN it SHALL show the trace `case -> spec -> design/ADRs -> tasks -> tests/gates -> independent verifier` and SHALL distinguish the completed demo lane from the planned high-load lane.
2. IF local load-test metrics are published THEN the repository SHALL use `req/s`, latency percentiles, failure rate, scenario size, provenance, and a local-only disclaimer; the command workload SHALL address at least two explicit command-api processes rather than an unexposed replica set.
3. WHEN documentation validation runs THEN it SHALL fail for missing README assets, invalid SVG XML, absent truth labels, a non-five-endpoint contract, or a malformed performance evidence file.

**Teste independente:** Executar o validador documental e inspecionar o relatório de carga versionado e o gráfico derivado.

## Edge Cases

- SE Docker não estiver disponível para uma nova execução, ENTÃO nenhum número novo DEVE ser alegado; o visual DEVE usar somente o baseline local já versionado e registrar sua proveniência.
- SE um SVG for aberto sem fontes externas, ENTÃO textos, contraste e hierarquia DEVEM continuar legíveis.
- SE um link apontar para um recurso opcional ou ignorado pelo Git, ENTÃO a validação DEVE falhar.
- SE uma métrica misturar leitura e escrita, ENTÃO o README DEVE identificá-la como cenário agregado, não como latência exclusiva de reservas.
- SE uma proteção AWS tiver semântica de melhor esforço, ENTÃO ela NÃO DEVE ser descrita como limite determinístico.

## Requirement Traceability

| ID | História | Fase | Estado |
| --- | --- | --- | --- |
| README-01 | Entender o produto rapidamente | Specify | Approved |
| README-02 | Distinguir entrega e evolução | Specify | Approved |
| README-03 | Avaliar método e desempenho | Specify | Approved |

**Cobertura:** 3 requisitos, 9 critérios de aceitação, nenhum sem mapeamento.

## Success Criteria

- [ ] O primeiro bloco do README responde o que é, o que foi entregue e qual é o limite do domínio.
- [ ] A demo e a arquitetura high-load não podem ser confundidas visualmente ou textualmente.
- [ ] Todas as imagens referenciadas existem, são SVGs válidos quando aplicável e têm descrição acessível.
- [ ] O baseline publicado é local, reproduzível e não usa `TPS` como sinônimo de requisições HTTP por segundo.
- [ ] O validador documental e os gates do projeto passam.

