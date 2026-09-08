# Usar relógio do PostgreSQL para decidir expiração

- Estado: aceita
- Referências: [prazo de expiração](0003-prazo-de-liberacao-de-reservas-expiradas.md), [especificação da demo](../../.specs/features/flash-booking-demo/spec.md), [ADR de PostgreSQL](0004-postgresql-como-fonte-autoritativa.md).

## Contexto

API, publisher, consumidor e reconciliador podem executar em tasks distintas, com relógios locais divergentes. A regra impede expiração antes de `expiresAt` e exige concluir a devolução até cinco segundos depois quando banco e processamento estiverem saudáveis. Uma decisão distribuída baseada em relógio de processo criaria resultados diferentes para a mesma reserva.

## Decisão

Persistir `expiresAt` em UTC e usar o relógio do PostgreSQL na operação condicional que realiza `PENDING -> EXPIRED`. A atualização só pode ocorrer quando `expires_at <= clock_timestamp()` e ainda estiver `PENDING`; estado terminal e devolução de estoque permanecem na mesma transação. Mensagem SQS antecipada não altera a reserva. O reconciliador consulta e tenta processar candidatos em frequência máxima de um segundo enquanto saudável.

`expiresAt + 5 segundos` é um objetivo de operação saudável, não uma garantia durante indisponibilidade de banco, worker ou fila. Após recuperação, o reconciliador processa os atrasados de maneira idempotente sem alterar o motivo de um fechamento já confirmado.

## Alternativas consideradas

Usar o relógio Java simplifica o código local, mas não estabelece uma referência comum entre tasks. Depender somente do timer SQS não cobre atraso ou perda de publicação. Um serviço de relógio externo acrescenta custo e superfície operacional sem necessidade, pois o banco já é a fonte transacional da reserva.

## Consequências

Migrations e queries devem usar timestamp com timezone e índice para busca de pendentes vencidas. Testes de integração controlam o tempo no banco ou usam janela mensurável; não devem afirmar expiração antecipada a partir de relógio local. A decisão é idêntica na demo e na arquitetura de alta carga, pois ambas usam PostgreSQL compatível.
