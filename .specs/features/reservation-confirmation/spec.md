# Confirmação externa da reserva — especificação

**Status:** Aprovada; implementação em andamento.

## Problem Statement

O Flash Booking gerencia a retenção e o compromisso definitivo dos ingressos. Um único módulo externo resolve todas as pendências e envia uma solicitação assíncrona de confirmação. O Flash Booking decide a transição com seu estoque e relógio autoritativos e comunica o resultado. Pagamento, cobrança, estorno e emissão continuam em outros módulos.

## Goals

- [ ] Permitir que uma decisão externa elegível torne uma reserva `CONFIRMED` antes do prazo.
- [ ] Preservar o inventário sob concorrência entre confirmação, cancelamento e expiração.
- [ ] Comunicar aceites e rejeições para que o módulo externo conclua ou compense seu próprio fluxo.
- [ ] Permitir solicitação assíncrona de cancelamento de reserva confirmada, preservando o estoque até a conclusão externa.
- [ ] Explicar no README o ciclo completo com vistas C4 e um fluxo temporal alinhado ao runtime.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Pagamento, cobrança, reembolso e webhooks de provedores de pagamento | Pertencem a outros módulos; este serviço recebe uma solicitação de confirmação sem interpretar sua causa. |
| Emissão ou entrega de ingresso | Exige outro contrato de domínio. |
| Implementar o módulo externo | Um produtor simulado basta para verificar a interface. |
| Aplicar infraestrutura AWS ou publicar contratos remotos | A entrega prepara recursos e contratos no repositório; aplicação e integração remota exigem execução operacional própria. |
| Executar estorno, reembolso ou outra reversão financeira | O módulo externo executa essa operação e informa o desfecho; Flash Booking gerencia somente o estado da reserva e seu estoque. |
| Acompanhar se uma compensação externa de `CANCELLED` ou `EXPIRED` terminou | O responsável externo controla seus próprios retries, resultados e falhas; o estado da reserva já é terminal no Flash Booking. |

## Assumptions & Open Questions

| Decisão | Contrato | Razão | Confirmada? |
| --- | --- | --- | --- |
| Significado de `CONFIRMED` | Todas as pendências externas declaradas resolvidas pelo único responsável autorizado e todos os ingressos da reserva definitivamente comprometidos | O Flash Booking conhece a declaração recebida e o compromisso de estoque; não processa nem verifica pagamento por conta própria. | sim; resposta explícita do usuário |
| Quantidade confirmada | A reserva é confirmada por inteiro, sem confirmação parcial | Mantém uma transição e uma invariante de estoque por reserva. | sim; resposta explícita do usuário |
| Entrada do estímulo externo | Mensagem de integração durável em fila de confirmação, consumida pelo worker | Tolera indisponibilidade e separa o produtor da implementação do Flash Booking. | sim; escolha explícita do usuário |
| Responsável pelas pendências | Um único módulo externo resolve todas antes de solicitar confirmação | Evita que a conclusão isolada de uma etapa confirme a reserva prematuramente. | sim; resposta explícita do usuário |
| Início do trabalho externo | `ReservationHeld` gravado na outbox da criação e enviado a uma SQS direta do responsável externo | Segue o padrão já usado para e-mail/expiração e não delega ao cliente um segundo envio após receber `201`. | sim; escolha solicitada pelo usuário e feita após revisão do código |
| Prazo | O relógio PostgreSQL, observado depois do lock da reserva, precisa estar antes de `expiresAt` | Impede confirmar estoque já liberado, mesmo se a mensagem foi produzida antes do prazo. | sim; usuário aceitou recomendação e pediu explicação |
| Corrida com cancelamento/expiração | A primeira transição válida sob o lock vence; o estado terminal não volta para `PENDING` | Preserva a devolução única e evita oversell. | sim |
| Cancelar após confirmação | `DELETE /reservations/{id}` registra `CANCELLATION_PENDING`, envia solicitação externa por outbox e conserva o estoque até sucesso externo; não há retorno a `CONFIRMED` | Flash Booking coordena a intenção sem executar a reversão financeira nem permitir desfazer o pedido de cancelamento. | sim; respostas explícitas do usuário |
| Cancelar reserva pendente | Manter o `DELETE` imediato de `PENDING`, com devolução única de capacidade e mensagem ao responsável externo | Preserva o comportamento já implementado e permite interromper ou compensar a operação externa. | sim; resposta explícita do usuário |
| Expirar reserva pendente | A transição efetiva `PENDING → EXPIRED` registra `ReservationHoldClosed(EXPIRED)` na mesma transação da devolução de capacidade | O responsável externo precisa parar ou compensar trabalho; o evento de agendamento de expiração não representa o fechamento. | sim; usuário aceitou recomendação |
| Falha no cancelamento externo | Manter `CANCELLATION_PENDING` e estoque comprometido até resposta positiva; recuperar por retry ou intervenção operacional | Uma vez solicitado, o cancelamento não é desfeito, e falha externa não prova reversão concluída. | sim; resposta explícita do usuário |
| Resposta ao produtor | Resultado assíncrono `ReservationConfirmed` ou `ReservationConfirmationRejected`, correlacionado à solicitação | O produtor não deve supor sucesso ao publicar a mensagem. | sim; decisões explícitas do usuário sobre confirmação e rejeição |
| Distribuição de fatos de reserva | Sem SNS Fan-Out; somente mensagens dirigidas ao único responsável externo | Não há múltiplos assinantes independentes; SQS direta já corresponde à integração necessária. | sim; sem fan-out, resposta explícita do usuário |
| Rejeição após operação externa | Enviar um único `ReservationConfirmationRejected` correlacionado, que exige avaliação e compensação externa sem acompanhamento no Flash Booking | Flash Booking não sabe qual operação financeira ocorreu; uma segunda mensagem de cancelamento pode duplicar o mesmo comando. | sim; usuário aceitou recomendação e excluiu acompanhamento |
| Retenção da inbox | Manter o resultado enquanto a reserva existir | Preserva o replay de mensagens redirigidas tardiamente; revisar crescimento antes de habilitar escala. | sim |
| Documentação visual | Mostrar o ciclo e os limites comprovados pelo runtime | Evita anunciar comportamento sem evidência e separa reserva de pagamento. | sim |

