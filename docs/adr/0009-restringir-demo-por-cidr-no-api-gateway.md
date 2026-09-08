# Restringir a API publicada da demo por CIDR no API Gateway

- Estado: substituída pelo ADR 0012
- Referências: [spec da demo](../../.specs/features/flash-booking-demo/spec.md), [plano de custo](../cost-estimate.md), [políticas de recurso do API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-resource-policies.html).

> Decisão histórica, sem vigência. O controle atual exige IAM/SigV4 e está no [ADR 0012](0012-autenticacao-e-protecao-de-custos-na-borda.md).

## Contexto

A demo será usada somente pelo responsável pelo projeto e permanecerá publicada por no máximo 1h30. Credenciais AWS usadas pelo Terraform permitem administrar recursos, mas não restringem automaticamente as chamadas HTTP de uma API pública. Não há requisito de autenticação de usuário no case e implementar login ou um provedor de identidade acrescentaria código e custo sem demonstrar o objetivo do backend.

## Decisão

Usar uma API REST do API Gateway com resource policy que permite invocação somente dos CIDRs informados na variável Terraform obrigatória `allowed_cidrs`. O valor não é versionado e não possui default permissivo. `terraform plan` deve falhar se a lista estiver vazia. O operador fornece o IP público atual em um arquivo `*.tfvars` ignorado pelo Git; um avaliador autorizado informa o seu próprio CIDR antes do apply.

O acesso à API é bloqueado na borda para origens fora da lista. Esta decisão não transforma o endpoint em API autenticada nem substitui autorização de negócio; ela limita a exposição temporária da demo. Banco, Valkey e ALB continuam privados e só recebem tráfego das origens internas previstas.

## Alternativas consideradas

Deixar o endpoint público sem restrição é mais rápido, mas contradiz o uso individual acordado. Autenticação no Java, Cognito ou VPN introduzem alteração de código, operação adicional e, em alguns casos, custo; não são proporcionais para uma demonstração de 1h30. Restringir somente o security group do ALB não identifica o cliente externo quando o API Gateway está à frente dele.

## Consequências históricas

O endereço IP residencial poderia mudar e exigir novo `plan`/`apply`. A decisão foi substituída porque CIDR não autentica o operador. O ADR 0012 mantém CIDR apenas como camada complementar, sem colocar valores pessoais no repositório, e adiciona identidade IAM/SigV4 nas duas arquiteturas.
