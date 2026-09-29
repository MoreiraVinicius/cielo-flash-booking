# Flash Booking Glossary

## Language

**Reserva**: vínculo de uma quantidade de ingressos a um cliente e um evento. Começa como retenção temporária e pode ser confirmada pelo responsável externo quando todas as pendências estiverem resolvidas. A confirmação compromete os ingressos, sem declarar pagamento ou emissão.

**Cliente**: pessoa titular de uma ou mais reservas, identificada internamente e contatada pelo e-mail informado na criação.

**Operador da demonstração**: pessoa autenticada na borda AWS para invocar endpoints durante a avaliação. Não é o cliente ligado à reserva.

**Reserva pendente**: reserva temporariamente retida, ainda não confirmada nem encerrada por cancelamento ou expiração.

**Reserva cancelada (`CANCELLED`)**: reserva pendente encerrada antes do prazo, reserva expirada por pedido tardio ou reserva confirmada cujo cancelamento externo foi concluído; o estado terminal preserva código e descrição do motivo.

**Reserva expirada**: reserva cujo prazo foi alcançado quando o PostgreSQL decidiu o encerramento. O vencimento prevalece quando uma solicitação de cancelamento materializa o estado terminal.

**Motivo de encerramento**: causa registrada para cancelamento ou expiração, identificada por código e descrição.

**Disponibilidade exibida**: quantidade retornada por consulta de evento. Pode ficar defasada por até um segundo por causa do cache e nunca autoriza uma reserva.

**Estoque autoritativo**: disponibilidade persistida no PostgreSQL e atualizada pela transação de reserva, cancelamento ou expiração.

**Notificação de reserva**: mensagem que informa ao cliente a criação e o prazo de uma reserva temporária. Não confirma compra ou pagamento.

## Ciclo confirmado da reserva

Os termos abaixo definem o [ciclo de confirmação externa](features/reservation-confirmation/spec.md).

**Reserva confirmada (`CONFIRMED`)**: reserva cujos ingressos permanecem comprometidos após o único responsável externo declarar todas as pendências resolvidas. Não é um status financeiro.

**Cancelamento pendente (`CANCELLATION_PENDING`)**: reserva antes confirmada cujo cancelamento foi solicitado e ainda depende da conclusão externa. Os ingressos continuam comprometidos até essa conclusão.

**Responsável externo**: único módulo lógico que resolve todas as pendências e atesta a resolução integral antes da confirmação da reserva.

**`ReservationHeld`**: fato de que uma reserva `PENDING` reteve temporariamente os ingressos, permitindo ao responsável externo iniciar seu trabalho. Não significa confirmação definitiva.

**Resultado de confirmação**: decisão de aceitar ou rejeitar a resolução externa de uma reserva, comunicada ao responsável externo.

**Conclusão do cancelamento (`ReservationCancellationCompleted`)**: mensagem correlacionada à solicitação de cancelamento que declara concluídas todas as obrigações externas; somente essa conclusão permite liberar a capacidade de uma reserva em `CANCELLATION_PENDING`.

**`ReservationHoldClosed`**: fato de que uma retenção `PENDING` terminou como `CANCELLED` ou `EXPIRED`, permitindo ao responsável externo interromper ou compensar seu trabalho.
