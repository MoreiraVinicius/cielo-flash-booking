# Confirmação externa da reserva — desenho

**Spec:** `.specs/features/reservation-confirmation/spec.md`
**Status:** Arquitetura aprovada; implementação em andamento.

## Veredito sobre o sistema atual

O fluxo implementado reserva e devolve capacidade com segurança, mas não conclui uma retenção como compromisso definitivo. `ReservationCreated` e `ReservationExpirationScheduled` na outbox são eventos de saída para notificação e expiração. A menção a “confirmação assíncrona aceita” em `flash-booking-high-load/design.md` trata de retornar HTTP `202` ao pedido de reserva sob carga, não de confirmar depois uma reserva existente. Portanto, a outbox atual não resolve a entrada de uma decisão externa.

## Alternativas de interface

| Abordagem | Vantagem | Custo ou limite | Recomendação |
| --- | --- | --- | --- |
| Comando HTTP interno autenticado | Devolve aceite/rejeição imediatamente; reduz atraso antes do prazo. | Acopla disponibilidade e latência dos módulos; exige controle de autorização entre serviços. | Alternativa se o produtor precisar de decisão síncrona. |
| Mensagem assíncrona de confirmação + inbox | Mantém intenção durável e tolera indisponibilidade; reutiliza worker, PostgreSQL e outbox. | A mensagem pode chegar após o prazo; produtor precisa esperar resultado e compensar rejeição. | **Escolhida pelo usuário** para o plano. |
| Webhook direto do provedor de pagamento | Parece encurtar o caminho. | Faz Flash Booking interpretar credenciais e semântica de pagamento que não lhe pertencem. | Não adotar nesta fronteira. |

## Architecture Overview

```mermaid
flowchart LR
    A[Cliente ou operador] --> B[Flash Booking: Command API]
    B --> P[(PostgreSQL: reserva e outbox)]
    P --> O[Publisher]
    O --> H[SQS direta: mensagens ao responsável externo]
    H --> M[Único responsável externo por todas as pendências]
    M --> Q[SQS direta: confirmações e desfechos de cancelamento]
    Q --> W[Flash Booking: worker]
    W --> P
```

O único responsável externo trata todas as pendências e solicita a confirmação antes do prazo. Somente a role IAM desse responsável recebe `sqs:SendMessage` para a fila de entrada; essa permissão lhe concede autoridade para declarar todas as pendências resolvidas. O campo `source` da mensagem é dado autodeclarado para correlação, não autenticação. O worker registra `resolutionId` na inbox, bloqueia a reserva, consulta o relógio PostgreSQL após o lock e decide `PENDING → CONFIRMED` ou uma rejeição. Na mesma transação grava o resultado na outbox; o publisher o entrega depois. O produtor não considera a reserva definitiva até receber `ReservationConfirmed`.

Há um único módulo externo responsável por **todas** as pendências. Suas réplicas competem por uma fila de trabalho; uma mensagem vai a uma réplica de cada vez, embora redelivery seja possível e exija idempotência no processamento externo. A criação já grava `ReservationCreated` e `ReservationExpirationScheduled` na outbox da mesma transação e publica cada tipo em uma SQS específica; por isso, um `ReservationHeld` com payload mínimo na outbox e uma SQS direta para o único responsável é mais coeso que exigir que o cliente repasse `reservationId` após o HTTP `201`. A proposta mantém o consumidor de e-mail atual: `ReservationCreated` continua interno, enquanto `ReservationHeld` é contrato da integração externa. Não há SNS Fan-Out.

## Filas de trabalho e fan-out

