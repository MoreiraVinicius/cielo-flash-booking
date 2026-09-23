# Acesso temporário ao RDS pelo DataGrip

Use este procedimento somente enquanto `database_administrative_access_enabled = true` na demo. Ele libera o PostgreSQL para uma única IPv4 `/32`; não use `0.0.0.0/0`.

## Ativar ou atualizar o IP

No arquivo local e ignorado `infra/environments/demo/demo.tfvars`, defina:

```hcl
database_administrative_access_enabled = true
database_administrative_cidr           = "SEU_IPV4_PUBLICO/32"
```

Descubra o IP atual no PowerShell:

```powershell
(Invoke-RestMethod https://checkip.amazonaws.com).Trim()
```

Quando o IP mudar, substitua apenas o valor de `database_administrative_cidr` pelo novo `/32`, execute o plano aprovado e aplique-o. Nunca amplie a regra para uma faixa maior para contornar a troca de IP.

## Recuperar os dados de conexão

No console AWS, abra **RDS → Databases → `flash-booking-demo-postgres`** e copie o endpoint e a porta `5432`. O banco é `flashbooking` e o usuário mestre é `flashbooking`.

Na mesma tela, abra o segredo mestre associado em **Secrets Manager** e use o valor de `password`. Não copie a senha para `demo.tfvars`, arquivos `.env`, repositório ou histórico do terminal.

Baixe o bundle CA regional/global atual do RDS pela documentação da AWS e guarde-o fora do repositório. O DataGrip precisa desse arquivo para validar o certificado do servidor.

## Configurar o DataGrip

Crie uma fonte de dados **PostgreSQL** com:

```text
Host: endpoint copiado do RDS
Port: 5432
Database: flashbooking
User: flashbooking
Password: senha do Secrets Manager
URL: jdbc:postgresql://ENDPOINT_DO_RDS:5432/flashbooking?sslmode=verify-full
```

Em **SSH/SSL**, habilite SSL e aponte **CA file** para o bundle CA do RDS. Não habilite túnel SSH. Clique em **Test Connection**; uma conexão válida exige TLS, a CA e o hostname do endpoint.

## Recuperar acesso

Se o IP mudou, atualize somente o `/32` conforme a seção anterior e reaplique o Terraform autorizado. Se o RDS foi parado pelo controle de custo, inicie a instância `flash-booking-demo-postgres` no console ou execute:

```powershell
aws rds start-db-instance --region sa-east-1 --db-instance-identifier flash-booking-demo-postgres
```

Espere o estado **available** no console RDS antes de testar a conexão novamente.

## Revogar

Antes de encerrar a demo sem destruí-la, defina `database_administrative_access_enabled = false` e aplique o plano autorizado. Para encerrar a demo inteira, execute o fluxo de destruição já documentado; isso remove o RDS e o caminho administrativo junto com os demais recursos.
