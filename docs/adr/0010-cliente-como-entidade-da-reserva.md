# Relacionar cada reserva a um cliente persistido

- Estado: aceita
- Referências: [modelagem de dados](../data-model.md), [especificação da demo](../../.specs/features/flash-booking-demo/spec.md).

## Contexto

O identificador da reserva não informa quem a solicitou nem fornece um destino confiável para comunicação. O case não inclui cadastro de clientes, login de consumidor ou endpoints adicionais, mas uma reserva utilizável precisa manter o vínculo com a pessoa que a realizou.

## Decisão

Modelar `Customer` como entidade persistida e relacionar `Reservation.customerId` por chave estrangeira obrigatória. `POST /events/{id}/reservations` recebe `customer.name` e `customer.email`. O código Java remove espaços externos, converte o e-mail para minúsculas com `Locale.ROOT`, valida e persiste somente o resultado na coluna única `email`; não existe uma segunda coluna de normalização. A mesma transação faz upsert atômico do cliente pela chave única, bloqueia a capacidade e cria a reserva. Se o e-mail já existir, o nome e o instante de atualização recebem os dados mais recentes; concorrência sobre o mesmo e-mail não cria clientes duplicados.

`GET /reservations/{id}` retorna os dados mínimos do cliente junto da reserva. Não será criado endpoint de cadastro de cliente: a identidade de quem administra a API é tratada pelo API Gateway e não deve ser confundida com o cliente associado à reserva.

## Alternativas consideradas

Guardar apenas nome e e-mail diretamente na reserva reduziria uma tabela, mas duplicaria dados e impediria representar que várias reservas pertencem ao mesmo cliente. Criar um serviço completo de clientes e autenticação do consumidor aumentaria o escopo sem ser exigido pelos cinco endpoints. Receber somente `customerId` deixaria o fluxo impossível de executar sem um endpoint externo inexistente.

## Consequências

Nome e e-mail passam a ser dados pessoais: não podem aparecer integralmente em logs, mensagens de erro ou métricas. O banco mantém unicidade sobre a única coluna `email`; o Java é responsável por sempre aplicar a mesma transformação antes de procurar ou gravar um cliente. Escritas administrativas que contornem a aplicação podem violar a forma canônica e devem ser proibidas. Uma solução real deve substituir a identificação por e-mail por uma identidade verificada e definir retenção e exclusão de dados; isso não é necessário para demonstrar o case.