**Open questions:** none. O contrato técnico do responsável externo precisa preservar idempotência durável por reserva/operação; o campo `source` na mensagem é autodeclarado e não substitui permissão IAM para a fila.

## User Stories

### P1: Confirmar uma reserva elegível

**User Story:** Como módulo externo, quero solicitar a conclusão de uma reserva para saber se os ingressos foram comprometidos definitivamente.

**Acceptance Criteria:**

1. WHEN uma solicitação recebida da fila exclusiva do responsável externo identificar uma reserva `PENDING` e o PostgreSQL observar tempo anterior a `expiresAt` depois do lock THEN o sistema SHALL persistir `CONFIRMED` e `confirmedAt` em uma transação, sem alterar `event.available`.
2. WHEN uma reserva passar a `CONFIRMED` THEN `GET /reservations/{id}` SHALL retornar esse estado e `confirmedAt` sem declarar pagamento ou emissão.
3. IF a solicitação de confirmação chegar para reserva inexistente THEN o sistema SHALL registrar resultado `NOT_FOUND` correlacionado, sem alterar inventário.
4. IF a mensagem não contiver um envelope válido com `source`, `resolutionId`, `reservationId` e tipo conhecido THEN o consumidor SHALL deixá-la elegível a retry e DLQ sem alterar a reserva; a permissão IAM da fila SHALL limitar o produtor ao único responsável externo.
5. WHEN uma reserva de quantidade maior que um for confirmada THEN o sistema SHALL comprometer a quantidade inteira ou rejeitar a solicitação sem confirmação parcial.
6. WHEN uma reserva `PENDING` for criada THEN o sistema SHALL registrar `ReservationHeld` na outbox da mesma transação para a fila direta do responsável externo, com `reservationId`, `eventId`, `quantity`, `expiresAt` e identidade durável do evento.

**Independent Test:** Enviar uma solicitação válida a um produtor simulado e verificar estado, instante, inventário e resposta correlacionada no PostgreSQL.

### P1: Preservar os desfechos sob concorrência

**User Story:** Como responsável pelo inventário, quero que confirmação, cancelamento e expiração disputem a mesma reserva sem efeitos duplicados.

**Acceptance Criteria:**

