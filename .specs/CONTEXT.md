# Flash Booking Glossary

## Language

**Reserva**: bloqueio temporário de uma quantidade de ingressos de um evento. Sua aceitação não representa compra definitiva.

**Cliente**: pessoa titular de uma ou mais reservas, identificada internamente e contatada pelo e-mail informado na criação.

**Operador da demonstração**: pessoa autenticada na borda AWS para invocar endpoints durante a avaliação. Não é o cliente ligado à reserva.

**Reserva pendente**: reserva ainda não encerrada por cancelamento ou expiração.

**Reserva cancelada**: reserva encerrada por solicitação decidida antes do prazo, com código e descrição preservados.

**Reserva expirada**: reserva cujo prazo foi alcançado quando o PostgreSQL decidiu o encerramento. O vencimento prevalece quando uma solicitação de cancelamento materializa o estado terminal.

**Motivo de encerramento**: causa registrada para cancelamento ou expiração, identificada por código e descrição.

**Disponibilidade exibida**: quantidade retornada por consulta de evento. Pode ficar defasada por até um segundo por causa do cache e nunca autoriza uma reserva.

**Estoque autoritativo**: disponibilidade persistida no PostgreSQL e atualizada pela transação de reserva, cancelamento ou expiração.

**Notificação de reserva**: mensagem que informa ao cliente a criação e o prazo de uma reserva temporária. Não confirma compra ou pagamento.
