# Pausar notificações durante a semeadura AWS

Esta operação preserva clientes, reservas, eventos de outbox e mensagens SQS. Ela pausa somente o consumidor de notificações da task `worker`, impedindo chamadas ao SES enquanto `notification_consumer_enabled=false` estiver aplicado.

Antes da semeadura, no `infra/environments/demo/demo.tfvars` local e ignorado pelo Git, defina:

```hcl
notification_consumer_enabled = false
```

Revise e aplique a alteração exclusivamente no ambiente demo:

```powershell
terraform -chdir=infra/environments/demo plan
terraform -chdir=infra/environments/demo apply
```

Após a semeadura, restaure no mesmo arquivo:

```hcl
notification_consumer_enabled = true
```

Execute novamente `plan` e `apply`. A pausa não remove mensagens da SQS; ao reativar o consumidor, mensagens acumuladas podem ser processadas. Confirme o estado da fila e da DLQ no CloudWatch antes de considerar a entrega de notificações normalizada.