1. WHILE a reserva estiver `CONFIRMED` ou `CANCELLATION_PENDING` o sistema SHALL impedir que o consumidor de expiração devolva sua capacidade.
2. IF cancelamento ou expiração vencer primeiro THEN uma confirmação posterior SHALL produzir `ReservationConfirmationRejected` com motivo `CANCELLED` ou `EXPIRED`, sem reduzir capacidade outra vez.
3. IF confirmação vencer primeiro THEN `DELETE /reservations/{id}` SHALL registrar `CANCELLATION_PENDING` e a solicitação externa na mesma transação, responder `202` e preservar `event.available`.
4. WHEN a confirmação disputar o lock e o prazo for alcançado antes da decisão PostgreSQL THEN o sistema SHALL rejeitar a confirmação e materializar `EXPIRED` com devolução única de capacidade.
5. The system SHALL manter `event.capacity - event.available = SUM(quantity)` das reservas `PENDING`, `CONFIRMED` e em cancelamento do evento após cada transação concluída.

**Independent Test:** Executar pares concorrentes confirmação × cancelamento e confirmação × expiração em PostgreSQL real, incluindo espera pelo lock até depois do prazo.

### P1: Entregar uma decisão confiável ao módulo externo

**User Story:** Como módulo externo, quero receber um desfecho verificável mesmo quando mensagens são duplicadas ou chegam tarde.

**Acceptance Criteria:**

1. WHEN uma resolução nova for consumida THEN o sistema SHALL gravar `(source, resolutionId)`, fingerprint e resultado em uma inbox durável na mesma transação da decisão sobre a reserva.
2. IF o mesmo `resolutionId` for entregue novamente, mesmo com outro `messageId` de transporte, THEN o sistema SHALL reproduzir o resultado registrado sem nova transição ou nova devolução de capacidade; payload divergente com a mesma chave SHALL produzir conflito.
3. WHEN uma confirmação for aceita ou rejeitada por regra de negócio THEN o sistema SHALL registrar um evento de resultado na outbox na mesma transação da inbox e da reserva.
4. IF a publicação do resultado falhar THEN a outbox SHALL manter o evento elegível a retry; o produtor externo SHALL tratar o resultado como pendente até recebê-lo.
5. IF o consumidor não conseguir processar uma mensagem após as tentativas configuradas THEN a mensagem SHALL permanecer recuperável em DLQ com motivo observável.
6. IF uma confirmação for rejeitada depois de uma operação externa concluída THEN o sistema SHALL enviar o resultado correlacionado com motivo; o módulo externo SHALL decidir e executar a compensação correspondente sem pedir confirmação retroativa.
7. WHEN `DELETE` encerrar uma reserva `PENDING` antes do prazo THEN o sistema SHALL registrar `ReservationHoldClosed` com motivo `CANCELLED` para o responsável externo na mesma transação da devolução de capacidade, para interromper ou compensar seu trabalho.
8. WHEN a outbox republicar `ReservationHeld` THEN o sistema SHALL conservar a identidade do evento e encaminhá-lo somente à fila do único responsável externo; o contrato desse responsável SHALL exigir deduplicação durável por reserva e operação, inclusive na sua chamada ao provedor de pagamento caso ela exista.
9. WHEN a transição efetiva `PENDING → EXPIRED` devolver capacidade THEN o sistema SHALL registrar `ReservationHoldClosed` com motivo `EXPIRED` para o responsável externo na mesma transação e somente uma vez, independentemente de `DELETE`, consumidor ou reconciliador vencer.
10. WHEN uma confirmação rejeitada ou um fechamento de hold exigir compensação externa THEN o Flash Booking SHALL entregar a mensagem recuperável sem registrar nem aguardar um estado de compensação financeira.

**Independent Test:** Duplicar mensagem, interromper o consumidor entre commit e ack, e recuperar publicação de resultado sem efeito de inventário repetido.

### P1: Concluir cancelamento de reserva confirmada

**User Story:** Como solicitante de cancelamento, quero saber que o pedido está em andamento até o módulo externo concluir todas as suas obrigações e o estoque poder ser liberado.

**Acceptance Criteria:**

