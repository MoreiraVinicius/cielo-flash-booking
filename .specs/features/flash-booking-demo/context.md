# Contexto da demo Flash Booking

**Estado:** Validated

Este contexto fixa o ciclo da demo: retenção temporária, confirmação integral pelo único responsável externo e cancelamento assíncrono depois da confirmação. A [spec de confirmação externa](../reservation-confirmation/context.md) define as filas e transições desse ciclo.

## Limite da feature

Entregar os cinco endpoints do case em Java/Spring Boot, executáveis localmente por Docker Compose e publicáveis em uma arquitetura AWS econômica por Terraform. A reserva inclui o cliente e gera notificação de reserva temporária por e-mail.

## Decisões de implementação

### Operação

- Uma pessoa deve conseguir desenvolver, provisionar, diagnosticar e explicar a solução.
- ECS Fargate foi escolhido em vez de EKS.
- O mesmo binário inicia como `query-api`, `command-api` ou `worker`; a decisão está em [STATE.md](../../STATE.md).
- O ambiente demo terá uma task de consultas, uma de comandos e uma de worker. O perfil local de concorrência executará ao menos duas instâncias de comandos para provar o requisito do case sem custo AWS adicional.

### Consistência

- PostgreSQL será a fonte de verdade.
- RDS PostgreSQL 16 Single-AZ será a infraestrutura autoritativa da demo.
- Por até dois dias de demo, o operador pode habilitar acesso direto do DataGrip ao RDS a partir de um único IPv4 `/32`; isso torna a rota dos subnets de dados internet-routable e não é reutilizável na arquitetura high-load.
- A criação da reserva continua síncrona e inicia em `PENDING`; a confirmação posterior chega por fila assíncrona e não declara pagamento ou compra.
- A disponibilidade nunca será usada para autorizar a reserva; a transação de reserva decide.
- Evento pode receber `startsAt` e `endsAt` opcionais. Sem início, a venda é imediata; sem fim, permanece elegível enquanto houver capacidade. O PostgreSQL decide a criação e a janela no mesmo decremento de inventário. Início informado é posterior ao instante de criação; fim com início é posterior ao início; fim sem início é no mínimo 10 minutos posterior à criação.
- Reservas pendentes expiram em 10 minutos, valor configurável.
- Com banco e processamento saudáveis, a devolução de capacidade deve concluir até expiresAt + 5 segundos, sem estender a validade.
- Antes de `expiresAt`, `DELETE /reservations/{id}` encerra uma reserva pendente como `CANCELLED`. Em `expiresAt` ou depois, o prazo prevalece e a mesma chamada materializa `EXPIRED`; o PostgreSQL decide após conquistar o lock da reserva.
- Confirmação mantém os ingressos já retidos; `CONFIRMED` e `CANCELLATION_PENDING` não expiram pelo prazo original nem alteram disponibilidade.
- `DELETE` sobre `CONFIRMED` registra `CANCELLATION_PENDING`, responde `202` e envia uma única solicitação ao responsável externo. Só `ReservationCancellationCompleted` com o `cancellationId` correspondente conclui `CANCELLED` e libera capacidade.
- `ReservationHeld` inicia o trabalho do responsável externo; fechamento de uma reserva pendente publica `ReservationHoldClosed(CANCELLED|EXPIRED)` para o mesmo responsável.
- CANCELLED e EXPIRED persistem código e descrição do motivo conforme o catálogo e formato HTTP definido em [STATE.md](../../STATE.md); `CONFIRMED` preserva `confirmedAt` mesmo após o cancelamento concluído.
- Idempotência dos comandos usa uma janela de 24 horas no PostgreSQL: a validade é decidida atomicamente pelo relógio do banco e o worker remove registros vencidos em lotes.
- A elegibilidade de expiração usa o relógio do PostgreSQL.
- `GET /events/{id}` usa ElastiCache for Valkey compartilhado, com TTL máximo de um segundo, e retorna também a janela persistida. `GET /reservations/{id}` consulta PostgreSQL e retorna somente `{id, name}` como referência do evento. O cache não autoriza reservas.
- Toda reserva pertence a um `Customer`. O Java trata o e-mail antes da busca e persiste somente a forma canônica na coluna `email`.
- Reserva criada emite `ReservationCreated` por outbox e envia e-mail assíncrono pelo SES; o texto não confirma compra.

### Infraestrutura

- AWS será o único provedor de runtime.
- Terraform será o único mecanismo de provisionamento.
- Docker Compose será usado apenas no ambiente local.
- A região padrão será `sa-east-1`.
- O API Gateway REST será o único ponto público e exigirá IAM/SigV4. Resource policy, WAF, throttling por método e limites de capacidade protegem a demonstração.

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