| Papel | SQS direta | SNS com assinaturas SQS | Recomendação |
| --- | --- | --- | --- |
| Entrada `ReservationConfirmationRequested` e desfecho de cancelamento | Uma fila tem um dono lógico (Flash Booking), mas vários workers podem competir por mensagens. | Cada fila assinante recebe sua cópia, permitindo múltiplos processadores do mesmo comando. | **SQS direta**, pois só Flash Booking decide o estado da reserva. |
| `ReservationHeld` para iniciar todas as pendências | Uma fila para o único módulo responsável permite várias réplicas sem distribuir a mesma tarefa a módulos diferentes; a operação externa ainda exige idempotência. | Fan-out dá uma cópia da mesma pendência a cada assinante, podendo duplicar a operação de negócio. | **SQS direta do responsável externo**, recomendada após revisão da outbox atual; o módulo é simulado nos testes, não implementado aqui. |
| Resultado e pedido de cancelamento ao módulo externo | A mesma fila dirigida ao único responsável pode transportar tipos de mensagem distintos; o consumidor os distingue por contrato. | Fan-out só agrega valor se houver vários consumidores independentes com necessidades concretas. | **A mesma SQS direta do responsável externo**; não implantar SNS agora. |

Vários eventos de venda não exigem uma fila por `eventId`: a fila recebe mensagens de todos, e vários workers processam reservas diferentes em paralelo, inclusive reservas do mesmo evento. A confirmação bloqueia a linha da reserva, sem novo débito de `event.available`; a concorrência relevante com expiração e cancelamento é resolvida no PostgreSQL. Uma fila SQS Standard tolera reordenação/duplicidade; inbox, transição condicional e outbox tornam a decisão idempotente. A fila não impede que o produtor externo cobre duas vezes se ele não tiver sua própria chave idempotente. Se surgir requisito de ordem estrita para mensagens da mesma **reserva**, avaliar FIFO com `MessageGroupId=reservationId`, permitindo paralelismo entre reservas. Agrupar por `eventId` serializaria todas as reservas da flash sale e criaria um gargalo. Mesmo FIFO pode entregar outra vez após sua janela de deduplicação ou expiração da visibilidade; a proteção de negócio precisa ser durável. SNS fica como opção futura quando houver múltiplos consumidores independentes.

O payload correto não comprova a identidade nem a resolução das pendências. A integração confia na role IAM do único responsável para atestar o conjunto inteiro; Flash Booking só comprova sua decisão de estoque e prazo.

### Por que uma mensagem antes do prazo pode ser rejeitada

Exemplo: `expiresAt=12:00:00`. O produtor publica `ReservationConfirmationRequested` às `11:59:59`, mas a fila entrega às `12:00:01`. Às `12:00:00`, a reserva deixa de ser válida; o reconciliador pode liberar os ingressos, e outra reserva pode retê-los. Confirmar retroativamente pela hora declarada no payload poderia comprometer os mesmos ingressos duas vezes. Por isso o Flash Booking bloqueia a reserva e usa o horário PostgreSQL da **decisão**. Se ainda estiver `PENDING`, o consumidor materializa `EXPIRED`, devolve a capacidade e grava `ReservationHoldClosed(EXPIRED)` atomicamente; depois responde `EXPIRED`. Se outro fluxo já a expirou, a confirmação só grava e entrega a rejeição. O módulo externo aguarda esse resultado e resolve sua própria consequência.

## Máquina de estados e inventário

```mermaid
stateDiagram-v2
    [*] --> PENDING: POST cria retenção
    PENDING --> CONFIRMED: decisão externa aceita antes do prazo
    PENDING --> CANCELLED: DELETE antes do prazo
    PENDING --> EXPIRED: prazo alcançado
    CONFIRMED --> CANCELLATION_PENDING: DELETE solicita reversão externa
    CANCELLATION_PENDING --> CANCELLED: sucesso externo comprovado
    CANCELLED --> [*]
    EXPIRED --> [*]
```

| Transição | Efeito no estoque | Resultado externo |
| --- | --- | --- |
| Criação → `PENDING` | `available -= quantity` | Reserva temporária criada. |
| `PENDING → CONFIRMED` | Nenhum; estoque já está retido. | `ReservationConfirmed`. |
| `PENDING → CANCELLED` ou `EXPIRED` | `available += quantity` uma vez. | `ReservationHoldClosed(CANCELLED/EXPIRED)` ao responsável externo; uma confirmação posterior recebe `ReservationConfirmationRejected`. |
| `CONFIRMED → CANCELLATION_PENDING` | Nenhum; o estoque segue comprometido. | `ReservationCancellationRequested` com `cancellationId` pela outbox. |
| `CANCELLATION_PENDING → CANCELLED` | `available += quantity` uma vez, após `ReservationCancellationCompleted` corresponder ao `cancellationId`. | A mensagem de sucesso é consumida e deduplicada na inbox. |
| Confirmação após `CONFIRMED` | Nenhum. | Mesma mensagem reproduz resultado; mensagem distinta não confirma novamente. |

