# Notificar a criação da reserva por e-mail de forma assíncrona

- Estado: aceita
- Referências: [modelagem de dados](../data-model.md), [modelo de reserva](0002-reserva-temporaria-com-motivo-de-encerramento.md), [documentação do Amazon SES](https://docs.aws.amazon.com/ses/latest/dg/Welcome.html).

## Contexto

O cliente precisa receber os dados necessários para consultar sua reserva. A operação não representa compra ou pagamento confirmado; chamar o e-mail de “confirmação de compra” criaria uma promessa de negócio que o domínio não suporta. Enviar e-mail dentro da transação de reserva também aumentaria a latência e permitiria que uma indisponibilidade do provedor de e-mail bloqueasse o inventário.

## Decisão

Após a criação da reserva, gravar `ReservationCreated` no outbox na mesma transação do inventário. O publicador encaminha o evento para uma fila SQS de notificações, separada da fila de expiração. Um worker consome a mensagem e envia pelo Amazon SES um e-mail com identificador da reserva, evento, quantidade e `expiresAt`, contendo a frase explícita de que a reserva é temporária e não confirma compra ou pagamento.

A entrega é “pelo menos uma vez”. O worker registra um identificador de notificação único para evitar reenvios conhecidos, mas uma falha ambígua após o SES aceitar a mensagem ainda pode produzir duplicidade. Falha de e-mail usa tentativas limitadas, DLQ e alarme; nunca desfaz uma reserva válida.

No Docker Compose, Mailpit substitui o SES. Na demo AWS, remetente e destinatários usados na apresentação devem estar verificados enquanto a conta SES permanecer no sandbox. Envio para destinatários arbitrários exige solicitar acesso de produção e fica fora da demonstração.

## Alternativas consideradas

Envio síncrono pelo controller acoplaria disponibilidade do SES ao caminho crítico da reserva. SNS não oferece o mesmo contrato de e-mail transacional e template. Não notificar deixaria o usuário dependente apenas da resposta HTTP, que pode ser perdida após o commit.

## Consequências

O mesmo código de notificação existe na demo e na alta carga. Apenas quantidade de workers, parâmetros da fila e limites de envio variam por Terraform. O conteúdo deve evitar dados desnecessários e logs devem mascarar o endereço do destinatário.
