# Contexto da demo Flash Booking

**Estado:** Validated

## Limite da feature

Entregar os cinco endpoints do case em Java/Spring Boot, executáveis localmente por Docker Compose e publicáveis em uma arquitetura AWS econômica por Terraform. A reserva inclui o cliente e gera notificação de reserva temporária por e-mail.

## Decisões de implementação

### Operação

- Uma pessoa deve conseguir desenvolver, provisionar, diagnosticar e explicar a solução.
- ECS Fargate foi escolhido em vez de EKS.
- O mesmo binário inicia como `query-api`, `command-api` ou `worker`; ver [ADR 0013](../../../docs/adr/0013-separar-servicos-de-consulta-e-comando.md).
- O ambiente demo terá uma task de consultas, uma de comandos e uma de worker. O perfil local de concorrência executará ao menos duas instâncias de comandos para provar o requisito do case sem custo AWS adicional.

### Consistência

- PostgreSQL será a fonte de verdade.
- RDS PostgreSQL 16 Single-AZ será a infraestrutura autoritativa da demo; ver [ADR 0004](../../../docs/adr/0004-postgresql-como-fonte-autoritativa.md).
- A reserva será aceita de forma síncrona como bloqueio temporário PENDING, sem confirmação de compra; ver [ADR 0002](../../../docs/adr/0002-reserva-temporaria-com-motivo-de-encerramento.md).
- A disponibilidade nunca será usada para autorizar a reserva; a transação de reserva decide.
- Reservas pendentes expiram em 10 minutos, valor configurável.
- Com banco e processamento saudáveis, a devolução de capacidade deve concluir até expiresAt + 5 segundos, sem estender a validade; ver [ADR 0003](../../../docs/adr/0003-prazo-de-liberacao-de-reservas-expiradas.md).
- CANCELLED e EXPIRED persistem código e descrição do motivo conforme o catálogo e formato HTTP do [ADR 0006](../../../docs/adr/0006-catalogo-de-motivos-de-encerramento.md).
- Idempotência dos comandos usa uma janela de 24 horas no PostgreSQL: a validade é decidida atomicamente pelo relógio do banco e o worker remove registros vencidos em lotes, conforme o [ADR 0007](../../../docs/adr/0007-idempotencia-persistente-de-comandos.md).
- A elegibilidade de expiração usa o relógio do PostgreSQL conforme o [ADR 0008](../../../docs/adr/0008-relogio-do-banco-para-expiracao.md).
- `GET /events/{id}` usa ElastiCache for Valkey compartilhado, com TTL máximo de um segundo. `GET /reservations/{id}` consulta PostgreSQL e retorna somente `{id, name}` como referência do evento. O cache não autoriza reservas; ver [ADR 0005](../../../docs/adr/0005-cache-valkey-compartilhado-e-binario-unico.md).
- Toda reserva pertence a um `Customer`. O Java trata o e-mail antes da busca e persiste somente a forma canônica na coluna `email`, conforme [ADR 0010](../../../docs/adr/0010-cliente-como-entidade-da-reserva.md) e [modelagem de dados](../../../docs/data-model.md).
- Reserva criada emite `ReservationCreated` por outbox e envia e-mail assíncrono pelo SES; o texto não confirma compra, conforme [ADR 0011](../../../docs/adr/0011-notificacao-assincrona-de-reserva-por-email.md).

### Infraestrutura

- AWS será o único provedor de runtime.
- Terraform será o único mecanismo de provisionamento.
- Docker Compose será usado apenas no ambiente local.
- A região padrão será `sa-east-1`.
- O API Gateway REST será o único ponto público e exigirá IAM/SigV4. Resource policy, WAF, throttling por método e limites de capacidade protegem a demonstração; ver [ADR 0012](../../../docs/adr/0012-autenticacao-e-protecao-de-custos-na-borda.md).

### Decisões a critério do agente

- Nomes internos de pacotes e classes.
- Valores exatos de CPU e memória da task, desde que econômicos e parametrizados.
- Formato interno dos eventos do outbox.

## Referências específicas

- Case original: `Case BackEnd 1.md`.
- Stack da vaga: Java, Spring Boot, Spring Data, Spring Security, APIs REST, testes, Docker e sistemas distribuídos.

## Ideias adiadas

- Aurora, RDS Proxy e múltiplas tasks por serviço pertencem ao plano de alta carga. Valkey, WAF e separação consulta/comando já pertencem à demo, com capacidade econômica.
- EKS depende de equipe de plataforma e não integra os planos executáveis atuais.