Invariante por evento: `capacity - available = SUM(quantity WHERE status IN ('PENDING','CONFIRMED','CANCELLATION_PENDING'))`. Uma reserva `CONFIRMED` não tem motivo de encerramento; `confirmedAt` fica presente também durante e depois do cancelamento pendente para preservar o histórico da confirmação. Expiração só atua sobre `PENDING` e ignora `CONFIRMED` e `CANCELLATION_PENDING`. `DELETE` em `PENDING` mantém o encerramento imediato existente. `DELETE` em `CONFIRMED` grava `CANCELLATION_PENDING` e responde `202` sem afirmar conclusão; repetir o pedido mantém `202` sem nova solicitação. Depois de entrar em `CANCELLATION_PENDING`, a reserva não retorna a `CONFIRMED`. Falha ou ausência de resposta externa conserva esse estado e o estoque até chegada do desfecho positivo.

## Contrato de integração proposto

- Início do fluxo externo: gravar `ReservationHeld` na outbox junto da criação e publicá-lo em uma SQS direta do único módulo responsável por todas as pendências. O payload mínimo/versionado contém `outboxEventId`, `reservationId`, `eventId`, `quantity` e `expiresAt`, sem dados pessoais ou financeiros. O evento interno atual `ReservationCreated` alimenta a fila de notificação por e-mail e seu payload inclui `customerId`; não o expor diretamente como contrato público sem revisão. Não usar SNS para esse trabalho.
- Entrada: `ReservationConfirmationRequested` versão 1, com `resolutionId` estável para a operação de negócio, `messageId` de transporte, `source`, `reservationId` e `requestedAt`. `requestedAt` serve para auditoria, nunca para substituir o relógio da decisão. O responsável só envia esta mensagem depois de resolver todas as pendências.
- Origem: somente a role IAM do único responsável externo publica na fila de entrada e atesta todas as pendências. `source` é identificador contratual autodeclarado, não prova de identidade. Payload tem limite e validação. Não receber webhook bruto de pagamento.
- Deduplicação de entrada: chave única `(source, resolutionId)` na inbox, com fingerprint do payload, resultado e instante para replay; `messageId` pode mudar entre tentativas e não é suficiente para idempotência de negócio. A reserva só pode aceitar uma resolução externa, e uma chave repetida com payload diferente é conflito. O mesmo `resolutionId` reentregue reproduz o resultado, sem nova confirmação.
- Saída da confirmação: `ReservationConfirmed` ou `ReservationConfirmationRejected`, versão 1, com `messageId`, `resolutionId`, `reservationId`, código de resultado e instante de decisão; nenhuma PII ou dado financeiro. A rejeição obriga o solicitante a avaliar e executar compensação de uma operação externa já concluída; Flash Booking não sabe se há valor a estornar nem acompanha a conclusão da compensação.
- Cancelamento de confirmado: `DELETE` cria `cancellationId`, registra `CANCELLATION_PENDING` e `ReservationCancellationRequested` na outbox transacional. O publisher envia a solicitação à fila do módulo externo responsável. Esse módulo devolve `ReservationCancellationCompleted` com o mesmo `cancellationId` somente depois de resolver todas as obrigações. A mensagem correlacionada e deduplicada permite `CANCELLED` e devolução única de estoque. Sem conclusão positiva, Flash Booking mantém o estado e a capacidade.
- Fechamento de pendente: `DELETE` antes do prazo conclui `CANCELLED`; em `expiresAt` ou depois, `DELETE`, consumidor ou reconciliador podem concluir `EXPIRED`. Somente o vencedor devolve estoque e grava `ReservationHoldClosed` com o motivo efetivo na mesma transação para o responsável externo. `ReservationExpirationScheduled` apenas dispara a tentativa; não é o fato de fechamento. O fechamento pode chegar antes ou depois de `ReservationHeld`; o módulo externo precisa tratar ambas as ordens e compensar uma operação já concluída. Flash Booking não acompanha a compensação.
- Rejeições de domínio: `NOT_FOUND`, `EXPIRED`, `CANCELLED`, `ALREADY_CONFIRMED`, `INVALID_REQUEST`. Falha técnica deixa a mensagem elegível a retry/DLQ, sem fabricar rejeição de negócio.
- Regra temporal: o instante PostgreSQL observado depois do lock decide a aceitação. Mensagem atrasada pode ser rejeitada mesmo se produzida antes do prazo; cabe ao produtor tratar compensação após a resposta.

