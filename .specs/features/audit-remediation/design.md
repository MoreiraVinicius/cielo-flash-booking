# Audit Remediation Design

## Decisions

1. PostgreSQL fornece o instante de criacao e selecao temporal. O relogio Java permanece apenas onde nao decide estado persistido.
2. O filtro HTTP envolve o `ServletInputStream` e encerra a leitura ao ultrapassar o limite, cobrindo corpos sem tamanho declarado.
3. Respostas idempotentes preservam o resultado original, mas o campo operacional `correlationId` e reidratado para a requisicao atual.
4. A notificacao usa claim com lease em transacao curta, chamada SES fora da transacao e conclusao em nova transacao.
5. Event persistence migra de JPA para `JdbcTemplate`. A mudanca reduz uma tecnologia sem alterar o schema ou o contrato.
6. O primeiro deploy usa apply direcionado somente ao repositorio ECR, push da imagem e apply completo. Nenhum provisionador local entra no Terraform.
7. Um topico SNS central recebe alarmes de borda, compute e dados. A inscricao por e-mail continua exigindo confirmacao do destinatario.

## Components

| Area | Change |
| --- | --- |
| Reservation persistence | Database clock, stable expiry predicate, immutable customer upsert |
| HTTP boundary | Streaming body limit, current correlation ID, throwable logging |
| Cache | Healthy misses bypass outage bulkhead; success resets failure window |
| Worker | Scheduler pool, faster reconciler, leased email claim, retention cleanup |
| Persistence stack | JDBC replaces the isolated JPA adapter |
| Terraform | S3 backend declaration, ECS health checks/autoscaling, SNS-backed alarms |
| Delivery | GitHub Actions and corrected runbook/README claims |

## Failure Semantics

- Exceeding the body or idempotency-key limit returns a stable client error.
- A cache outage may shed database fallback load; a normal cache miss may not.
- Email delivery is at-least-once. A lease prevents concurrent sends but cannot prove the result of a provider timeout.
- Alarm creation is independent of subscription confirmation.
