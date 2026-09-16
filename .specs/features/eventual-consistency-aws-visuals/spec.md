# AWS Eventual Consistency Visuals Specification

## Problem Statement

O case original exige consistência eventual para disponibilidade. O README precisa separar visualmente a consistência forte do inventário, a projeção de leitura em cache e os efeitos assíncronos. As vistas AWS devem manter nomes, rotas e fronteiras de rede precisos, com texto nativo legível e somente assets utilizados.

## Goals

- [x] Explicar onde existe consistência forte e onde existe convergência eventual.
- [x] Mostrar as peças AWS efetivamente usadas pela demo sem confundir cache ou fila com fonte de verdade.
- [x] Separar a topologia demo validada da arquitetura high-load planejada.
- [x] Substituir os diagramas raster antigos por SVGs legíveis e remover assets sem uso.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Alterar comportamento Java, banco ou infraestrutura | A feature documenta a versão 1 já implementada. |
| Reaplicar a demo AWS | A execução remota anterior já foi validada e destruída. |
| Implementar a arquitetura high-load | AD-006 mantém essa topologia como alvo não provisionado. |
| Prometer entrega exatamente uma vez | SQS entrega ao menos uma vez; consumidores precisam ser idempotentes. |
| Tratar Valkey como autoridade do estoque | PostgreSQL continua sendo a única fonte de verdade. |

## Assumptions & Open Questions

| Tema | Decisão | Justificativa | Confirmada? |
| --- | --- | --- | --- |
| Fonte funcional | Case, spec da demo, código Java e Terraform atual | A imagem deve explicar a implementação, não uma arquitetura imaginada. | yes |
| Disponibilidade eventual | Somente `GET /events/{id}` usa Valkey | A consulta de reserva lê PostgreSQL diretamente. | yes |
| Limite do cache | Invalidação pós-commit best effort e TTL máximo de 1 segundo | Falha de invalidação não autoriza estoque e converge pelo TTL. | yes |
| Efeitos assíncronos | Outbox, SQS, consumidores e SES depois do commit | A resposta `201` não depende de mensageria ou e-mail. | yes |
| Formato | SVG autossuficiente com texto nativo e setas ortogonais | Evita distorção e permite revisão do conteúdo. | yes |
| Limpeza | Excluir apenas quatro assets marcados como obsoletos pelo gate atual | Todos têm zero uso no README e permanecem recuperáveis pelo Git. | yes |

**Open questions:** none. O pedido define a explicação, as vistas AWS e a remoção dos assets sem uso.

## User Stories

### P1: Entender a fronteira de consistência

**História:** Como avaliador técnico, quero distinguir a transação autoritativa das projeções e efeitos eventuais para verificar que o sistema atende ao case sem permitir oversell.

**Acceptance Criteria:**

1. WHEN the consistency visual presents reservation creation THEN it SHALL show `POST /events/{id}/reservations` and PostgreSQL committing inventory decrement, customer, `PENDING` reservation, and both outbox events in one transaction before the API returns `201`.
2. WHEN the visual presents availability reads THEN it SHALL show that only `GET /events/{id}` uses Valkey cache-aside, the Query API reads PostgreSQL on a miss, and `GET /reservations/{id}` reads PostgreSQL directly.
3. WHEN a command changes event availability THEN the visual SHALL show best-effort cache invalidation after commit and a maximum one-second TTL as the convergence bound; it SHALL state that cache never authorizes inventory.

**Teste independente:** Ler somente o diagrama e explicar por que uma disponibilidade temporariamente defasada não produz oversell.

### P1: Entender os efeitos assíncronos

**História:** Como arquiteto, quero acompanhar o outbox até expiração e notificação para avaliar recuperação, duplicidade e prazo de convergência.

**Acceptance Criteria:**

1. WHEN a committed outbox event is pending THEN the worker SHALL be shown polling PostgreSQL and publishing `ReservationExpirationScheduled` or `ReservationCreated` to separate SQS queues; the worker consumers SHALL perform expiration or request SES delivery, rather than SQS directly invoking SES.
2. WHEN SQS redelivers or processing fails THEN the visual SHALL show idempotent or conditional consumers, bounded redrive with `maxReceiveCount = 5`, and a DLQ for each flow.
3. WHEN the system is healthy THEN the visual SHALL distinguish expiration convergence by `expiresAt + 5s` from SES delivery request within 30 seconds; asynchronous failure SHALL NOT be shown rolling back the persisted reservation or its HTTP response.

