# Flash Booking

Vocabulário do núcleo de reserva temporária de ingressos.

## Language

**Reserva**:
Bloqueio temporário de uma quantidade de ingressos de um evento. Sua aceitação não representa uma compra definitiva.
_Avoid_: Compra confirmada, venda concluída.

**Cliente**:
Pessoa titular de uma ou mais reservas, identificada internamente e contatada pelo e-mail informado na criação.
_Avoid_: Usuário IAM, operador, entrevistador.

**Operador da demonstração**:
Pessoa autenticada na borda AWS para invocar os endpoints durante avaliação. Não representa necessariamente o cliente ligado à reserva.
_Avoid_: Cliente, titular da reserva.

**Reserva pendente**:
Reserva ainda não encerrada por cancelamento ou expiração. Seu prazo de validade é limitado.

**Reserva cancelada**:
Reserva encerrada por solicitação recebida e decidida antes do prazo, com código e descrição do motivo preservados.

**Reserva expirada**:
Reserva cujo prazo já foi alcançado quando o PostgreSQL decidiu o encerramento, com código e descrição do motivo preservados. O vencimento prevalece mesmo quando uma solicitação de cancelamento materializa o estado terminal.

**Motivo de encerramento**:
Causa registrada para o cancelamento ou a expiração de uma reserva, identificada por um código e explicada por uma descrição.
_Avoid_: Motivo de falha de pagamento, motivo de compra recusada.

**Disponibilidade exibida**:
Quantidade de ingressos retornada por consulta de evento. Pode permanecer defasada por até um segundo por causa do cache e nunca autoriza uma reserva.
_Avoid_: Estoque autoritativo.

**Estoque autoritativo**:
Disponibilidade persistida no PostgreSQL e atualizada pela transação de reserva, cancelamento ou expiração.
_Avoid_: Valor de cache.

**Notificação de reserva**:
Mensagem enviada ao cliente para informar que uma reserva temporária foi criada e quando expira.
_Avoid_: Confirmação de compra, confirmação de pagamento.
