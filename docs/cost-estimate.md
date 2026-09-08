# Estimativa de custo da demo

Região de referência: `sa-east-1`. Janela máxima ligada: 1h30. Créditos AWS disponíveis: US$100.

Este documento é um roteiro de estimativa, não uma promessa de preço. O valor real depende da conta, da região, das classes escolhidas e do tráfego. Antes de qualquer `terraform apply`, os preços precisam ser recalculados na AWS Pricing Calculator e registrados junto à evidência da demonstração.

## Recursos incluídos na estimativa

| Grupo | Recursos da demo | Diretriz de custo |
| --- | --- | --- |
| Entrada e proteção | API Gateway REST regional, AWS WAF e logs mínimos | Uma única API pública; limites conservadores evitam que tráfego rejeitado chegue aos containers. WAF e throttling reduzem exposição, mas não constituem teto financeiro absoluto. |
| Rede | VPC, sub-redes, ALB interno, VPC Link V2 e, se indispensável, NAT Gateway | NAT pode ser relevante mesmo em uma janela curta. Preferir endpoints privados e confirmar se o desenho consegue eliminá-lo sem quebrar ECR, logs, SES ou Secrets Manager. |
| Aplicação | Uma task Fargate para `query-api`, uma para `command-api` e uma para `worker` | Os três serviços usam a mesma imagem e tamanhos mínimos compatíveis com a JVM. Não consolidá-los apenas para baratear a demo, pois a separação faz parte da arquitetura avaliada. |
| Dados | RDS PostgreSQL Single-AZ, ElastiCache for Valkey e armazenamento | Classes econômicas parametrizadas em Terraform. PostgreSQL é autoritativo; Valkey é descartável e não requer capacidade equivalente à do banco. |
| Mensageria e e-mail | Duas filas SQS, respectivas DLQs e Amazon SES | Filas de expiração e notificação ficam isoladas. O volume da demo tende a ser pequeno, mas deve constar no cálculo. O Mailpit é usado apenas localmente. |
| Operação | ECR, Secrets Manager, CloudWatch Logs, métricas, alarmes e AWS Budgets | Retenção curta de logs e exclusão explícita dos artefatos cobrados. Budgets alerta; não interrompe universalmente todo gasto. |

## Cenário de cálculo

- Duração máxima dos recursos cobrados por tempo: 1h30.
- Uma única pessoa opera a demo; entrevistadores recebem acesso temporário somente se necessário.
- Tráfego de demonstração é limitado na borda: consultas em 20 requisições/s com rajada 40; comandos em 5 requisições/s com rajada 10.
- A arquitetura de alta carga não entra no cálculo porque seu Terraform não será aplicado nesta entrega.
- Transferência de dados, armazenamento mínimo faturável, endereços IPv4 públicos e arredondamentos de cobrança devem ser incluídos mesmo com execução curta.

## Portões antes do provisionamento

1. Calcular cada recurso na AWS Pricing Calculator para `sa-east-1`, usando as classes e os volumes definidos nas variáveis Terraform.
2. Registrar o total, as classes, a duração, o tráfego assumido e quais itens são cobertos por créditos da conta.
3. Não executar `terraform apply` se a estimativa superar US$100 ou se algum recurso de custo relevante estiver sem estimativa.
4. Configurar alertas de AWS Budgets em US$50, US$80 e US$100. Esses alertas são defesa adicional, não garantia de corte automático.
5. Definir um temporizador operacional de 1h30 e o responsável pelo `terraform destroy` antes de criar recursos.

## Encerramento e conferência

Após a demonstração, executar o `terraform destroy` da demo e conferir no console/API a remoção de RDS, ElastiCache, serviços e tasks ECS, ALB/VPC Link, NAT Gateway, WAF e demais recursos com cobrança contínua. ECR, snapshots, logs ou segredos preservados intencionalmente precisam ser listados com custo residual; caso contrário, também devem ser removidos.

Fontes de preço a consultar no momento da execução: [RDS PostgreSQL](https://aws.amazon.com/rds/postgresql/pricing/), [ElastiCache](https://aws.amazon.com/elasticache/pricing/), [Fargate](https://aws.amazon.com/fargate/pricing/), [API Gateway](https://aws.amazon.com/api-gateway/pricing/), [AWS WAF](https://aws.amazon.com/waf/pricing/), [SQS](https://aws.amazon.com/sqs/pricing/) e [SES](https://aws.amazon.com/ses/pricing/).
