# Executar e apresentar a demo

Este roteiro permite que outra pessoa reproduza a demo localmente, valide o Terraform sem criar recursos e, quando tiver credenciais temporárias autorizadas, faça uma demonstração AWS curta e destrua tudo ao final.

## Pré-requisitos

- JDK 21, Docker Desktop com o engine em execução, Terraform 1.8 ou superior e AWS CLI 2.32 ou superior.
- `k6` somente para repetir o benchmark local.
- Uma conta AWS autorizada, um perfil temporário e um remetente SES verificado somente para `plan` ou `apply` remoto. Nenhuma chave AWS deve entrar em `demo.tfvars`, imagem Docker, repositório ou contêiner.

## Validar localmente

Na raiz do repositório, execute:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify -Pintegration
docker compose up --build --detach
.\scripts\compose-smoke.ps1
docker compose --profile concurrency down
```

O smoke testa o fluxo entre serviços: cria evento pelo `command-api` em `http://localhost:8082`, consulta-o pelo `query-api` em `http://localhost:8081`, cria e consulta uma reserva e espera o e-mail aparecer no Mailpit em `http://localhost:8025`.

Para medir concorrência entre processos, use:

```powershell
.\performance\demo\run.ps1
```

O script sobe o perfil `concurrency` com duas réplicas adicionais de `command-api` contra o mesmo PostgreSQL, executa três cenários k6 e encerra a pilha em `finally`. Os resultados gerados são locais e ficam em `performance/demo/results/`; a baseline versionada está em [performance/demo/README.md](../performance/demo/README.md).

## Validar Terraform sem AWS

Estes comandos não criam recursos. Os testes dos módulos usam `mock_provider`.

```powershell
terraform -chdir=infra/bootstrap init -backend=false
terraform -chdir=infra/bootstrap validate
terraform -chdir=infra/modules/network test
terraform -chdir=infra/modules/data-plane test
terraform -chdir=infra/modules/compute test
terraform -chdir=infra/modules/edge-observability test
terraform -chdir=infra/environments/demo init -backend=false
terraform -chdir=infra/environments/demo validate
```

## Demonstração AWS autorizada

O ambiente é a demo, em `sa-east-1`. Ele é limitado a 1h30 e deve ser destruído no fim. Antes de começar, confirme que há autorização explícita para criar recursos e que a conta possui créditos suficientes.

1. Autentique o perfil temporário no host. Para AWS CLI 2.32 ou superior, execute `aws login --profile flash-booking-demo`; em organizações com IAM Identity Center, use o fluxo existente, por exemplo `aws sso login --profile flash-booking-demo`.
2. Confirme a identidade, sem expor tokens: `aws sts get-caller-identity --profile flash-booking-demo`.
3. Copie `infra/environments/demo/demo.tfvars.example` para o arquivo ignorado `demo.tfvars`. Preencha um CIDR público restrito, o e-mail de alerta, o remetente SES verificado, a tag imutável da imagem já publicada no ECR e a role que pode assumir a role de invocação. Não use `0.0.0.0/0`.
4. Crie uma vez o bucket de state. Escolha um nome globalmente único:

```powershell
terraform -chdir=infra/bootstrap init
terraform -chdir=infra/bootstrap apply -var='state_bucket_name=nome-unico-do-state' -var='aws_region=sa-east-1'
```

5. Inicialize o ambiente apontando ao bucket retornado. O arquivo de backend é local e não deve conter credenciais:

```powershell
terraform -chdir=infra/environments/demo init -backend-config='bucket=nome-unico-do-state' -backend-config='key=flash-booking/demo.tfstate' -backend-config='region=sa-east-1'
terraform -chdir=infra/environments/demo plan -var-file=demo.tfvars
terraform -chdir=infra/environments/demo apply -var-file=demo.tfvars
```

