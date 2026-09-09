# Autenticar na borda e limitar abuso antes dos containers

- Estado: aceita
- Substitui: [ADR 0009](0009-restringir-demo-por-cidr-no-api-gateway.md)
- Referências: [avaliação do case](../case-requirements-evaluation.md), [controle IAM no API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/permissions.html), [AWS WAF no API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-control-access-aws-waf.html), [usage plans e limites em melhor esforço](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-api-usage-plans.html), [integração privada REST por VPC Link V2](https://docs.aws.amazon.com/whitepapers/latest/best-practices-api-gateway-private-apis-integration/rest-api.html) e [ações do AWS Budgets](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-controls.html).

## Contexto

A API publicada será usada somente pelo responsável pelo projeto e pelos entrevistadores autorizados. Uma lista de CIDRs reduz exposição, mas não autentica pessoas e pode mudar em redes residenciais. API keys e usage plans também não são mecanismos de autenticação e seus limites são aplicados em melhor esforço. O controle deve acontecer antes do ALB, dos containers e do banco, onde uma rajada já produziria custo e contenção.

## Decisão

Usar um único Amazon API Gateway REST regional como único ponto público das duas arquiteturas. Todos os métodos exigem `AWS_IAM`; cada operador assume uma role temporária com somente `execute-api:Invoke` nas rotas da demo e assina as requisições com SigV4. A resource policy permite apenas as roles configuradas e, quando viável, os CIDRs fornecidos por Terraform. API keys não concedem acesso.

O API Gateway usa VPC Link V2 para um ALB interno. O ALB, os serviços ECS, PostgreSQL, Valkey, filas e endpoints administrativos permanecem sem endereço público. Security groups permitem ao ALB receber somente do VPC Link e aos containers receber somente de seus target groups. Rotas GET seguem para o serviço de consultas; POST e DELETE seguem para o serviço de comandos.

Defesa contra abuso e custo:

- throttling por método no API Gateway, com valores iniciais da demo de 20 req/s e burst 40 para GET, e 5 req/s e burst 10 para comandos;
- AWS WAF associado ao stage, com allowlist de CIDR e regra baseada em taxa parametrizada;
- limites máximos de tasks ECS, conexões e fallback ao banco definidos em Terraform;
- payload máximo e validação no controller; resposta `429` quando a borda rejeitar excesso;
- AWS Budget de US$5 com alertas em 50%, 80% e 100%, além do procedimento obrigatório de `terraform destroy` ao final da janela de 1h30.

Throttling, WAF e Budgets não formam um teto financeiro matemático: API Gateway e WAF trabalham com limites aproximados e Budgets pode ter atraso. A proteção principal é negar identidades não autorizadas, não expor backends e manter tetos pequenos de capacidade na demo.

## Alternativas consideradas

HTTP Basic no Java exigiria distribuir e rotacionar uma senha e faria requisições abusivas alcançarem a aplicação antes da rejeição. Cognito é adequado para clientes finais, mas adiciona fluxo de login não exigido pelo case. API key foi rejeitada porque a própria AWS orienta não usá-la para autenticação ou autorização. Um ALB público duplicaria a superfície de entrada e permitiria contornar os controles do API Gateway.

## Consequências

Entrevistadores precisam de credenciais temporárias próprias para assumir uma `ApiInvokerRole`; não recebem a role de provisionamento. Postman e ferramentas equivalentes devem usar assinatura AWS SigV4. A autenticação recomendada ocorre no host por `aws login` ou pelo fluxo IAM Identity Center já adotado pela organização. Terraform recebe o perfil explicitamente; imagens e containers da aplicação usam task roles e nunca incorporam nem montam as credenciais do operador. O mesmo contrato de acesso vale para demo e alta carga; somente limites e capacidade mudam por Terraform.
