# Contexto da confirmação externa

**Status:** T01–T06 implementadas; T07 e reconciliação documental/visual continuam no plano AD-031.

## Linguagem de domínio

**Reserva pendente:** retenção temporária de ingressos, ainda sujeita a cancelamento ou vencimento.

**Reserva confirmada:** compromisso definitivo de todos os ingressos da reserva, decidido pelo Flash Booking antes de `expiresAt` a partir da declaração autorizada de que o único responsável externo resolveu todas as pendências. O Flash Booking não verifica pagamento nem emite ingresso.

**Solicitação de confirmação:** instrução de um módulo externo identificada de modo durável; sua publicação não prova que a reserva foi confirmada.

**Resultado de confirmação:** decisão deste sistema, aceita ou rejeitada, devolvida ao módulo externo por evento correlacionado.

**Reserva retida (`ReservationHeld`):** fato de que `PENDING` reteve temporariamente todos os ingressos solicitados. Inicia o trabalho do único responsável externo; não significa confirmação definitiva.

**Cancelamento solicitado:** intenção registrada para uma reserva já confirmada. Enquanto a operação externa não terminar, os ingressos continuam comprometidos em `CANCELLATION_PENDING`; o estado final `CANCELLED` ainda não foi alcançado.

**`ReservationCancellationCompleted`:** mensagem de sucesso correlacionada pelo `cancellationId` à solicitação externa; declara que todas as obrigações externas foram concluídas e permite ao Flash Booking liberar o estoque uma vez.

**Compensação externa:** correção de uma operação concluída fora do Flash Booking quando a confirmação da reserva é rejeitada. A reserva pode já estar `EXPIRED` ou `CANCELLED`; a compensação não é uma nova transição da reserva.

## Premissas do usuário

- O Flash Booking continua responsável pelo gerenciamento do ciclo da reserva.
- Pagamentos e compras pertencem a outros módulos de uma possível arquitetura maior; não serão implementados aqui.
- A reserva precisa admitir conclusão definitiva por estímulo externo. `PAID` seria um nome incorreto para um estado que o Flash Booking não pode verificar.
- O README deverá explicar esse limite e representar o fluxo com diagramas C4.
- A entrada da solicitação de confirmação será por fila assíncrona, conforme escolha explícita do usuário.
- Uma reserva será confirmada inteira ou não será confirmada; não há confirmação parcial.
- O usuário aceitou usar o instante da decisão no PostgreSQL para mensagens atrasadas e pediu uma explicação do caso.
- Há um único módulo externo responsável por resolver **todas** as pendências antes de solicitar confirmação. Réplicas desse módulo podem dividir o trabalho; módulos independentes não recebem cópias da mesma tarefa.
- Após revisar a outbox e as filas atuais, a escolha solicitada pelo usuário para iniciar esse trabalho é publicar `ReservationHeld` em uma SQS direta do único responsável externo. Não implantar SNS Fan-Out.
- Uma fila SQS dedicada é a entrada da confirmação; ela tem um único dono lógico, o Flash Booking, e pode ter vários workers competindo. A fila de saída tem como único dono lógico o responsável externo, ainda que várias réplicas dele a consumam.
- A infraestrutura separa os sentidos em `reservation-to-owner` (publicação pelo worker, consumo pelo responsável) e `reservation-from-owner` (publicação pelo responsável, consumo pelo worker); ambas são SQS Standard, long poll de 20 segundos e DLQ própria. Em AWS, a role do responsável é opcional na configuração da demo, mas sem seu ARN não há acesso externo concedido.
- O `worker` tem `SendMessage` somente na fila ao responsável e `ReceiveMessage`/`DeleteMessage`/`GetQueueAttributes` somente na fila de entrada. A role externa configurada pode consumir a fila ao responsável e publicar na fila de entrada; não recebe acesso às filas de e-mail ou expiração.
- A outbox persiste `ReservationHeld`, `ReservationConfirmed`, `ReservationConfirmationRejected`, `ReservationHoldClosed` e `ReservationCancellationRequested` com o estado/inbox que os origina. Eventos de integração usam `outboxEventId` igual ao id da linha, sem PII, e o publisher os roteia exclusivamente a `reservation-to-owner`; criação/notificação e expiração preservam suas filas atuais.
- Vários eventos de venda podem usar a mesma fila de confirmação com processamento paralelo por reserva.
- O usuário quer que o cancelamento de uma reserva confirmada seja assíncrono: o Flash Booking registra um estado intermediário, envia uma solicitação ao módulo externo e só conclui após desfecho externo. `DELETE` de `PENDING` permanece imediato e precisa notificar o responsável externo para parar ou compensar seu trabalho.
- A expiração efetiva de `PENDING` também notifica o responsável externo por `ReservationHoldClosed(EXPIRED)`; o agendamento da expiração não é esse fato.
- Uma vez iniciado o cancelamento de `CONFIRMED`, não há comando para desfazer o cancelamento nem retorno a `CONFIRMED`. Falha ou ausência de resposta externa mantém `CANCELLATION_PENDING` e o estoque comprometido até conclusão; recuperação depende de retry ou intervenção operacional.
- Quando uma confirmação é rejeitada após uma operação externa concluída, o Flash Booking deve enviar um resultado correlacionado que acione a compensação no módulo externo; esse serviço não executa a compensação financeira.
- O desfecho positivo do cancelamento de `CONFIRMED` significa que todas as obrigações externas necessárias foram revertidas; até esse desfecho, estoque segue comprometido. Uma única mensagem `ReservationConfirmationRejected` basta para exigir compensação após confirmação rejeitada. O Flash Booking não acompanha a conclusão da compensação externa de reservas `CANCELLED` ou `EXPIRED`.

## Condição técnica de integração

- O contrato usa `outboxEventId` estável em republicações, `reservationId` + operação para deduplicação durável no responsável externo e `resolutionId` estável nas respostas. SQS e outbox podem redeliver, e somente o responsável externo pode impedir execução dupla de sua própria operação. O acesso IAM à fila autoriza o envio; `source` no payload não autentica o produtor.
