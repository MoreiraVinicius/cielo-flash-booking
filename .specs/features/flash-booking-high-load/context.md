# Contexto de alta carga do Flash Booking

**Estado:** Pronto para design

## Limite da feature

Evoluir a infraestrutura AWS de uma demo validada para Multi-AZ, mantendo o mesmo código Java, controllers, schema Flyway, endpoints, domínio, autenticação, notificação e contratos de cache.

## Decisões de implementação

### Evolução independente

- Consultas, comandos e workers terão métricas, autoscaling e planos de capacidade separados.
- Nenhuma evolução será acionada somente por expectativa de crescimento.
- O benchmark da demo definirá o envelope inicial.

### Alta carga

- Os dois GET continuam usando o mesmo cache-aside Valkey da demo, com TTL máximo de um segundo.
- Escritas continuarão no PostgreSQL com consistência forte.
- Aurora PostgreSQL Serverless e RDS Proxy substituem o RDS Single-AZ sem mudar o binário Java.
- ECS continuará sendo usado; EKS não é requisito de escala.
- Eventos com horário conhecido usarão pré-escala programada além de target tracking.
- `query-api` e `command-api` serão serviços ECS separados, mas apontarão para a mesma imagem; ADR 0013.
- O serviço de consultas usará Valkey e, para disponibilidade, endpoint read-only do RDS Proxy. Consulta de reserva usa read-write para evitar leitura ausente logo após criação.
- Autenticação IAM/SigV4, API Gateway REST único, WAF e notificação SES permanecem iguais à demo; somente limites e quantidade de tasks variam por Terraform.

### Limite explícito

- A arquitetura cobre alta carga enquanto a contenção da linha do evento respeitar o SLO medido.
- DynamoDB com inventário particionado preserva confirmação síncrona em uma evolução extrema.
- SQS FIFO por evento é a alternativa quando o produto aceitar confirmação assíncrona.

## Decisões a critério do agente

- Tipo de nó, shards, réplicas, ACUs e máximo de tasks, sempre parametrizados no Terraform.
- Métricas de target tracking escolhidas a partir do benchmark.
- Nenhuma mudança de código Java, controller, regra de negócio, schema ou evento exclusiva da arquitetura alta.

## Ideias adiadas

- Migração efetiva do write model para DynamoDB.
- Alteração do contrato de reserva para `202 Accepted` com SQS FIFO.
- EKS, GitOps e service mesh.