### Idempotência entre sistemas

O evento `ReservationHeld` conserva o mesmo `outboxEventId` em cada tentativa de publicação. A outbox atual pode republicar depois de enviar à SQS e antes de gravar `published_at`, inclusive em mais de uma instância do publisher; por isso a fila não é promessa de exatamente uma execução. O contrato do único responsável externo exige uma identidade durável por reserva e operação (`reservationId` + tipo), registro de processamento e a mesma chave de idempotência em sua operação externa. Se a operação externa envolver pagamento, a chave precisa acompanhar a chamada ao provedor; Flash Booking não pode garantir isso sozinho. O responsável só envia `ReservationConfirmationRequested` após resolver **todas** as pendências, com `resolutionId` estável em retries. A inbox do Flash Booking e a transição condicional impedem que redelivery confirme duas vezes ou altere estoque novamente.

## Code Reuse Analysis

| Artefato atual | Reuso ou mudança planejada |
| --- | --- |
| `reservation/application/*` | Concentrar a decisão de transição em um módulo de ciclo de reserva, com interface pequena para o consumidor. |
| `JdbcReservationPersistenceAdapter` | Reaproveitar `FOR UPDATE` e tempo PostgreSQL pós-lock; adicionar transição condicional e leitura de resultado. |
| `outbox_event` e publisher | Registrar resultados e pedido de cancelamento na mesma transação do estado; rotear novos tipos sem reutilizar fila de notificação/expiração. |
| `ExpirationReconciler`, `SqsExpirationConsumer` e `DELETE` tardio | Somente o vencedor de `PENDING → EXPIRED` devolve capacidade e grava `ReservationHoldClosed(EXPIRED)` na mesma transação. |
| `GET /reservations/{id}` | Expor `CONFIRMED`/`confirmedAt` sem dados de pagamento. |
| `docs/images/flash-booking-c4-*.svg` | Reusar convenções visuais e mostrar a arquitetura implementada e seus limites. |

## Data Models

- `reservation.status`: adicionar `CONFIRMED` e `CANCELLATION_PENDING` ao `CHECK`; `confirmed_at TIMESTAMPTZ` e `cancellation_id UUID` nullable, com coerência por status. `confirmed_at` é nulo antes da confirmação e obrigatório em `CONFIRMED`, `CANCELLATION_PENDING` e no `CANCELLED` vindo de reserva confirmada. `cancellation_id` é obrigatório em `CANCELLATION_PENDING`, único enquanto não nulo e permanece no `CANCELLED` originado dele; é nulo nos outros estados. Motivos terminais continuam identificando cancelamento solicitado ou prazo alcançado.
- `confirmation_inbox`: `source` e `resolution_id` são texto não vazio de até 128 caracteres e formam a chave primária de deduplicação; `reservation_id` nullable permite armazenar `NOT_FOUND` e referencia a reserva com exclusão restrita quando ela existe. `message_type` só aceita confirmação solicitada ou conclusão de cancelamento; o fingerprint é SHA-256 hexadecimal minúsculo de 64 caracteres; `outcome` é um objeto JSON reproduzível, `processed_at` usa o relógio PostgreSQL e `cancellation_id` é obrigatório somente para conclusão de cancelamento. Não há índice secundário até existir uma consulta medida que o justifique.
- A inbox conserva o resultado enquanto a reserva existir nesta versão. Limpeza ou arquivamento posterior exige janela de redrive definida para não perder a semântica de replay.
- `outbox_event`: novos tipos de resultado e solicitação de cancelamento com payload versionado. Publicação segue o padrão transacional existente.
- Inventário: manter a atualização condicional em PostgreSQL; não decrementar outra vez ao confirmar.

