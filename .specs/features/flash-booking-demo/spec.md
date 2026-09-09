# Flash Booking Demo Specification

## Problem Statement

Construir o núcleo funcional de uma reserva de ingressos para flash sale. A solução deve impedir oversell, suportar múltiplas instâncias, expirar reservas e ser simples de operar por uma pessoa.

## Goals

- [ ] Entregar os cinco endpoints do case com comportamento verificável.
- [ ] Garantir consistência dos comandos sob concorrência.
- [ ] Executar localmente por Docker Compose.
- [ ] Provisionar todo o runtime AWS por Terraform.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Pagamento | Não consta no case. |
| Cadastro, senha e login de cliente final | Não constam no case; a borda autentica apenas operadores e entrevistadores. |
| Frontend | O desafio é backend. |
| Pipeline de CI/CD | Não será avaliada nesta entrega. |
| Alta disponibilidade | Pertence ao plano de alta carga. |
| EKS | Exige carga operacional incompatível com a demo individual. |

## Assumptions & Open Questions

| Tema | Decisão | Justificativa | Confirmada? |
| --- | --- | --- | --- |
| Duração da reserva | 10 minutos configuráveis | Compatível com SQS message timer e comum para checkout. | yes |
| Aceitação da reserva | Síncrona, com estado PENDING | Bloqueio temporário, sem compra definitiva; ADR 0002. | yes |
| Liberação após vencimento | Até expiresAt + 5 segundos com banco e processamento saudáveis | Limita estoque temporariamente bloqueado; ADR 0003. | yes |
| Encerramento | Persistir código e descrição do motivo em CANCELLED e EXPIRED | O catálogo e o formato de consulta estão definidos no ADR 0006. | yes |
| Banco | RDS PostgreSQL 16 Single-AZ | Econômico e suficiente para a demo. | yes |
| Cache | ElastiCache for Valkey compartilhado | Cacheia os dois GETs por no máximo um segundo; ADR 0005. | yes |
| Autenticação da API | API Gateway REST com IAM/SigV4, principals e CIDRs permitidos | Autentica antes dos containers e evita senha compartilhada; ADR 0012. | yes |
| Serviços HTTP | `query-api` e `command-api` separados, usando a mesma imagem | Permite escala independente sem duplicar domínio; ADR 0013. | yes |
| Cliente da reserva | `Customer` ligado por chave estrangeira | Permite consulta e notificação sem criar endpoint de cadastro; ADR 0010. | yes |
| Notificação | E-mail assíncrono por outbox, SQS e SES | Não bloqueia a transação nem representa compra confirmada; ADR 0011. | yes |
| Região | `sa-east-1` | Proximidade com o contexto brasileiro da vaga. | yes |
| Build | Maven | Convenção simples para Spring Boot. | yes |

**Open questions:** none. The capacity envelope is measured in the benchmark task and is not a prerequisite for implementing the demo; remote-recovery limits belong to the target high-load architecture.

## User Stories

### P1: Gerenciar eventos

**História:** Como operador, quero criar um evento e consultar sua disponibilidade para controlar a venda.

**Acceptance Criteria:**

1. WHEN `POST /events` receives a name and positive capacity THEN the system SHALL create the event and return `201`.
2. IF creation receives invalid data THEN the system SHALL return `400` as `application/problem+json`.
3. WHEN `GET /events/{id}` finds the event THEN the system SHALL return total and available capacity.
4. IF the event does not exist THEN the system SHALL return `404`.
5. WHEN `GET /events/{id}` finds a valid cache entry THEN the system SHALL return the displayed availability without querying PostgreSQL; on a cache miss, the system SHALL query PostgreSQL and populate the cache for at most one second.
6. IF `POST /events/{id}/reservations` references a nonexistent event THEN the system SHALL return `404` without persisting a customer, reservation, or outbox event, and SHALL record that final response in the idempotency contract.

**Teste independente:** Criar um evento e consultá-lo pelo identificador retornado.

### P1: Reservar sem oversell

**História:** Como cliente, quero reservar ingressos sem que a capacidade seja ultrapassada e manter a reserva ligada aos meus dados.

**Acceptance Criteria:**

1. WHEN capacity is sufficient and the customer is valid THEN the system SHALL create or reuse the customer, decrement availability, and create a `PENDING` reservation linked to that customer and event in one transaction.
2. IF capacity is insufficient THEN the system SHALL return `409` without changing inventory, customer, or reservation.
3. WHILE multiple instances contend for the same event, the system SHALL keep availability between zero and total capacity.
4. WHEN `GET /reservations/{id}` finds the reservation THEN the system SHALL return its status, quantity, expiry, event, customer, and closure reason.
5. WHEN `GET /reservations/{id}` finds a valid cache entry THEN the system SHALL return the reservation without querying PostgreSQL; on a cache miss, the system SHALL query PostgreSQL and populate the cache for at most one second.
6. IF the customer's name or email is invalid THEN the system SHALL return `400` without reserving capacity.