O `plan` e o `apply` usam o campo `aws_profile` de `demo.tfvars` para escolher o perfil local. Revise o plano antes de aplicar. Terraform cria a rede, RDS PostgreSQL, Valkey, filas e DLQs, ECR, ECS Fargate, ALB interno, API Gateway REST, WAF, logs, alarmes e Budget. Não há criação manual pelo console.

## Invocar a API

O único endpoint público é o API Gateway REST. A infraestrutura exige IAM e SigV4, além da allowlist de CIDR. O operador assume `ApiInvokerRole`, que só recebe `execute-api:Invoke`; ela não provisiona nem destrói recursos. Postman deve usar autorização AWS Signature. Um teste simples pelo AWS CLI ou `curl` deve assinar a requisição com as credenciais temporárias dessa role.

Uma chamada sem assinatura válida, sem `execute-api:Invoke` ou fora do CIDR deve receber `403` antes de alcançar o VPC Link. A borda limita GET a 20 req/s (burst 40) e POST/DELETE a 5 req/s (burst 10); excesso começa a receber `429` no API Gateway. Budget emite alertas a 50%, 80% e 100% de US$5, mas não é um interruptor financeiro imediato.

## Roteiro de Case Review

1. Mostre os cinco endpoints e o fluxo local composto.
2. Explique que a atualização condicional do PostgreSQL e as constraints decidem o estoque. Mais réplicas não mudam essa regra e não permitem oversell.
3. Mostre a transação que inclui cliente, inventário, reserva e outbox. Explique idempotência por 24 horas, consumidor duplicado e reconciliador de expiração.
4. Diferencie reserva temporária de compra. O e-mail é assíncrono, usa SES em AWS e Mailpit localmente e não reverte a reserva quando falha.
5. Mostre cache-aside de no máximo um segundo e invalidação após commit. PostgreSQL continua sendo a fonte de verdade.
6. Mostre os três modos da mesma imagem: consultas, comandos e worker. Eles escalam e recebem permissões diferentes.
7. Mostre API Gateway IAM/SigV4, WAF, allowlist, throttling e o ALB privado. API Gateway é a única entrada pública.
8. Mostre os gates, o baseline local e o Terraform dividido por módulos. Declare a limitação: sem credenciais AWS locais, só houve validação estática/mockada, não um `plan` remoto.
9. Mostre o orçamento, a janela de 1h30 e o encerramento abaixo.

## Encerrar a demonstração

Ainda dentro de 1h30, destrua o runtime e confirme que não restaram recursos cobrados:

```powershell
terraform -chdir=infra/environments/demo destroy -var-file=demo.tfvars
terraform -chdir=infra/environments/demo state list
```

O `state list` deve ficar vazio após a destruição. O bucket de state é bootstrap deliberadamente separado; somente destrua-o se não precisar mais do histórico:

```powershell
terraform -chdir=infra/bootstrap destroy -var='state_bucket_name=nome-unico-do-state' -var='aws_region=sa-east-1'
```

## Trade-offs aceitos

- RDS Single-AZ e NAT único reduzem custo e operação na demo. A evolução para alta carga altera a topologia, não o domínio.
- Uma linha de inventário muito disputada aumenta latência e lock waits. A proteção é rejeitar ou esperar sem aceitar acima da capacidade; o benchmark local define o ponto de promoção.
- VPC Link V2 foi validado com AWS provider 5.100.0 e URI do ALB. O provider 6.x exige Terraform Windows 64-bit para permitir a validação remota do formato `integration_target` mais novo.
- Alertas e throttling reduzem risco de custo, mas não garantem teto absoluto. A proteção operacional final é a janela curta e `terraform destroy`.

## Uso de IA

A IA ajudou a estruturar a especificação, o design, as tarefas, os testes, os módulos Terraform, os scripts de validação e este roteiro. Cada alteração foi revisada no repositório, validada por ferramentas locais e registrada em commits atômicos. A IA não recebeu, gerou, imprimiu ou versionou credenciais AWS. Decisões de domínio, limites de custo, escopo de deployment e autorização de infraestrutura permanecem responsabilidade do responsável pelo projeto.