1. WHEN `DELETE` solicitar o cancelamento de uma reserva `CONFIRMED` THEN o sistema SHALL gravar `CANCELLATION_PENDING`, um `cancellationId` estável e `ReservationCancellationRequested` na outbox na mesma transação, sem liberar capacidade.
2. IF o módulo externo publicar `ReservationCancellationCompleted` com o `cancellationId` correspondente e identidade de mensagem válida THEN o sistema SHALL concluir `CANCELLED` e devolver a capacidade exatamente uma vez.
3. WHILE o desfecho externo estiver ausente ou incerto THEN o sistema SHALL manter a capacidade comprometida e expor o cancelamento como pendente.
4. IF o mesmo desfecho externo for entregue novamente THEN o sistema SHALL reproduzir o resultado sem criar nova transição ou devolver capacidade duas vezes.
5. IF o módulo externo ainda não puder concluir todas as obrigações THEN ele SHALL manter a solicitação sob retry; enquanto a conclusão não chegar, o Flash Booking SHALL permanecer em `CANCELLATION_PENDING`, conservar a capacidade e não reverter a `CONFIRMED`.
6. IF `DELETE` for repetido enquanto a reserva estiver `CANCELLATION_PENDING` THEN o sistema SHALL retornar o mesmo estado pendente sem criar outro pedido externo.

**Independent Test:** Simular módulo externo que demora, confirma e duplica respostas; verificar estado, outbox e inventário em PostgreSQL real.

### P1: Mostrar o ciclo completo e seus limites no README

**User Story:** Como avaliador, quero distinguir a retenção temporária, o compromisso confirmado e as obrigações externas que ficam fora do Flash Booking.

**Acceptance Criteria:**

1. WHEN o README descrever a reserva THEN ele SHALL mostrar `PENDING`, `CONFIRMED`, `CANCELLATION_PENDING`, `CANCELLED` e `EXPIRED` como estados de reserva, sem incluir status de pagamento.
2. WHEN o README mostrar o contexto C4 THEN ele SHALL identificar Flash Booking, o responsável externo e as responsabilidades de cada sistema.
3. WHEN o README mostrar os containers C4 THEN ele SHALL distinguir APIs, worker, PostgreSQL, fila de confirmação, fila dirigida ao responsável externo e limites entre os sistemas, sem apresentar tabelas como containers.
4. WHEN o README mostrar a dinâmica THEN um diagrama SHALL cobrir confirmação aceita, mensagem tardia, duplicidade, corrida com expiração e cancelamento pendente até conclusão externa.
5. WHEN a validação documental rodar THEN os SVGs SHALL ser legíveis, acessíveis, vinculados no README e coerentes com os contratos e comportamento verificados, sem alegar compra paga ou implantação AWS.

**Independent Test:** Inspecionar README e SVGs renderizados e executar o gate documental contra os contratos atuais e propostos.

## Edge Cases

- IF um módulo externo concluir seu trabalho antes de receber `ReservationConfirmed` THEN sua própria compensação SHALL ser definida no contrato de integração; o Flash Booking não SHALL simular sucesso retroativo após o prazo.
- IF duas mensagens distintas disputarem a mesma reserva THEN apenas uma SHALL conquistar `PENDING → CONFIRMED`; as demais SHALL receber desfecho estável e não alterar inventário.
- IF `endsAt` do evento chegar depois da criação da reserva THEN ele SHALL bloquear novas reservas, mas não SHALL invalidar uma confirmação de reserva ainda dentro de `expiresAt`.
- IF a fila atrasar além de `expiresAt` THEN o resultado SHALL ser rejeição explícita, nunca confirmação baseada apenas no horário declarado pelo produtor.

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| CONFIRM-01 | Confirmar reserva elegível | Execute | In progress |
| CONFIRM-02 | Preservar desfechos sob concorrência | Execute | In progress |
| CONFIRM-03 | Entregar decisão confiável | Execute | In progress |
| CONFIRM-04 | Mostrar ciclo e limites no README | Execute | In progress |
| CONFIRM-05 | Concluir cancelamento de reserva confirmada | Execute | In progress |

**Coverage:** 5 requisitos mapeados ao desenho, às tarefas e aos testes previstos. A implementação e reconciliação documental permanecem em andamento.

## Success Criteria

- [ ] Uma reserva elegível pode terminar `CONFIRMED` sem conhecer pagamento.
- [ ] Corridas e retries não geram oversell nem devolução de estoque confirmado.
- [ ] O módulo externo distingue solicitação recebida de confirmação aceita.
- [ ] Cancelamento de reserva confirmada não libera estoque antes do desfecho externo positivo.
- [ ] O README explica o ciclo confirmado e seus limites com diagramas alinhados ao runtime.