**Teste independente:** Disparar reservas concorrentes acima da capacidade por ao menos dois processos de comandos e comprovar que a soma aceita não ultrapassa a capacidade.

### P1: Cancelar e expirar

**História:** Como cliente, quero cancelar uma reserva e ter reservas abandonadas expiradas automaticamente.

**Acceptance Criteria:**

1. WHEN a `PENDING` reservation is cancelled THEN the system SHALL transition it to `CANCELLED` and return capacity exactly once.
2. WHEN a `PENDING` reservation reaches `expiresAt`, with healthy database and expiration processing, THEN the system SHALL complete its transition to `EXPIRED` and return capacity exactly once by `expiresAt + 5 seconds`.
3. IF an expiration message is processed again THEN the system SHALL leave inventory and status unchanged.
4. IF asynchronous processing fails THEN the system SHALL apply bounded retries and route an exhausted failure to a DLQ.
5. WHEN a reservation transitions to `CANCELLED` or `EXPIRED` THEN the system SHALL persist the closure-reason code and description in the same transaction as the status and capacity return.
6. WHILE the current instant is earlier than `expiresAt`, the system SHALL prevent early reservation expiry.
7. WHEN cancellation or expiry commits its transaction THEN the system SHALL invalidate the cache entries for the reservation and affected event.
8. WHEN `GET /reservations/{id}` returns a terminal reservation THEN the system SHALL return a `closureReason` object with `code` and `description`; for `PENDING`, that object SHALL be null.

**Teste independente:** Cancelar e expirar reservas com duplicidade de mensagens e conferir o inventário final.

### P1: Idempotência e erros

**História:** Como integrador, quero repetir requisições com segurança e receber erros explícitos.

**Acceptance Criteria:**

1. WHEN a mutable operation repeats the same `Idempotency-Key` and payload THEN the system SHALL return the original result without a new effect.
2. IF an `Idempotency-Key` is reused with a different payload THEN the system SHALL return `409`.
3. WHEN a response is produced THEN the system SHALL include a correlation identifier.
4. IF an unexpected error occurs THEN the system SHALL return `500` without exposing internal details.
5. WHEN a command accepts an `Idempotency-Key` THEN the system SHALL persist the key, operation, normalized target, payload hash, and final response for 24 hours; an identical repeat SHALL return the same response and an incompatible reuse SHALL return `409`.
6. IF a mutable endpoint does not receive an `Idempotency-Key` THEN the system SHALL return `400` as `application/problem+json` without executing the command.

**Teste independente:** Repetir requisições iguais e conflitantes, inclusive em paralelo.

### P1: Notificar a reserva

**História:** Como cliente, quero receber por e-mail os dados da reserva para conseguir consultá-la antes da expiração.

**Acceptance Criteria:**

1. WHEN a reservation is confirmed as `PENDING` THEN the system SHALL write `ReservationCreated` to the outbox in the same transaction.
2. WHEN the worker processes `ReservationCreated` THEN the system SHALL send the reservation identifier, event, quantity, and `expiresAt` to the customer's email.
3. The email SHALL state that it is a temporary reservation and does not confirm a purchase or payment.
4. IF SES is unavailable THEN the system SHALL preserve the reservation, retry within a bound, and route an exhausted message to the notification DLQ.
5. WHEN the database, queue, worker, and SES are healthy THEN the system SHALL request SES delivery within 30 seconds after the reservation commits.

**Teste independente:** Criar uma reserva no Docker Compose, localizar o e-mail no Mailpit e conferir dados e aviso de reserva temporária.

### P1: Proteger a API e limitar abuso

**História:** Como operador, quero que apenas identidades autorizadas usem a API e que rajadas sejam bloqueadas antes dos containers.

**Acceptance Criteria:**

