# Plano de telemetria AWS — não aplicado

**Estado:** proposta documentada; nenhuma task definition, role, endpoint, dashboard ou conta AWS foi alterada.

## Contexto e recomendação

O runtime já exporta métricas e traces HTTP em OTLP no perfil `observability`; o padrão continua desligado. A smoke local comprovou o protocolo até um OpenTelemetry Collector, mas não comprovou ingestão, IAM, preço, Application Signals nem operação na AWS.

Recomendação inicial para ECS Fargate: um sidecar **CloudWatch Agent** por task de API, recebendo OTLP/HTTP em `localhost:4318` e encaminhando métricas à ingestão OTLP nativa de CloudWatch e traces ao X-Ray/Transaction Search. O agente é a distribuição AWS de Collector recomendada pela AWS, recebe dados de SDKs OTLP e assina chamadas AWS. Fixar imagem por versão e, quando implantado, digest; nunca usar `latest` em task definition. Confirmar a versão mínima que oferece ingestão OTLP (1.300070.0 ou posterior nas instruções atuais) antes do plano Terraform. [Ingestão OTLP no CloudWatch Agent](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLPCloudWatchAgent.html)

O primeiro canário deve ser apenas `command-api`, porque é onde há operações de escrita HTTP que interessam à demonstração. Depois de validar custo, cardinalidade, privacidade e recursos Fargate, habilitar `query-api`. Não habilitar `worker` nesta fase: o seu fluxo útil é SQS/outbox e sua continuidade de traces está explicitamente fora desta implementação. Os dashboards existentes continuam sem ler essas novas séries; AD-023/AD-024 permanecem evidência da demo implantada atual.

## Topologia proposta

```text
API Gateway / ALB
        │ HTTP
        ▼
┌────────────────── ECS Fargate task (awsvpc) ───────────────────┐
│ command-api                                                     │
│ Spring Boot 4 / Micrometer                                      │
│ OTLP/HTTP metrics + traces ───────────────┐                      │
│ localhost:4318                             ▼                      │
│                                  CloudWatch Agent sidecar         │
└───────────────────────────────────────────┬──────────────────────┘
                                            │ SigV4 over HTTPS
                           ┌────────────────┴─────────────────┐
                           ▼                                  ▼
                 CloudWatch OTel Metrics              AWS X-Ray /
                 (PromQL, dashboards)                 Transaction Search
```

A porta `4318` fica apenas no namespace de rede da task; não criar listener público, regra de entrada no security group ou ALB target para OTLP. As tasks atuais são `awsvpc` e já têm task roles separadas por processo. A app enviaria `management.otlp.metrics.export.url=http://localhost:4318/v1/metrics` e `management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:4318/v1/traces`, além de `OTEL_SERVICE_NAME` estável (`flash-booking-command-api`, `flash-booking-query-api`) e atributo `deployment.environment` apropriado.

## IAM e limite entre containers

As task definitions atuais usam um execution role compartilhado para pull/logs/secrets e roles de task distintas para query, command e worker (`infra/modules/compute/main.tf`). O CloudWatch Agent precisa de credenciais da task role. Em Fargate, essa role é da task, não uma role IAM isolada por container: adicionar permissões ao sidecar também torna as credenciais visíveis aos containers da mesma task. Registrar esse risco e preservar o isolamento entre as três tasks.

