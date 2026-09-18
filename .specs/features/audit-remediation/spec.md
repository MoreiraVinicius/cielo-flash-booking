# Audit Remediation Specification

## Problem Statement

A revisao tecnica encontrou falhas de corretude, operacao e reprodutibilidade no codigo Java e na infraestrutura AWS. A entrega deve corrigir esses defeitos sem ampliar o produto alem do case.

## Goals

- [ ] Usar o relogio do PostgreSQL nas decisoes temporais persistidas.
- [ ] Reforcar limites HTTP, idempotencia, cache e observabilidade de erros.
- [ ] Evitar trabalho remoto dentro de transacoes e dar vazao ao processamento de expiracao.
- [ ] Remover codigo e dependencias sem uso real.
- [ ] Tornar o deploy Terraform reproduzivel e os alarmes acionaveis.
- [ ] Executar verificacao automatica no build e documentar somente evidencia sustentada.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Deploy ou alteracao de recursos AWS | Exige autorizacao remota separada. |
| Troca da autenticacao IAM por JWT | Exige decisao explicita de arquitetura. |
| Alta disponibilidade Multi-AZ | Continua fora da demo de baixo custo. |
| Garantia de entrega exatamente uma vez por SES | O provedor nao oferece transacao atomica com PostgreSQL. |

## Assumptions & Open Questions

| Decisao | Padrao escolhido | Justificativa | Confirmada? |
| --- | --- | --- | --- |
| Contrato HTTP | Preservar endpoints e formatos existentes | Evita transformar correcao em nova API. | yes |
| Componentes AWS | Preservar a topologia publicada, corrigindo seus defeitos operacionais | Mudanca total invalidaria a evidencia existente e excederia a solicitacao. | yes |
| Idempotency-Key | Limite de 128 caracteres | Limite explicito impede falha tardia no indice e abuso de memoria. | yes |
| Expiracao | Lotes configuraveis de 1000 a cada 500 ms | Supera a carga historica sem criar novo componente. | yes |

**Open questions:** none.

## User Stories

### P1: Persistencia temporal correta

**Historia:** Como operador, quero que prazos e identidades persistidas sejam estaveis entre instancias.

**Acceptance Criteria:**

1. WHEN a reservation is created THEN `createdAt` and `expiresAt` SHALL derive from PostgreSQL time.
2. WHEN expired reservations are selected THEN the indexed predicate SHALL use a stable statement timestamp.
3. WHEN an existing customer email is reused with another name THEN prior and new reservations SHALL retain the original persisted customer identity.
4. WHEN an idempotency key exceeds 128 characters THEN the API SHALL return `400` before database access, and PostgreSQL SHALL enforce the same bound.

### P1: Fronteira HTTP resiliente

**Historia:** Como integrador, quero limites e erros consistentes mesmo sob entradas hostis e repeticoes.

**Acceptance Criteria:**

1. WHEN a request body exceeds 64 KiB with or without `Content-Length` THEN the system SHALL return `413` without invoking the controller.
2. WHEN an idempotent error is replayed THEN its body and response header SHALL contain the correlation ID of the current request.
3. WHEN an unexpected exception occurs THEN the server SHALL log the throwable with its correlation ID and SHALL keep internal details out of the response.
4. WHEN Valkey returns a healthy cache miss THEN the database fallback SHALL not consume the outage bulkhead; WHEN a cache operation succeeds THEN prior circuit failures SHALL be cleared.

### P1: Processamento operacional seguro

**Historia:** Como operador, quero que tarefas periodicas cumpram o prazo sem segurar transacoes durante chamadas externas.

**Acceptance Criteria:**

1. WHEN scheduled jobs are enabled THEN independent jobs SHALL be able to execute on separate scheduler threads.
2. WHEN the reconciler runs THEN it SHALL inspect up to 1000 expired reservations every 500 ms by default.
3. WHEN an email is sent THEN the provider call SHALL execute outside a database transaction; state transitions before and after the call SHALL use short transactions.
4. WHEN outbox and notification records are terminal and older than the retention window THEN a bounded cleanup SHALL remove them in referentially safe order.

### P1: Implementacao proporcional

**Historia:** Como avaliador, quero codigo que mostre apenas abstracoes usadas pelo sistema.

**Acceptance Criteria:**

1. WHEN event persistence is built THEN it SHALL use the same JDBC stack as the rest of the application and SHALL not depend on JPA.
2. WHEN reservation transitions execute THEN SQL SHALL remain the single runtime source of truth and unused in-memory transition methods SHALL not exist.
3. WHEN the container image is built THEN unit tests SHALL run before packaging.

### P1: AWS reproduzivel e observavel

**Historia:** Como avaliador, quero conseguir revisar um deploy que inicia corretamente e sinaliza falhas reais.

**Acceptance Criteria:**

1. WHEN the demo root is initialized with backend configuration THEN it SHALL declare an S3 backend and accept the runbook workflow.
2. WHEN the first deployment runs THEN the runbook SHALL create ECR first, build and push the immutable image, and only then create ECS services.
3. WHEN ECS starts any task THEN its task definition SHALL contain an ECS health check; API services SHALL support autoscaling beyond one task.
4. WHEN CloudWatch detects API errors, unhealthy targets, stopped worker capacity, queue backlog, DLQ messages, or RDS saturation THEN an alarm SHALL target the configured SNS topic.
5. CloudWatch SHALL use only published AWS metrics; no `AWS/ApiGateway/Throttle` metric SHALL exist.

### P1: Evidencia automatizada

**Historia:** Como avaliador, quero que cada mudanca seja verificavel sem confiar no README.

**Acceptance Criteria:**

1. WHEN a commit or pull request runs in GitHub Actions THEN unit tests and Terraform format, validation, and module tests SHALL run.
2. README and runbook SHALL distinguish local verification from historical remote evidence and SHALL not label `503` bursts as successful capacity proof.

## Edge Cases

- IF `Content-Length` is absent or false THEN streamed bytes SHALL still be counted.
- IF two workers attempt the same email THEN only a claim with a valid lease SHALL call the provider.
- IF the email provider outcome is ambiguous THEN retry remains at-least-once and the limitation SHALL be documented.
- IF no SNS subscription is confirmed THEN alarms remain configured but delivery is pending AWS confirmation.

## Requirement Traceability

| ID | Story | Task | Status |
| --- | --- | --- | --- |
| REM-01 | Persistencia temporal correta | T01 | Implemented; PostgreSQL runtime gate blocked |
| REM-02 | Fronteira HTTP resiliente | T02 | Validated locally |
| REM-03 | Processamento operacional seguro | T03 | Implemented; PostgreSQL runtime gate blocked |
| REM-04 | Implementacao proporcional | T04 | Validated locally |
| REM-05 | AWS reproduzivel e observavel | T05 | Validated locally |
| REM-06 | Evidencia automatizada | T06 | Validated locally |

**Coverage:** 6 total, 6 mapped, 0 unmapped.

## Success Criteria

- [ ] Todos os testes Java aplicaveis passam.
- [ ] `terraform fmt -check -recursive`, `terraform validate` e os testes de modulos passam.
- [ ] Nenhuma credencial ou operacao AWS remota e executada.
- [ ] O verificador independente registra PASS com evidencia por criterio.