## Representações visuais no README

| Vista | Pergunta que responde | Artefato publicado como proposta |
| --- | --- | --- |
| C4 System Context | Quem solicita confirmação e cancelamento e qual sistema decide/compensa? | `docs/images/flash-booking-confirmation-c4-context.svg` |
| C4 Container | Onde estão APIs, worker, PostgreSQL, outbox e as duas filas direcionais da integração, sem SNS? | `docs/images/flash-booking-confirmation-c4-containers.svg` |
| Dinâmica/estado | O que acontece com aceite, duplicata, atraso, expiração e cancelamento pendente? | `docs/images/flash-booking-confirmation-lifecycle.svg` |

As vistas C4 seguem níveis distintos: contexto mostra pessoas e sistemas; containers mostram processos e armazenamentos dentro do Flash Booking e as duas SQS fora do limite do software, com dono lógico identificado; o diagrama de estados mostra desfechos e corridas. O README mantém os cinco endpoints e as figuras atuais como entrega comprovada. As novas vistas têm título e legenda “proposta — não implementada”, descrição acessível e nenhuma afirmação de pagamento realizado. Os SVGs foram renderizados e submetidos ao validador documental; T08–T10 exigem reconciliação com o runtime futuro.

## Error Handling Strategy

| Cenário | Resultado |
| --- | --- |
| Mensagem duplicada | Reproduzir decisão da inbox, sem novo efeito. |
| Mensagem após prazo ou após encerramento | Rejeição de negócio correlacionada; nenhuma confirmação retroativa. |
| Operação externa concluída mas confirmação rejeitada | Resultado correlacionado ao solicitante; ele avalia e executa compensação sob seu próprio controle. Não há segunda transição da reserva já expirada nem acompanhamento da compensação no Flash Booking. |
| Cancelamento externo falho, recusado ou sem resposta | Manter `CANCELLATION_PENDING` e estoque comprometido; retry/alerta operacional até conclusão positiva, sem presumir reversão nem permitir desfazer o pedido. |
| Banco indisponível | Retry da mensagem; nenhuma confirmação afirmada. |
| Falha de envio da resposta | Outbox pendente e retry; produtor permanece aguardando. |
| Falhas reiteradas no consumo | DLQ e alarme operacional, com procedimento de redrive. |

## Risks & Concerns

