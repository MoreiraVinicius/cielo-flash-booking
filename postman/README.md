# Postman: documentação executável

O projeto possui **uma única coleção**: flash-booking-aws.postman_collection.json. O nome do arquivo foi preservado para não quebrar quem já o importou, mas ele agora contém dois fluxos:

- **Local | fluxo completo do case**: usa o Docker Compose e não exige AWS.
- **AWS | fronteira e fluxo do case**: usa API Gateway, IAM e AWS Signature v4.

As duas pastas cobrem os cinco endpoints do case. A pasta Local também demonstra idempotência, conflito de capacidade, cancelamento, retorno de capacidade, erro por falta de Idempotency-Key e reserva rejeitada antes da abertura de uma flash sale.

## Importar

No Postman, importe:

1. flash-booking-aws.postman_collection.json;
2. flash-booking.local.postman_environment.json;
3. flash-booking.aws.postman_environment.json.

Importar os dois ambientes é seguro. O ambiente AWS não possui URL, chave, token ou e-mail preenchidos.

## Executar localmente

Pré-requisito: Docker Desktop saudável.

    docker compose up --build --detach

No Postman:

1. selecione o ambiente **Flash Booking Local**;
2. abra **Local | fluxo completo do case**;
3. envie os requests de 01 até 11, em ordem.

O ambiente Local separa os endereços porque a aplicação executa dois modos HTTP:

| Variável | Valor | Serviço |
| --- | --- | --- |
| commandBaseUrl | http://localhost:8082 | POST e DELETE |
| queryBaseUrl | http://localhost:8081 | GET |

Todos os testes devem ficar verdes. A requisição 01 inicia uma execução nova e recria identificadores e chaves idempotentes. Depois da requisição 03, o e-mail de reserva pode ser conferido no Mailpit em http://localhost:8025.

## Criar massa e reutilizar respostas na apresentação

Os requests de criação já possuem um **Body** seguro e editável. Use-os como massa inicial da apresentação:

| Request | Body demonstrado | O que você pode alterar |
| --- | --- | --- |
| 01 | Criar evento imediato | `name` e `capacity: 2` | nome e capacidade do evento |
| 03 | Criar reserva | `quantity: 1` e `customer` | quantidade, nome e e-mail do cliente |
| 10 | Criar venda futura | evento, capacidade, `startsAt` e `endsAt` | nome e capacidade; os horários são gerados automaticamente |

O encadeamento é automático. Não copie o ID da resposta:

1. Envie **01 | Criar evento imediato**. Em **Scripts > Post-response**, o código salva `event.id` em `eventId`.
2. Os requests 02, 03, 04, 06 e 08 usam `{{eventId}}` na URL. O Postman substitui a variável antes de enviar.
3. Envie **03 | Criar reserva**. O código salva `reservation.id` em `reservationId`.
4. Os requests 05 e 07 usam `{{reservationId}}`. O request 10 salva o novo ID em `futureEventId`, que o 11 usa.

Para mostrar isso ao vivo, abra a coleção no Postman, escolha **Variables** e observe a coluna **Current value** após cada envio. Os testes também exibem `guarda eventId...` e `guarda reservationId...` em verde, provando que a captura ocorreu.

O fluxo cobre:

| Requests | Resultado esperado |
| --- | --- |
| 01–02 | Evento criado e consultado com disponibilidade 2. |
| 03–05 | Reserva PENDING, retry idempotente com o mesmo id e leitura da reserva. |
| 06 | 409 application/problem+json para capacidade insuficiente. |
| 07–08 | Reserva CANCELLED, motivo CANCELLED_BY_REQUEST e disponibilidade devolvida para 2. |
| 09 | 400 application/problem+json sem Idempotency-Key. |
| 10–11 | Venda futura criada e reserva antes de startsAt rejeitada com 409 application/problem+json. |
| 12–15 | Bateria Local: payload e endpoint incompatíveis retornam 409, conflito de capacidade é reproduzido, e chave com 129 caracteres retorna 400. |

## Rodar a bateria de idempotência

O **Run** do Postman é o folder selecionado no Collection Runner; o histórico de uma execução não é versionado. Para a apresentação, use a sequência já versionada:

1. Com **Flash Booking Local** selecionado, abra o folder **Local | fluxo completo do case** e clique em **Run**.
2. Execute os requests 01–15 em ordem. Os requests 12–15 são a bateria de idempotência.
3. Mostre os resultados: replay idêntico retorna `201`; Body ou endpoint incompatível retorna `409 resource-conflict`; chave ausente ou longa retorna `400 invalid-request`; o conflito de capacidade reaparece como o `409` persistido.

Na AWS autorizada, preencha o ambiente, abra **AWS | fronteira e fluxo do case** e clique em **Run**. Execute 00–13: os requests 09–13 aplicam os mesmos erros e replay com AWS Signature v4. O request 00 permanece sem assinatura de propósito e deve retornar `403`.

Para encerrar:

    docker compose down

## Executar na AWS

A demo AWS é temporária e atualmente não está ativa. Execute esta pasta somente depois de uma autorização explícita de deploy. A arquitetura, os requisitos e as evidências da demo ficam em [`.specs/features/flash-booking-demo/`](../.specs/features/flash-booking-demo/).

Depois de um terraform apply autorizado:

1. obtenha api_invoke_url e api_invoker_role_arn nos outputs Terraform;
2. assuma a ApiInvokerRole com um principal permitido em trusted_principal_arns;
3. selecione o ambiente **Flash Booking AWS**;
4. preencha **somente no ambiente ativo**:

| Variável | Valor necessário |
| --- | --- |
| commandBaseUrl | valor completo de api_invoke_url |
| queryBaseUrl | o mesmo valor de api_invoke_url |
| awsRegion | região do ambiente, normalmente sa-east-1 |
| awsAccessKeyId | credencial temporária da role |
| awsSecretAccessKey | credencial temporária da role |
| awsSessionToken | token temporário da role |
| customerEmail | destinatário aceito pelo SES da conta |

Em Authorization, a coleção já configura **AWS Signature v4** para o serviço execute-api. Não altere os demais requests para No Auth: somente 00 | Rejeitar chamada sem SigV4 usa noauth de propósito e deve retornar 403.

Depois disso, envie a pasta **AWS | fronteira e fluxo do case** de 00 até 08, em ordem. Ela valida a fronteira IAM, os cinco endpoints, idempotência, conflito de capacidade e cancelamento.

## O que esta coleção prova, e o que não prova

Ela prova o contrato HTTP e torna visíveis as regras mais importantes do case. Ela não substitui os testes Java/Testcontainers que provam concorrência, relógio do PostgreSQL, constraints, outbox e mensagens duplicadas.

Também não afirma que a demo AWS continua ativa, que o throttling produz 429 determinístico ou que a arquitetura high-load foi provisionada. Esses limites e a evidência histórica estão no [README principal](../README.md).

## Executar fora do Postman

Os arquivos seguem Postman Collection/Environment v2.1 e podem ser executados pelo Newman depois de instalar as dependências localmente. O repositório não exige Newman para o gate básico: valide a estrutura com:

    powershell -File scripts/validate-postman.ps1

O validador rejeita JSON inválido, ambientes incompletos, ausência dos assertions principais, URL AWS ativa, segredo versionado e e-mail pessoal na configuração versionada.