O caminho documentado pela AWS é `CloudWatchAgentServerPolicy`, mas a policy gerenciada atual inclui permissões que esta ingestão simples não precisa (por exemplo, `ec2:Describe*`, operações de criação/retenção de logs e acesso a parâmetros SSM). Preferir policy inline própria, após validar a lista com uma task de staging: `cloudwatch:PutMetricData` para métricas e `xray:PutTraceSegments`/`xray:PutTelemetryRecords` para traces, sem permissões de leitura de sampling remoto quando ele não estiver configurado. As APIs não oferecem resource ARNs úteis para essas ações e podem exigir `Resource: "*"`; restringir por condição/namespace quando suportado e documentar o limite. Não anexar a policy ampla às roles atuais sem revisão. [Policy gerenciada e ações](https://docs.aws.amazon.com/aws-managed-policy/latest/reference/CloudWatchAgentServerPolicy.html)

Se Application Signals for aprovado depois, tratar permissões de descoberta e configuração do serviço como um incremento separado; não ampliar implicitamente o role de runtime com policy de configuração. A configuração inicial não coleta/exporta logs OTLP. Logs continuam no driver `awslogs` e nos grupos ECS existentes.

## Alternativas

| Opção | Vantagens | Custos e limites | Decisão proposta |
| --- | --- | --- | --- |
| CloudWatch Agent sidecar + OTLP nativo | Recomendado pela AWS para ingestão CloudWatch; SigV4, endpoints e região tratados pelo agente; atende SDK OTLP e Fargate sidecar | IAM da task compartilhado; aumenta CPU/memória por task; acopla o destino operacional ao CloudWatch; policy gerenciada é ampla | Canário recomendado para métricas e traces básicos |
| ADOT/upstream OpenTelemetry Collector sidecar | Pipeline portável e controle explícito de processors, sampling, filtros e exporters; útil se houver outro backend | Mais configuração e ciclo de atualização próprios; exporter AWS e SigV4 precisam ser mantidos; não é requisito para a ingestão básica que o CloudWatch Agent já faz | Escolher somente se portabilidade/pipeline custom for requisito decidido |
| CloudWatch Application Signals com auto-instrumentação ADOT | Service map, métricas padrão de latência/falhas, SLO e visão APM integrada; Java suportado em ECS | Instrumentação/dependências/atributos adicionais, configuração do agente e IAM de descoberta; preço próprio; Java agent junto à instrumentação Micrometer pode duplicar spans | Decisão separada após canário OTLP, sem habilitar nesta mudança |

O Agent sidecar OTLP não deve ser descrito automaticamente como Application Signals. Application Signals é uma experiência APM separada; confirmar o setup compatível com esta instrumentação Boot/Micrometer, habilitar Transaction Search onde exigido e medir custos antes de alegar mapa de serviços/SLOs. A AWS recomenda ADOT + CloudWatch Agent para a experiência mais integrada; o caminho existente usa Micrometer/Spring, não o agente ADOT Java. [Opções de instrumentação Application Signals](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Getting-Started-App-Signals.html) · [Habilitação no ECS](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Application-Signals-Enable-ECSMain.html)

## Métricas, sampling e privacidade

- Começar com traces a 5% no canário; em staging, 100% com tráfego sintético. Alterar `management.tracing.sampling.probability` por ambiente, nunca transportar o valor local 1.0 para produção. Head sampling não garante reter todas as traces com erro; se isso for requisito, avaliar tail sampling no Collector como decisão posterior.
- Manter métricas HTTP completas com tags de baixa cardinalidade: método, status, rota-template, nome de serviço, ambiente e região. Não criar tags com `reservationId`, `customerId`, e-mail, `Idempotency-Key`, `X-Correlation-ID` ou URL bruta.
- Não exportar bodies, query strings ou headers de autenticação/cookies. Verificar atributos reais das spans HTTP no canário e remover/redigir caminho concreto quando contiver IDs. Não usar baggage para dados pessoais. `X-Correlation-ID` continua identificador de logs e não é o trace ID.
- Não adicionar spans de outbox/SQS nem afirmar continuidade assíncrona entre APIs e worker. A propagação de contexto do protocolo não persiste contexto na linha da outbox por si só.
- Manter limites e timeout curtos nos exportadores. Indisponibilidade do agente/back-end deve degradar telemetria, não rejeitar reserva; a integração local já testou este comportamento com endpoint OTLP inalcançável.

## Custo e rede

Não é possível estimar um total sem região, tráfego, tamanho dos dados, retenção e preço vigente. Modelar na AWS Pricing Calculator antes da aprovação considerando:

1. vCPU/memória Fargate adicionais do sidecar em cada serviço e possível mudança de classe das tasks atuais (256 CPU/512 MiB não devem ser presumidos suficientes para app + agente).
2. Bytes de métricas OTLP ingeridos, sampling/volume de traces, Transaction Search, consultas PromQL/API, SLOs/alarmes e eventuais logs adicionais.
3. NAT ou VPC interface endpoints/reachability para `monitoring` e `xray`; endpoints privados também têm custo horário e por dados. Porta do collector não exige ingresso externo.

O modelo de métricas OTel do CloudWatch é por GB ingerido e documenta 15 meses de retenção incluída; consultas no Query Studio/console são gratuitas, enquanto chamadas de API são tarifadas por amostras lidas. Isso não torna traces/Application Signals grátis. O custo de traces, Transaction Search, tarefas Fargate, endpoints de VPC e recursos existentes deve ser consultado por região no momento da aprovação. [Preço de métricas OTel](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/metrics-otel-pricing.html) · [Métricas OTel CloudWatch](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/metrics-otel-overview.html)

Usar AD-017/AD-026 como guardrails financeiros existentes; o limite/budget não substitui estimativa nem garante corte instantâneo. Fixar sampling, período de métricas e atributos permitidos antes do deploy.

## Rollout, prova e rollback

1. Antes de planejar Terraform: selecionar região, imagem/tag/digest, sizing de task e política IAM custom; verificar suporte e endpoint CloudWatch OTLP na região; confirmar Transaction Search e retenção/custo; revisar configurações efetivas das spans; estimar gasto sob os Budgets AD-017/AD-026.
2. Fazer um `terraform plan` revisável depois de autorização própria para iniciar a fase AWS. O plano deve mostrar mudanças somente nas task definitions/roles e, se inevitável, rede para endpoints; não recriar dashboards AD-023/AD-024 nem ativar a arquitetura high-load.
3. Implantar um canário da `command-api` com perfil `command-api,observability`, atributos de serviço/ambiente, exportadores e sidecar. Deixar `essential=false` para o Agent ou definir comportamento de falha explicitamente, garantindo que falha do agente não pare o caminho HTTP; manter health check da aplicação como critério do ECS.
4. Validar métricas OTLP e traces na região, nomes dos serviços e rotas, ausência de tags PII, latência/erros HTTP, aumento de CPU/memória, retries/drop/exporter errors e custo. Comparar com API Gateway e os dashboards existentes; não somar contagens HTTP como clientes, reservas únicas, venda ou receita.
5. Só então decidir habilitação de `query-api`. `worker` requer desenho separado da correlação persistida na outbox e não entra nesse rollout.
6. Rollback: desativar o perfil/exportadores e endpoints OTLP na task do canário, remover o sidecar e suas permissões por task role, depois revisar novo plano. Não há alteração de schema. Reverter sem alterar os grupos de logs, dashboards ou telemetria de borda existentes.

**Autorização necessária antes de qualquer ação remota:** aprovar imagem e digest, permissões IAM, dimensões/preço, região/endpoints, tarefa canário e janela do `terraform plan/apply`. Esta especificação não autoriza `terraform plan`, `apply`, deploy, mudanças IAM ou consultas de produção.