| Concern | Evidência atual | Impacto | Mitigação |
| --- | --- | --- | --- |
| Runtime anterior aceitava somente três estados | `.specs/STATE.md` e schema inicial | Uma migração e transições incorretas poderiam liberar estoque já confirmado. | AD-031 supersede AD-007; a migration e os testes cobrem cada estado e cada devolução. |
| Expiração e `DELETE` hoje só fecham `PENDING` | `JdbcReservationPersistenceAdapter.java` | Uma atualização ingênua poderia devolver estoque confirmado. | Transições condicionais e teste de corrida contra PostgreSQL real. |
| O prazo é calculado antes da espera pelo lock de estoque | `CreateReservationService.java:43-55` | Reduz a janela efetiva para confirmação sob contenção. | Medir o prazo na decisão de criação e testar lock wait na mesma mudança. |
| Mensagem externa pode chegar tarde | Nova fronteira assíncrona | O produtor pode ter concluído sua própria operação sem reserva. | Resultado explícito e contrato de compensação; não presumir sucesso pela publicação. |
| Cancelamento de confirmado depende de responsável externo | Fronteira assíncrona | `CANCELLATION_PENDING` pode durar indefinidamente e reter estoque. | Não liberar estoque sem `ReservationCancellationCompleted`; fila e consumidor mantêm retry/DLQ e o módulo externo controla sua recuperação. |
| Inbox cresce com mensagens processadas | Novo armazenamento durável | Custo e retenção sem limite explícito. | Medir volume e definir arquivo/limpeza somente depois de fixar a janela de redrive. |
| Publisher atual pode reenviar o mesmo evento | `OutboxSqsPublisher` publica antes de marcar `published_at` e várias instâncias leem pendências | Um `ReservationHeld` duplicado pode iniciar a mesma operação externa mais de uma vez. | Identidade estável do evento, deduplicação durável por reserva/operação no responsável externo e chave idempotente no provedor. |
| `ReservationHoldClosed` pode chegar antes de `ReservationHeld` | SQS Standard e publicação assíncrona | Responsável externo poderia iniciar trabalho em reserva já cancelada ou expirada. | Tratar fechamento como estado terminal/tombstone por reserva e descartar hold tardio; teste com ordem invertida para ambos os motivos. |
| Diagramas C4 antigos combinam topologia e componentes da demo | `docs/images/flash-booking-c4-demo.svg`, `README.md` | O leitor pode confundir cenários de infraestrutura com o ciclo de reserva. | Vistas C4 por nível e reconciliação das figuras antigas com o runtime, identificando demo e topologia de alta carga. |
| Specs derivadas assumem apenas três estados | `flash-booking-demo`, `dynamic-fake-load`, `event-executive-summary` | Invariantes, métricas e relatórios ficariam falsos. | Atualizar todas as specs afetadas junto da implementação, inclusive critérios e testes. |

## Tech Decisions

| Decisão | Contrato | Razão |
| --- | --- | --- |
| Nome do estado | `CONFIRMED` | Representa compromisso de reserva sem interpretar pagamento. |
| Entrada e saída | Inbox para mensagem externa, outbox para resultado | Protege duplicatas e dual write. |
| Cancelamento de confirmado | Pedido sem reversão a `CONFIRMED`, com `CANCELLATION_PENDING` + outbox para fila externa | Não afirma reversão financeira nem libera capacidade antes do desfecho positivo. |
| Fechamento de pendente | `ReservationHoldClosed(CANCELLED/EXPIRED)` gravado na transição vencedora | Avisa o único responsável externo sem acompanhar sua compensação. |
| Fan-out | Adiado até existir mais de um consumidor independente | Evita um tópico sem assinantes e não duplica uma pendência externa. |
| Autoridade temporal | PostgreSQL após lock | Mantém a mesma regra de prazo sob concorrência. |
| Fonte dos visuais | `.specs/` e código verificado | README apresenta a arquitetura sem se tornar outra fonte de regra. |

## Referências de desenho

- [C4 Model — diagramas](https://c4model.com/diagrams): contexto, containers e diagramas dinâmicos têm escopos distintos; não é necessário desenhar todos os níveis.
- [AWS — transactional outbox](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html): publicação pode duplicar eventos; o consumidor precisa ser idempotente.
- [AWS — SNS com filas SQS assinantes](https://docs.aws.amazon.com/sns/latest/dg/sns-sqs-as-subscriber.html): um tópico envia cópias para filas independentes.
- [AWS — tipos de fila SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-queue-types.html): Standard pode duplicar e reordenar; FIFO introduz grupos ordenados.
- [AWS — paralelismo por grupo FIFO](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/FIFO-queues-understanding-logic.html): mensagens de grupos diferentes podem progredir em paralelo.

## Specs afetadas na execução

Atualizar `STATE.md`/AD-007, `CONTEXT.md`, `flash-booking-demo/{spec,context,design}`, `flash-booking-high-load/{spec,context,design,tasks}`, `dynamic-fake-load/spec`, `event-executive-summary/{spec,context,design}`, `eventual-consistency-aws-visuals/{spec,design}` e `readme-visual-storytelling/{spec,design}`. Revisar também Postman, notificações, diagramas existentes e o gate de README. Artefatos de validação anteriores preservam o escopo que efetivamente validaram.
