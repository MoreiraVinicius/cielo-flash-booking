# AWS demo environment

This composition creates the full demo topology from the network, data-plane, compute, and edge modules. It intentionally has no backend block: after applying `infra/bootstrap` once, initialize with a non-versioned backend configuration file that points to the private state bucket.

Copy `demo.tfvars.example` to an ignored `demo.tfvars`, replace every placeholder, and run `terraform init -backend=false` plus `terraform validate` without credentials. An actual `plan` or `apply` requires temporary AWS credentials and explicit deployment authorization.

## Resumo executivo do evento

O resumo e opcional e usa uma unica chave global, desligada por padrao. A geracao e automatica: quando um evento elegivel atinge `endsAt`, o worker salva o apurado e, se configurado, publica o Markdown no Discord na proxima varredura (intervalo default de 30 segundos). Nao existe comando manual por evento. Mantenha a chave ligada ate os eventos da apresentacao terminarem e a publicacao concluir; uma ativacao nova nao recupera eventos iniciados antes dela.

### Configurar o Discord

1. No canal do Discord, crie um Incoming Webhook em **Editar canal > Integracoes > Webhooks** e copie a URL.
2. No AWS Secrets Manager, crie um segredo de texto simples e guarde a URL inteira como `SecretString`.
3. Copie o ARN do segredo e preencha somente `discord_webhook_secret_arn` no `infra/environments/demo/demo.tfvars` local. O arquivo e ignorado pelo Git; nunca cole a URL nele. Deixe a propriedade vazia para nao publicar.
4. Depois de autorizada e aplicada a configuracao Terraform, somente a role do worker pode chamar `secretsmanager:GetSecretValue` no ARN indicado. A URL nao vai para task definition, variavel de ambiente, logs ou Terraform.

O exemplo versionado `demo.tfvars.example` deixa o ARN vazio. O worker recebe apenas esse ARN em `EXECUTIVE_SUMMARY_DISCORD_WEBHOOK_SECRET_ARN`. Alarmes de contexto sao allowlisted em `executive_summary_operational_alarms` dentro da chamada do modulo `compute` em `main.tf`; cada item associa o nome CloudWatch a um rotulo simples. Nova Micro e `sa-east-1` sao os defaults, e cada evento elegivel com reservas tem no maximo uma inferencia.

### Ligar e desligar a chave global

O operador precisa de credenciais temporarias que possam assumir a `ApiInvokerRole`; a origem de rede tambem precisa estar na allowlist `allowed_cidrs`. Com o diretorio `infra/environments/demo` inicializado e o estado da demo configurado, use PowerShell para assumir a role e assinar as chamadas com SigV4:

```powershell
$api = terraform output -raw api_invoke_url
$roleArn = terraform output -raw api_invoker_role_arn
$eventId = "<id-do-evento>"
$credentials = aws sts assume-role --role-arn $roleArn --role-session-name flash-booking-summary | ConvertFrom-Json
$accessKey = $credentials.Credentials.AccessKeyId
$secretKey = $credentials.Credentials.SecretAccessKey
$sessionToken = $credentials.Credentials.SessionToken
$sigv4 = "aws:amz:sa-east-1:execute-api"

curl.exe --aws-sigv4 $sigv4 --user "${accessKey}:${secretKey}" `
  -H "x-amz-security-token: $sessionToken" -H "Content-Type: application/json" `
  -X PUT -d '{"enabled":true}' "$api/executive-summary/activation"

# Consulte o resumo salvo; a leitura nao executa Bedrock, CloudWatch ou Discord.
curl.exe --aws-sigv4 $sigv4 --user "${accessKey}:${secretKey}" `
  -H "x-amz-security-token: $sessionToken" `
  "$api/events/$eventId/executive-summary"

# Ao terminar a demonstracao, feche a janela global de geracao.
curl.exe --aws-sigv4 $sigv4 --user "${accessKey}:${secretKey}" `
  -H "x-amz-security-token: $sessionToken" -H "Content-Type: application/json" `
  -X PUT -d '{"enabled":false}' "$api/executive-summary/activation"
```

Ligue a chave antes de iniciar os eventos da apresentacao e deixe-a ligada ate cada `endsAt`; eventos ja em andamento antes da ativacao ficam fora dessa janela. A resposta do GET informa `DISABLED`, `NOT_ELIGIBLE` ou `SCHEDULED` enquanto nao ha relatorio, e depois `PARTIAL` ou `READY` com `markdown`, `generatedAt`, `asOf` e `deliveryStatus`. Um evento inexistente retorna `404`. Se `deliveryStatus` for `FAILED` ou `UNKNOWN`, o mesmo resumo nao e reenviado automaticamente; verifique o segredo/Discord sem editar a linha salva.

Nenhum deploy, chamada AWS real ou publicacao Discord real faz parte dos testes locais desta feature.
