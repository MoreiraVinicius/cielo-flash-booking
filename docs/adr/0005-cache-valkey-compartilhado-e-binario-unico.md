# Usar Valkey compartilhado com um único binário Java

- Estado: aceita
- Referências: [ADR de persistência](0004-postgresql-como-fonte-autoritativa.md), [spec da demo](../../.specs/features/flash-booking-demo/spec.md), [spec de alta carga](../../.specs/features/flash-booking-high-load/spec.md).

## Contexto

Os dois endpoints GET podem receber picos de acesso. A demo já precisa prever cache AWS, e operadores de Terraform devem poder alterar capacidade sem modificar Java. A disponibilidade exibida pode ser eventual; a reserva continua validada no PostgreSQL.

“Redis” pode significar o produto atual da Redis Ltd. ou as versões históricas Redis OSS. No ElastiCache, as alternativas relevantes são engines gerenciados Valkey e Redis OSS. A popularidade do nome Redis é uma vantagem de familiaridade, mas não muda o contrato técnico usado aqui: Spring Data Redis/Lettuce fala o protocolo compatível suportado pelos dois engines.

## Decisão

Usar Amazon ElastiCache for Valkey como cache compartilhado em ambas as arquiteturas. O mesmo binário Java usa Spring Data Redis com Lettuce por uma única porta de cache compatível com o protocolo Redis/Valkey. O nome da biblioteca Java não obriga o uso do engine Redis OSS. Terraform injeta endpoint, TLS, timeout, TTL e limites de fallback na task ECS.

Na demo, usar nó Valkey econômico parametrizado. Na arquitetura de alta capacidade, usar replication group Multi-AZ com primary e réplica; tipo de nó, número de shards e réplicas permanecem variáveis Terraform. A alteração de infraestrutura pode atualizar endpoint e parâmetros da task ECS, mas não classes Java, schema, migrations ou contratos HTTP.

## Contrato de cache

- `GET /events/{id}` usa `event-availability:{id}` com TTL máximo de um segundo.
- `GET /reservations/{id}` usa `reservation:{id}` com TTL máximo de um segundo.
- Reserva criada, cancelada ou expirada invalida a chave do evento depois do commit.
- Cancelamento e expiração invalidam também a chave da reserva depois do commit.
- Falha de invalidação é observável; o TTL limita a defasagem.
- Cache miss ou indisponibilidade consulta PostgreSQL. Timeout de cache: 100 ms; circuito abre após 5 falhas em 10 segundos; há no máximo 5 leituras de fallback simultâneas por task.
- POST e DELETE não são cacheados. Nenhum comando autoriza reserva a partir do cache.

## Alternativas consideradas

### ElastiCache for Redis OSS

É a alternativa mais reconhecível pelo nome e preservaria os mesmos comandos utilizados pelo projeto. Foi rejeitada porque não existe dependência de módulo exclusivo, versão histórica ou contrato comercial do Redis que justifique pagar mais. A AWS informa que Valkey mantém compatibilidade com APIs, formatos de dados e clientes Redis OSS, oferece 20% menor custo por nó e até 33% menor preço na modalidade serverless, além de governança pela Linux Foundation. Manter Redis OSS traria familiaridade nominal, mas não reduziria código, serviços ou risco de integração neste caso.

### Cache local em cada processo

Reduziria o custo de infraestrutura, mas cada task teria uma visão própria. Invalidação pós-commit não alcançaria as demais réplicas, o hit rate cairia a cada escala horizontal e a demo usaria uma arquitetura de código diferente da alta carga. Isso viola a decisão de binário e comportamento únicos.

### CloudFront ou cache do API Gateway

São úteis para respostas HTTP públicas, mas acrescentam regras de chave, headers, autorização e invalidação na borda. `GET /reservations/{id}` contém dados pessoais e não deve ser cacheado em uma borda compartilhada. Um único cache privado atende as duas consultas com a mesma política.

### Sem cache na demo

Seria mais barato durante 1h30, porém deixaria a implementação Java da demo diferente da arquitetura alta e impediria provar o comportamento de hit, miss, invalidação e fallback antes de escalar.

Fontes: [FAQ oficial do ElastiCache](https://aws.amazon.com/elasticache/faqs/), [compatibilidade Valkey e Redis OSS](https://aws.amazon.com/elasticache/redis/), [preço do ElastiCache](https://aws.amazon.com/elasticache/pricing/).

## Consequências

Valkey é somente cache; perda de dados no cache não perde reservas. Falha simultânea de cache e limite de fallback pode degradar GET com `503` para preservar o banco, enquanto comandos continuam dependentes somente do PostgreSQL. A arquitetura alta não inclui CloudFront, modelo de leitura ou projeções Java exclusivos.

A escolha deve ser revista se o projeto passar a depender de um módulo exclusivo do Redis, de suporte comercial específico da Redis Ltd. ou de uma incompatibilidade comprovada com Lettuce/Spring Data Redis. Popularidade isolada não é gatilho de migração porque a interface utilizada pela equipe continua sendo a mesma.
