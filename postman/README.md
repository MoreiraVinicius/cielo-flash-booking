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

O fluxo cobre:

| Requests | Resultado esperado |
| --- | --- |
| 01–02 | Evento criado e consultado com disponibilidade 2. |
| 03–05 | Reserva PENDING, retry idempotente com o mesmo id e leitura da reserva. |
| 06 | 409 application/problem+json para capacidade insuficiente. |
| 07–08 | Reserva CANCELLED, motivo CANCELLED_BY_REQUEST e disponibilidade devolvida para 2. |
| 09 | 400 application/problem+json sem Idempotency-Key. |
| 10–11 | Venda futura criada e reserva antes de startsAt rejeitada com 409 application/problem+json. |

Para encerrar:

    docker compose down

## Executar na AWS

A demo AWS é temporária e atualmente não está ativa. Execute esta pasta somente depois de uma autorização de deploy e de seguir o [runbook](../docs/demo-runbook.md).

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
