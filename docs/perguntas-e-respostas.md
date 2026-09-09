# Perguntas e respostas sobre o Flash Booking

Este documento explica o comportamento pretendido pelas especificações. Ele não substitui testes executados: uma propriedade só é comprovada quando a implementação produz a evidência prevista nas tasks.

## As especificações garantem que não haverá oversell?

As especificações definem um mecanismo que deve impedir oversell quando implementado e testado corretamente. Elas não são, por si só, a prova de que o sistema em execução é livre de erros.

A reserva precisa executar uma atualização condicional no PostgreSQL, equivalente a:

```sql
UPDATE event
SET available = available - :quantity
WHERE id = :eventId
  AND available >= :quantity;
```

Se nenhuma linha for alterada, não há capacidade suficiente e a API retorna `409`. A criação do cliente, da reserva, o decremento do estoque e o evento de outbox pertencem à mesma transação. Ou todos são confirmados, ou todos sofrem rollback.

## Como esse mecanismo se comporta em concorrência?

Exemplo: o evento possui um ingresso disponível e duas instâncias de `command-api` tentam reservar um ingresso ao mesmo tempo.

1. A primeira transação que alterar a linha do evento reduz `available` de `1` para `0`.
2. A segunda transação aguarda a concorrente e reavalia a condição `available >= 1`.
3. Como o valor já é `0`, ela não altera nenhuma linha e recebe `409`.

Não importa se ambas as chamadas leram uma disponibilidade antiga antes: a decisão de aceitar a reserva ocorre na escrita condicional autoritativa, não na leitura anterior nem no cache.

## Quais cenários de oversell são protegidos?

| Situação | Regra definida | Resultado esperado |
| --- | --- | --- |
| Duas reservas disputam o último ingresso | Decremento condicional de `available` | Apenas uma reserva é aceita. |
| Uma reserva pede mais que o disponível | Condição `available >= quantity` | Não há decremento parcial; resposta `409`. |
| Cache informa valor antigo | Cache nunca autoriza comando | O comando revalida o estoque no banco. |
| Cliente repete um POST | `Idempotency-Key` persistida | A resposta anterior é devolvida, sem novo decremento. |
| SQS reentrega expiração | Transição condicional `PENDING → EXPIRED` | Só a primeira mensagem devolve estoque. |
| Cancelamento e expiração concorrem | Ambos exigem que a reserva ainda esteja `PENDING` | Só o vencedor incrementa `available`. |
| Falha entre reserva e publicação | Outbox na mesma transação | Não existe reserva confirmada sem evento durável de expiração. |
| Banco falha antes do commit | Transação atômica | Nenhum efeito parcial é confirmado. |

Como segunda barreira, o schema exige `0 <= available <= capacity`, quantidade positiva, vínculo obrigatório da reserva a evento e cliente, e motivos obrigatórios em estados terminais.

## Como é tratada a consistência eventual?

Ela se aplica somente às consultas. Os dois GETs usam cache-aside no Valkey, com TTL máximo de um segundo e invalidação após o commit que altera evento ou reserva.

Uma pessoa pode visualizar por pouco tempo uma disponibilidade antiga. Isso não permite oversell, pois POST e DELETE não usam cache para decidir capacidade. Na demo, a defasagem causada pelo cache é limitada pelo TTL. Na arquitetura de alta carga, uma consulta de disponibilidade que não encontra cache pode usar réplica Aurora; nesse caso também existe o atraso de replicação, aceito apenas para exibição.

## Por que Valkey em vez de Redis OSS?

O código Java usa Spring Data Redis com Lettuce e fala um protocolo compatível com os dois engines. A escolha por ElastiCache for Valkey não altera domínio, controllers, schema ou contrato HTTP.

Valkey foi escolhido porque não há dependência de módulo exclusivo do Redis OSS e a AWS informa compatibilidade com clientes Redis OSS, menor custo por nó e governança aberta. Redis OSS continua uma alternativa válida se familiaridade de mercado, suporte específico ou uma incompatibilidade comprovada passarem a justificar a troca. A decisão completa está no [ADR 0005](adr/0005-cache-valkey-compartilhado-e-binario-unico.md).

## Como uma reserva é ligada ao cliente sem salvar o e-mail duas vezes?

`Reservation.customerId` é uma chave estrangeira para `Customer.id`. A tabela `Customer` possui somente uma coluna `email`, com unicidade. Não existe `emailNormalized` nem coluna equivalente.

Antes de buscar ou persistir o cliente, o Java remove espaços externos, converte o endereço para minúsculas usando `Locale.ROOT` e valida o resultado. Apenas essa forma canônica é gravada em `Customer.email`. O upsert atômico reutiliza o mesmo cliente em reservas concorrentes do mesmo endereço.

## Como o cliente recebe a reserva?

A criação da reserva grava `ReservationCreated` no outbox, na mesma transação do estoque. Um worker publica o evento em uma fila SQS de notificação e outro processamento envia e-mail via SES; no ambiente local, Mailpit substitui o SES.

O e-mail confirma uma reserva temporária, com identificador, evento, quantidade e vencimento. Não confirma compra ou pagamento. Falha de envio não desfaz a reserva: usa tentativas limitadas, DLQ e alarme.

## Como a API é protegida e como o custo é limitado?

O API Gateway REST regional é o único ponto público. Ele exige IAM/SigV4, aplica resource policy, WAF e throttling antes de encaminhar tráfego pelo VPC Link para o ALB interno. GETs chegam somente ao `query-api`; POSTs e DELETE chegam somente ao `command-api`.

Na demo, os limites iniciais são 20 requisições/s com rajada de 40 para GET e 5 requisições/s com rajada de 10 para comandos. Há máximos de tasks ECS e alertas de Budget em 50%, 80% e 100% de US$5. Essas barreiras reduzem abuso e custo, mas não são um teto financeiro matematicamente absoluto.

## Por que consultas e comandos ficam em containers separados?

Consultas crescem em momentos diferentes de reservas: por exemplo, divulgação e proximidade do evento aumentam GETs, enquanto a abertura da venda aumenta comandos e contenção no estoque. `query-api`, `command-api` e `worker` são serviços separados, com métricas e escala próprias, mas usam a mesma imagem Java, módulos de domínio, migrations e contratos.

Na arquitetura de alta carga, apenas topologia e parâmetros Terraform mudam: quantidade de tasks, Aurora, RDS Proxy, Valkey Multi-AZ e políticas de escala. Nenhuma regra de negócio Java pode mudar apenas por haver mais carga.

## O que ainda precisa ser provado?

Antes de afirmar que o sistema efetivamente garante essas propriedades, a implementação precisa passar por testes de concorrência entre processos distintos, retries idempotentes, mensagens duplicadas, corrida entre cancelamento e expiração, cache indisponível, migrations e contratos HTTP. A arquitetura de alta carga também permanece sem evidência remota enquanto seu Terraform não for autorizado e aplicado.

Referências: [modelo de dados](data-model.md), [design da demo](../.specs/features/flash-booking-demo/design.md), [design de alta carga](../.specs/features/flash-booking-high-load/design.md), [ADR 0005](adr/0005-cache-valkey-compartilhado-e-binario-unico.md), [ADR 0010](adr/0010-cliente-como-entidade-da-reserva.md), [ADR 0011](adr/0011-notificacao-assincrona-de-reserva-por-email.md), [ADR 0012](adr/0012-autenticacao-e-protecao-de-custos-na-borda.md) e [ADR 0013](adr/0013-separar-servicos-de-consulta-e-comando.md).
