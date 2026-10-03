# Semeadura AWS com Faker.js

Este utilitário cria exatamente 1.000 clientes únicos e reservas persistidas na demo AWS. Ele não é um teste de carga: limita o início a quatro comandos por segundo e falha ao receber uma resposta diferente de `201` durante a massa.

O utilitário cria reservas `PENDING` e cancela a reserva de preflight; ele não envia resoluções pelas filas e não mede o ciclo de confirmação. A confirmação está implementada no runtime local, mas este utilitário AWS não a aciona nem prova a integração com um responsável real.

Antes de executar, deixe `notification_consumer_enabled = false` aplicado no worker. O utilitário usa o perfil AWS local para assumir a `ApiInvokerRole`; nunca passe ou grave chaves no comando.

```powershell
npm --prefix performance/aws-seed install
node performance/aws-seed/seed.mjs --api-url <api_invoke_url> --region sa-east-1 --profile <aws_profile> --role-arn <api_invoker_role_arn>
```

Antes da massa, ele cria e consulta um evento, cria e consulta uma reserva e a cancela: os cinco endpoints públicos recebem uma chamada bem-sucedida. A massa posterior cria um evento de capacidade 1.000 e reserva uma unidade para cada cliente Faker.js, com e-mail único `@example.test`.

O relatório em `results/` contém somente IDs, contagens, códigos HTTP e horários; não contém segredos, nomes ou e-mails.
