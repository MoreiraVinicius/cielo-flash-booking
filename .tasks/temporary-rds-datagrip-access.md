# Acesso administrativo temporário ao RDS da demo

> Construa isto com **tlc-implement**.
> Cada critério abaixo vira uma verificação com prova referenciada por seu número. Nada em
> `Unresolved` é decidido durante a implementação.

## Intent

O responsável pela demo não consegue examinar nem corrigir o estado publicado pelo DataGrip local, pois o RDS aceita somente as tasks ECS. O substituto local não contém o estado remoto e, durante uma demonstração de dois dias, um bloqueio administrativo impede a recuperação de incidentes.

Ao concluir, o responsável poderá administrar o PostgreSQL publicado diretamente do DataGrip durante a janela temporária, a partir de uma única origem IPv4 cadastrada localmente; todo outro endereço continuará sem conectividade com o banco. 6 critérios em 1 slice · 4 decisões de segurança · 0 abertas, das quais 0 bloqueiam.

## Criteria

### RDS PostgreSQL

1. Dado que o acesso administrativo temporário está desativado, quando a configuração Terraform é avaliada, então o RDS permanece com `publicly_accessible = false`, usa duas subnets de dados isoladas e não possui regra de entrada IPv4 para a porta `5432`.
2. Dado que o acesso administrativo temporário está ativado com exatamente um CIDR IPv4 `/32` vindo de `demo.tfvars` ignorado pelo Git, quando a configuração Terraform é avaliada, então o RDS tem `publicly_accessible = true` e seu DB subnet group usa exatamente as duas subnets públicas da VPC.
3. Dado o acesso administrativo ativado, quando uma conexão TCP é direcionada ao RDS na porta `5432`, então o Security Group admite somente o CIDR administrativo `/32` e o Security Group das tasks ECS; nenhuma regra de entrada contém `0.0.0.0/0`.
4. Sempre, o RDS preserva criptografia em repouso e senha mestre gerenciada pelo RDS, e sua configuração PostgreSQL 16 exige TLS com `rds.force_ssl = 1`.
5. Quando o responsável segue o runbook do DataGrip, então ele obtém o endpoint, a porta `5432`, o banco `flashbooking` e a credencial mestre do Secrets Manager, e configura `sslmode=verify-full` com a CA da AWS e validação do hostname do RDS.
6. Se o IP público do responsável mudar ou o RDS estiver parado pelo controle de custo, então o runbook descreve a recuperação concreta: trocar somente o CIDR `/32` local e reaplicar, ou iniciar o RDS e aguardar o estado `available`, antes de testar a conexão novamente.

## Out of scope

- Acesso administrativo a Valkey, SQS, ALB ou ECS - a janela cobre somente PostgreSQL.
- Um segundo operador ou uma faixa de rede mais ampla - a origem é uma única IPv4 `/32`.
- VPN, bastion ou túnel de sessão - adicionariam dependências que não atendem à necessidade de acesso direto da demo curta.
- Mudanças em schema, dados ou contratos HTTP - o trabalho cria somente o caminho administrativo.

## Observable

| Surface | Decision | Landing |
| --- | --- | --- |
| conexão DataGrip → PostgreSQL | conexão da origem aprovada | 2, 3, 4, 5 |
| conexão DataGrip → PostgreSQL | origem não aprovada | 3 |
| conexão DataGrip → PostgreSQL | RDS parado ou IP alterado | 6 |
| runbook operacional | configuração TLS e recuperação | 5, 6 |
| API HTTP | resposta, autenticação e limites | n/a - a API Gateway não é alterada |

## Swept

- validation: 2 - o acesso ativado exige exatamente um CIDR IPv4 `/32`; a entrada ausente, ampla ou inválida é recusada pela configuração.
- failure modes: 6 - o runbook trata IP alterado e RDS parado com uma ação verificável.
- idempotency and retry: existing - a reaplicação Terraform reconcilia o estado declarado e não modifica os dados do PostgreSQL.
- authorization: 3 - o Security Group permite exclusivamente o CIDR administrativo e as tasks ECS.
- concurrency and ordering: n/a - a mudança não introduz operação concorrente nem nova transição de dados; a administração do banco já segue seus próprios controles PostgreSQL.
- data lifecycle: 1, 2 - a exposição é explicitamente ativada ou desativada e a destruição da demo elimina os recursos.
- external-dependency failure: 6 - indisponibilidade do RDS é recuperada somente quando a AWS informa o estado `available`.
- state transitions: 1, 2 - a configuração alterna entre o RDS privado padrão e a exceção pública temporária.
- observability: existing - o alarme de CPU do RDS e o dashboard operacional permanecem os sinais do banco; os testes Terraform provam estaticamente a restrição de rede.

## Impact

| Front | What changes |
|---|---|
| infraestrutura de rede | O DB subnet group do RDS alterna entre as subnets isoladas e as duas subnets públicas somente quando o acesso administrativo está ativado; Valkey continua nas subnets isoladas. |
| infraestrutura de segurança | O Security Group do RDS mantém o acesso das tasks ECS e, na exceção temporária, adiciona uma única regra IPv4 de porta `5432`. |
| configuração de banco | O RDS associa um parameter group PostgreSQL 16 que torna `rds.force_ssl = 1` explícito. |
| dados persistidos | Nada a migrar; a alteração não cria, reescreve ou apaga registros. |
| documentação operacional | O runbook passa a conter os valores de conexão do DataGrip e as duas recuperações previstas. |

## Decided

| Decision | Shape | Alternative rejected |
|---|---|---|
| Origem administrativa | Uma regra IPv4 `/32` local, sem default permissivo, além da regra ECS→RDS existente. | `0.0.0.0/0` - permite varredura e tentativa de autenticação por qualquer origem. |
| Alcance público | Somente o DB subnet group do RDS usa duas subnets públicas enquanto a exceção está ativa; Valkey permanece isolado. | Adicionar rota de Internet às subnets de dados - ampliaria a exposição de recursos que não precisam de acesso administrativo. |
| Privilégio administrativo | A credencial mestre gerenciada pelo RDS é usada pelo DataGrip para esta janela. | Conta limitada - não permite corrigir todo incidente de demonstração. |
| Proteção em trânsito | `rds.force_ssl = 1` e DataGrip em `sslmode=verify-full` com CA e hostname validados. | `sslmode=require` sem validação de servidor - cifra o tráfego, mas não autentica o servidor para o cliente. |

## Sources

- [.design/temporary-rds-datagrip-access.md](../.design/temporary-rds-datagrip-access.md) — **vinculante para a interface**: acesso administrativo direto, origem única, TLS e ciclo de revogação.
- [infra/modules/data-plane/main.tf](../infra/modules/data-plane/main.tf) — RDS PostgreSQL 16, segredo mestre gerenciado e DB subnet group atual.
- [infra/modules/network/main.tf](../infra/modules/network/main.tf) — subnets públicas, subnets isoladas e regra ECS→RDS atual.
- [AWS: SSL com RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html) — TLS obrigatório e comportamento de `rds.force_ssl`.

## Unresolved

| # | Kind | Question | Until answered |
|---|---|---|---|
| None |  |  |  |