1. WHEN an AWS request lacks a valid SigV4 signature and `execute-api:Invoke` permission THEN API Gateway SHALL return `403` without reaching VPC Link.
2. WHEN an origin is outside the allowed CIDRs THEN the resource policy or WAF SHALL block the call before the ALB.
3. WHEN a GET route exceeds 20 requests per second or a burst of 40 in the demo THEN API Gateway SHALL start throttling and return `429`.
4. WHEN a POST or DELETE route exceeds 5 requests per second or a burst of 10 in the demo THEN API Gateway SHALL start throttling and return `429`.
5. API Gateway REST SHALL be the only public resource; ALB, ECS, PostgreSQL, Valkey, and SQS SHALL remain private.
6. WHEN spending reaches 50%, 80%, or 100% of the US$100 budget THEN AWS Budget SHALL issue an alert; the operational document SHALL state that the alert does not guarantee an immediate billing stop.

**Teste independente:** Assinar uma chamada com role permitida, repetir sem assinatura e fora do CIDR, e validar por Terraform que nenhum backend possui entrada pública.

### P1: Executar e provisionar

**História:** Como avaliador, quero reproduzir a solução localmente e revisar sua arquitetura AWS.

**Acceptance Criteria:**

1. WHEN `docker compose up` completes THEN the system SHALL make local `query-api`, `command-api`, worker, PostgreSQL, Valkey, messaging, and Mailpit available.
2. WHEN the local concurrency profile starts THEN it SHALL run at least two `command-api` processes against the same PostgreSQL instance.
3. WHEN `terraform validate` runs THEN the infrastructure SHALL be valid.
4. WHEN the demo Terraform plan is applied with authorized credentials THEN the system SHALL create all AWS runtime resources.
5. The system SHALL require zero manual resource creation through the AWS console.
6. WHEN the demonstration reaches 1h30 THEN the runbook SHALL direct `terraform destroy` and verification of remaining resources.

**Teste independente:** Executar os gates locais, o cenário multiprocesso e gerar um `terraform plan` completo.

## Edge Cases

- SE a quantidade for zero ou negativa, ENTÃO o sistema DEVE retornar `400`.
- SE uma reserva referenciar evento inexistente, ENTÃO o sistema DEVE retornar `404` sem efeito parcial.
- SE cancelamento e expiração concorrerem, ENTÃO o sistema DEVE liberar capacidade uma vez.
- SE a publicação no SQS atrasar, ENTÃO o reconciliador DEVE expirar a reserva pelo horário persistido.
- SE o banco estiver indisponível, ENTÃO o sistema DEVE falhar sem confirmar reserva.
- SE o cache falhar, ENTÃO o sistema DEVE consultar PostgreSQL com timeout de cache de 100 ms, no máximo 5 fallbacks simultâneos por task e circuito aberto após 5 falhas em 10 segundos.
- SE o SQS entregar uma mensagem duplicada, ENTÃO o consumidor DEVE produzir o mesmo estado final.
- SE o envio de e-mail for duplicado após resposta ambígua do provedor, ENTÃO a reserva DEVE permanecer inalterada e a ocorrência DEVE ser observável.
- SE cancelamento ou expiração vencer a corrida, ENTÃO a operação concorrente DEVE afetar zero linhas e não incrementar capacidade novamente.

## Requirement Traceability

| ID | História | Fase | Estado |
| --- | --- | --- | --- |
| DEMO-01 | Gerenciar eventos | Execute | Implementing |
| DEMO-02 | Reservar sem oversell | Execute | Implementing |
| DEMO-03 | Cancelar e expirar | Execute | Implementing |
| DEMO-04 | Idempotência e erros | Execute | Implementing |
| DEMO-05 | Executar e provisionar | Execute | Implementing |
| DEMO-06 | Notificar a reserva | Design | Em design |
| DEMO-07 | Proteger a API e limitar abuso | Design | Em design |

**Cobertura:** 7 requisitos, 7 mapeados ao design, nenhum sem mapeamento.

## Success Criteria

- [ ] Os cinco endpoints passam nos testes de contrato.
- [ ] Nenhum teste concorrente produz oversell.
- [ ] Cancelamento e expiração devolvem capacidade uma vez.
- [ ] Expiração saudável conclui devolução até expiresAt + 5 segundos, inclusive pelo reconciliador na ausência de mensagem.
- [ ] CANCELLED e EXPIRED preservam código e descrição do motivo de encerramento.
- [ ] Os dois GET usam cache Valkey com TTL máximo de um segundo e invalidação pós-commit.
- [ ] Toda reserva possui cliente ligado por chave estrangeira e envia notificação assíncrona sem prometer compra.
- [ ] Consultas e comandos executam em serviços separados usando a mesma imagem e as mesmas regras de negócio.
- [ ] Requisições anônimas não alcançam os containers e excesso recebe `429` na borda.
- [ ] Docker Compose inicia a solução completa.
- [ ] Terraform representa todos os recursos AWS da demo.
