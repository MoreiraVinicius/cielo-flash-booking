# Contexto de alta carga do Flash Booking

**Estado:** Pronto para design

## Limite da feature

Descrever a evolução futura da demo validada para uma topologia Multi-AZ. A evolução preserva domínio, contratos HTTP, autenticação e semântica de notificação. Adaptadores e migrations operacionais podem evoluir no schema compartilhado quando a nova topologia exigir coordenação distribuída.

## Decisões de implementação

### Evolução independente

- Consultas, comandos e workers terão métricas, autoscaling e planos de capacidade separados.
- Nenhuma evolução será acionada somente por expectativa de crescimento.
- O benchmark da demo definirá o envelope inicial.

### Alta carga

- Somente `GET /events/{id}` usa o mesmo cache-aside Valkey da demo, com TTL máximo de um segundo.
- Escritas continuarão no PostgreSQL com consistência forte.
- Aurora PostgreSQL Serverless e RDS Proxy substituem o RDS Single-AZ. A Query API terá conexões explícitas para leitura eventual de eventos e leitura autoritativa de reservas.
- ECS continuará sendo usado; EKS não é requisito de escala.
- Eventos com horário conhecido usarão pré-escala programada além de target tracking.
- `query-api` e `command-api` serão serviços ECS separados, mas apontarão para a mesma imagem; ADR 0013.
- O serviço de consultas usará Valkey e o endpoint read-only do RDS Proxy para disponibilidade. Consulta de reserva não usa cache e usa o endpoint read-write para evitar leitura ausente logo após criação.
- A demo mantém um único publisher da outbox. Antes de escalar workers, o adaptador compartilhado deve adquirir lotes por claim/lease atômico no PostgreSQL; chamadas SQS ficam fora da transação de aquisição e uma falha libera o evento pelo vencimento do lease.
- O claim reduz duplicidade sistemática entre publishers, mas não promete exactly-once diante de resposta ambígua do SQS; consumidores continuam idempotentes.
- Autenticação IAM/SigV4, API Gateway REST único, WAF e notificação SES permanecem iguais à demo. Limites e quantidade das APIs variam por Terraform; o scale-out do publisher depende primeiro do adaptador de claim/lease.

### Limite explícito

- A arquitetura cobre alta carga enquanto a contenção da linha do evento respeitar o SLO medido.
- DynamoDB com inventário particionado preserva confirmação síncrona em uma evolução extrema.
- SQS FIFO por evento é a alternativa quando o produto aceitar confirmação assíncrona.

## Decisões a critério do agente

- Tipo de nó, shards, réplicas, ACUs e máximo de tasks, sempre parametrizados no Terraform.
- Métricas de target tracking escolhidas a partir do benchmark.
- Adaptadores de infraestrutura podem evoluir para suportar a topologia. Controllers, regras de negócio e eventos permanecem compartilhados com a demo. Migrations operacionais atualizam um único schema usado pelos dois ambientes, sem criar um fork high-load.

## Ideias adiadas

- Migração efetiva do write model para DynamoDB.
- Alteração do contrato de reserva para `202 Accepted` com SQS FIFO.
- EKS, GitOps e service mesh.
