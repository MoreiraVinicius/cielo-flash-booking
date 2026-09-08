# Usar PostgreSQL como fonte autoritativa de reservas

- Estado: aceita
- Referências: [enunciado](../../Case%20BackEnd%201.md), [design da demo](../../.specs/features/flash-booking-demo/design.md), [design de alta carga](../../.specs/features/flash-booking-high-load/design.md).

## Contexto

Cada reserva precisa: verificar e decrementar inventário, criar a reserva, registrar idempotência e gravar um evento de outbox. Cancelamento e expiração precisam encerrar a reserva, guardar o motivo e devolver a capacidade uma vez. Esses dados têm relações, invariantes e necessidade de diagnóstico durante o case.

## Decisão

Usar PostgreSQL como fonte autoritativa para eventos, reservas, inventário, idempotência e outbox. A demo usa Amazon RDS for PostgreSQL 16 Single-AZ. A arquitetura de alta capacidade usa Aurora PostgreSQL Serverless, com capacidade, reader e limites definidos por Terraform. As duas usam o mesmo driver JDBC, schema Flyway, SQL, modelo de domínio e binário Java.

## Alternativas consideradas

### DynamoDB

DynamoDB oferece escala horizontal e pagamento por requisição, o que é atraente para picos muito grandes e acessos por chave conhecidos. Suas transações conseguem agrupar ações atomicamente, mas cada operação de reserva teria de codificar explicitamente as condições, o modelo de chaves, índices e idempotência; transações processam duas escritas por item e consomem capacidade mesmo se a condição cancelar a transação. Isso desloca complexidade para a modelagem e aumenta o risco de implementação e testes no prazo atual. Fontes: [transações](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transactions.html), [capacidade de transações](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/read-write-operations.html).

### PostgreSQL

PostgreSQL representa relações e invariantes com transações SQL, foreign keys, constraints e atualização condicional. Permite auditar a sequência de reserva e diagnosticar inconsistências com consultas ad hoc, sem transformar o case em um exercício de modelagem NoSQL. RDS usa PostgreSQL comunitário, preservando portabilidade; Aurora mantém compatibilidade PostgreSQL para a evolução de infraestrutura. Fontes: [RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html), [Aurora PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.AuroraPostgreSQL.html).

## Preço e capacidade

A demo fica ativa por no máximo 1h30 e usa uma instância RDS Graviton econômica parametrizada. DynamoDB poderia reduzir o custo ocioso, mas essa economia não compensa reescrever o modelo de persistência no prazo disponível. A estimativa deve registrar região, duração, armazenamento, RDS, ElastiCache, ECS, rede e logs no [arquivo de custos](../cost-estimate.md), pois preços variam por região e data. Fontes: [preço RDS](https://aws.amazon.com/rds/postgresql/pricing/), [preço DynamoDB](https://aws.amazon.com/dynamodb/pricing/).

Aurora Serverless é reservado para alta capacidade porque permite variar ACUs via Terraform, mantendo o contrato PostgreSQL. Não é alegado como testado remotamente nesta entrega.

## Consequências

O hot row de um evento continua sendo o limite conhecido. Medir lock waits e p95 de reserva; somente se esse limite violar o SLO após tuning, abrir ADR de evolução para DynamoDB. O cache não se torna fonte de verdade e não participa da autorização de reservas.