**Teste independente:** Seguir os dois eventos desde a transação até SQS, SES, expiração e DLQ.

### P1: Comparar as escolhas AWS

**História:** Como revisor de arquitetura, quero ver a demo executada e o alvo high-load em vistas separadas, legíveis e honestas.

**Acceptance Criteria:**

1. WHEN the demo topology is shown THEN it SHALL include API Gateway REST, WAF, VPC Link v2, internal ALB, three ECS Fargate services, RDS PostgreSQL Single-AZ, single-node Valkey, two SQS queues with DLQs, SES, CloudWatch, Secrets Manager, one NAT Gateway, and the status applied, validated, and destroyed; regional managed services SHALL NOT be presented as resources inside the VPC.
2. WHEN the high-load topology is shown THEN it SHALL label the view as planned and not provisioned, preserve the three Java modes, and show Multi-AZ ECS, Aurora PostgreSQL with RDS Proxy, Multi-AZ Valkey, independent scaling, and separate asynchronous queues without claiming measured capacity or failover.
3. WHEN the README is validated THEN all three new SVGs SHALL exist, parse as standalone XML, contain non-empty accessible descriptions, render without clipped text, and use routed arrows that do not cross labels or nodes.
4. WHEN unused image cleanup completes THEN `flash-booking-aws-demo-v4.png`, `flash-booking-aws-demo-v5.png`, `flash-booking-aws-high-load-v1.png`, and `flash-booking-aws-high-load-v2.svg` SHALL be absent while every remaining file in `docs/images` is referenced by repository documentation.

**Teste independente:** Abrir as três imagens em 1600 px, conferir seus rótulos contra código/Terraform e executar o gate documental.

## Edge Cases

- SE a invalidação do Valkey falhar, ENTÃO o diagrama DEVE mostrar convergência pelo TTL e fallback para PostgreSQL, não perda de estoque.
- SE a publicação no SQS falhar, ENTÃO o evento DEVE permanecer pendente no outbox para nova tentativa.
- SE SQS entregar uma mensagem duplicada, ENTÃO o diagrama DEVE mostrar transição condicional ou deduplicação antes do efeito.
- SE a fila de expiração atrasar, ENTÃO o reconciliador DEVE aparecer como caminho de recuperação pelo relógio do banco.
- SE a topologia high-load for exibida, ENTÃO ela DEVE permanecer marcada como hipótese não provisionada.
- SE uma imagem estiver citada apenas em uma lista de assets obsoletos do validador, ENTÃO essa citação NÃO conta como uso documental.
- SE o cache não contiver o evento, ENTÃO a seta de fallback DEVE partir da Query API, não sugerir que Valkey consulta PostgreSQL.
- SE uma notificação for consumida, ENTÃO o worker DEVE solicitar o envio ao SES; a fila NÃO envia e-mail diretamente.

## Requirement Traceability

| ID | História | Fase | Estado |
| --- | --- | --- | --- |
| AWSVIS-01 | Entender a fronteira de consistência | Verified | Independent verifier approved all acceptance criteria |
| AWSVIS-02 | Entender os efeitos assíncronos | Verified | Independent verifier approved all acceptance criteria |
| AWSVIS-03 | Comparar as escolhas AWS | Verified | Independent verifier approved all acceptance criteria |

**Cobertura:** 3 requisitos, 10 critérios de aceitação, nenhum sem mapeamento.

## Success Criteria

- [x] Um leitor distingue estoque forte, disponibilidade eventual e efeitos assíncronos sem consultar outro documento.
- [x] As peças AWS da demo correspondem aos recursos existentes no Terraform.
- [x] A arquitetura high-load não é apresentada como implementada ou medida.
- [x] As três vistas usam texto SVG nativo e setas sem sobreposição.
- [x] O README e o gate documental referenciam somente imagens existentes e utilizadas.
- [x] O Verifier independente aprova todos os critérios e mata ao menos uma mutação documental.
