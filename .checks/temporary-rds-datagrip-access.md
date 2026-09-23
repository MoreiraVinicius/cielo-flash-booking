# Acesso administrativo temporário ao RDS da demo

Sources:

- [.tasks/temporary-rds-datagrip-access.md](../.tasks/temporary-rds-datagrip-access.md) — critérios C1–C6 e limites do trabalho.
- [.design/temporary-rds-datagrip-access.md](../.design/temporary-rds-datagrip-access.md) — vinculante para acesso direto, origem única, TLS e revogação.

## Out of scope

- Acesso a Valkey, SQS, ALB ou ECS - o caminho externo é somente PostgreSQL.
- CIDR amplo, segundo operador, VPN, bastion ou alteração do schema funcional - não fazem parte da janela temporária.

## Landing

Os módulos `network`, `data-plane` e o ambiente `demo` passam a declarar a exceção explicitamente e preservam suas relações existentes. O runbook reutiliza o segredo mestre RDS e os outputs existentes; não introduz credenciais no Terraform.

| One-way door | Literal shape | Alternative rejected |
| --- | --- | --- |
| Origem administrativa do RDS | Uma única entrada IPv4 `/32`, local e sem default permissivo, além do acesso ECS existente | `0.0.0.0/0` - expõe o PostgreSQL a qualquer origem da Internet |
| Conexão administrativa | PostgreSQL 16 com `rds.force_ssl = 1`; DataGrip em `sslmode=verify-full` | `sslmode=require` - não valida a identidade do servidor |

- Nothing else in this change is hard to reverse.

## Checks

### S1 - RDS PostgreSQL · módulos Terraform e runbook · ~25k

**C1** - Com a exceção desativada, o RDS é privado, usa as duas subnets isoladas e não tem entrada IPv4 5432.
Proof: `terraform -chdir=infra/modules/data-plane test` — run `keeps_data_private_encrypted_and_message_paths_isolated`; `terraform -chdir=infra/modules/network test` — run `creates_two_private_application_and_isolated_data_subnets`

**C2** - Com a exceção ativada e uma única IPv4 `/32`, o RDS é público e usa exatamente duas subnets públicas.
Proof: `terraform -chdir=infra/modules/data-plane test` — run `exposes_rds_only_for_explicit_administrative_access`

**C3** - A regra pública 5432 aceita apenas a IPv4 `/32`; o acesso do Security Group ECS permanece e não existe `0.0.0.0/0`.
Proof: `terraform -chdir=infra/modules/network test` — run `limits_rds_administrative_access_to_one_ipv4`

**C4** - O RDS mantém criptografia, senha mestre gerenciada e `rds.force_ssl = 1` no parameter group PostgreSQL 16.
Proof: `terraform -chdir=infra/modules/data-plane test` — run `requires_tls_and_preserves_rds_managed_security`

**C5** - O runbook instrui DataGrip com endpoint, porta 5432, banco `flashbooking`, segredo mestre, CA AWS e `sslmode=verify-full`.
Proof: `rg -n "sslmode=verify-full|flashbooking|5432|Secrets Manager|CA" docs/acesso-rds-datagrip.md`

**C6** - O runbook instrui a troca exclusiva do `/32` em caso de IP alterado e o reinício/espera por `available` se o RDS estiver parado.
Proof: `rg -n "CIDR|/32|available|start-db-instance" docs/acesso-rds-datagrip.md`

## Swept

- validation: C2
- failure modes: C6
- idempotency: existing - Terraform reconcilia o estado declarado sem alterar dados PostgreSQL
- authorization: C3
- concurrency: not in scope - não há nova operação concorrente de dados
- data lifecycle: C1, C2
- dependency failure: C6
- state transitions: C1, C2
- observability: existing - métricas e alarme RDS permanecem no dashboard operacional

## Handoff

S1 = ~25k tokens; cabe integralmente em um único lote, sem handoff de construção.
