# Flash Booking Demo Specification

## Problem Statement

Construir o núcleo funcional de uma reserva de ingressos para flash sale. A solução deve impedir oversell, suportar múltiplas instâncias, expirar reservas e ser simples de operar por uma pessoa.

## Goals

- [x] Entregar os cinco endpoints do case com comportamento verificável.
- [x] Garantir consistência dos comandos sob concorrência.
- [x] Executar localmente por Docker Compose.
- [x] Provisionar todo o runtime AWS por Terraform.
- [x] Permitir programar a abertura e o encerramento opcional de uma flash sale.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Pagamento | Não consta no case. |
| Cadastro, senha e login de cliente final | Não constam no case; a borda autentica apenas operadores e entrevistadores. |
| Frontend | O desafio é backend. |
| Deploy contínuo para AWS | Exige credenciais e autorização remota; o CI localiza falhas sem aplicar infraestrutura. |
| Alta disponibilidade | Pertence ao plano de alta carga. |
| EKS | Exige carga operacional incompatível com a demo individual. |

## Assumptions & Open Questions

| Tema | Decisão | Justificativa | Confirmada? |
| --- | --- | --- | --- |
| Duração da reserva | 10 minutos configuráveis | Compatível com SQS message timer e comum para checkout. | yes |
| Janela comercial do evento | `startsAt` e `endsAt` opcionais, decididos pelo PostgreSQL | Sem início a venda vale imediatamente; sem fim não há encerramento temporal; a regra permanece consistente entre tasks. | yes |
| Aceitação da reserva | Síncrona, com estado PENDING | Bloqueio temporário, sem compra definitiva. | yes |
| Liberação após vencimento | Até expiresAt + 5 segundos com banco e processamento saudáveis | Limita estoque temporariamente bloqueado. | yes |
| Encerramento | Persistir código e descrição do motivo em CANCELLED e EXPIRED | O catálogo e o formato de consulta estão definidos nesta especificação. | yes |
| Banco | RDS PostgreSQL 16 Single-AZ | Econômico e suficiente para a demo. | yes |
| Cache | ElastiCache for Valkey compartilhado | Cacheia somente a disponibilidade consultada por `GET /events/{id}` por no máximo um segundo. | yes |
| Autenticação da API | API Gateway REST com IAM/SigV4, principals e CIDRs permitidos | Autentica antes dos containers e evita senha compartilhada. | yes |
| Serviços HTTP | `query-api` e `command-api` separados, usando a mesma imagem | Permite escala independente sem duplicar domínio. | yes |
| Cliente da reserva | `Customer` ligado por chave estrangeira | Permite consulta e notificação sem criar endpoint de cadastro. | yes |
| Notificação | E-mail assíncrono por outbox, SQS e SES | Não bloqueia a transação nem representa compra confirmada. | yes |
| Região | `sa-east-1` | Proximidade com o contexto brasileiro da vaga. | yes |
| Build | Maven | Convenção simples para Spring Boot. | yes |
| Corte automático de custo | O Budget mensal passa a US$50 e, ao atingir esse gasto real, interrompe ECS e RDS por SNS/Lambda | Preserva dados e infraestrutura recuperável; ALB, Valkey, rede e armazenamento não possuem pausa e permanecem como custo residual. | yes |

**Open questions:** none. The capacity envelope is measured in the benchmark task and is not a prerequisite for implementing the demo; remote-recovery limits belong to the target high-load architecture.

## User Stories

### P1: Gerenciar eventos

**História:** Como operador, quero criar um evento e consultar sua disponibilidade para controlar a venda.

**Acceptance Criteria:**

1. WHEN `POST /events` receives a name, positive capacity and optional valid `startsAt`/`endsAt` THEN the system SHALL create the event and return `201` with the persisted window values or nulls.
2. IF creation receives invalid data THEN the system SHALL return `400` as `application/problem+json`.
3. WHEN `GET /events/{id}` finds the event THEN the system SHALL return total and available capacity.
4. IF the event does not exist THEN the system SHALL return `404`.
5. WHEN `GET /events/{id}` finds a valid cache entry THEN the system SHALL return the displayed availability without querying PostgreSQL; on a cache miss, the system SHALL query PostgreSQL and populate the cache for at most one second.
6. IF `POST /events/{id}/reservations` references a nonexistent event THEN the system SHALL return `404` without persisting a customer, reservation, or outbox event, and SHALL record that final response in the idempotency contract.
7. IF `startsAt` is at or before database `createdAt`, IF `endsAt` is at or before `startsAt`, or IF an end-only event has `endsAt` before database `createdAt + 10 minutes`, THEN the system SHALL return `400 application/problem+json` without persisting an event.
8. WHEN `GET /events/{id}` finds a cached or persisted event THEN the system SHALL return the persisted `startsAt` and `endsAt` ISO-8601 values or nulls.

**Teste independente:** Criar um evento e consultá-lo pelo identificador retornado.

### P1: Reservar sem oversell

**História:** Como cliente, quero reservar ingressos sem que a capacidade seja ultrapassada e manter a reserva ligada aos meus dados.

**Acceptance Criteria:**

1. WHEN capacity is sufficient and the customer is valid THEN the system SHALL create or reuse the customer, decrement availability, and create a `PENDING` reservation linked to that customer and event in one transaction.
2. IF capacity is insufficient THEN the system SHALL return `409` without changing inventory, customer, or reservation.
3. WHILE multiple instances contend for the same event, the system SHALL keep availability between zero and total capacity.
4. WHEN `GET /reservations/{id}` finds the reservation THEN the system SHALL return its status, quantity, expiry, customer, closure reason, and the event reference containing only `id` and `name`.
5. WHEN `GET /reservations/{id}` is called THEN the system SHALL query PostgreSQL directly and SHALL NOT depend on Valkey availability.
6. IF the customer's name or email is invalid THEN the system SHALL return `400` without reserving capacity.
7. WHILE a reservation command evaluates an event, the system SHALL atomically require sufficient capacity, `startsAt` absent or reached, and `endsAt` absent or not reached; outside that window it SHALL return `409` without customer, reservation, outbox or availability changes.

**Teste independente:** Disparar reservas concorrentes acima da capacidade por ao menos dois processos de comandos e comprovar que a soma aceita não ultrapassa a capacidade.

### P1: Cancelar e expirar

**História:** Como cliente, quero cancelar uma reserva e ter reservas abandonadas expiradas automaticamente.

**Acceptance Criteria:**

1. WHEN `DELETE /reservations/{id}` closes a `PENDING` reservation before `expiresAt` THEN the system SHALL return `200`, transition it to `CANCELLED`, persist `CANCELLED_BY_REQUEST`, and return capacity exactly once; WHEN the database evaluates that command at or after `expiresAt`, after acquiring the reservation lock, THEN the system SHALL instead return `200`, transition it to `EXPIRED`, persist `RESERVATION_DEADLINE_REACHED`, and return capacity exactly once.
2. WHEN a `PENDING` reservation reaches `expiresAt`, with healthy database and expiration processing, THEN the system SHALL complete its transition to `EXPIRED` and return capacity exactly once by `expiresAt + 5 seconds`.
3. IF an expiration message is processed again THEN the system SHALL leave inventory and status unchanged.
4. IF asynchronous processing fails THEN the system SHALL apply bounded retries and route an exhausted failure to a DLQ.
5. WHEN a reservation transitions to `CANCELLED` or `EXPIRED` THEN the system SHALL persist the closure-reason code and description in the same transaction as the status and capacity return.
6. WHILE the current instant is earlier than `expiresAt`, the system SHALL prevent early reservation expiry.
7. WHEN cancellation or expiry commits its transaction THEN the system SHALL invalidate the cache entry for the affected event.
8. WHEN `GET /reservations/{id}` returns a terminal reservation THEN the system SHALL return a `closureReason` object with `code` and `description`; for `PENDING`, that object SHALL be null.

**Teste independente:** Cancelar e expirar reservas com duplicidade de mensagens e conferir o inventário final.

### P1: Idempotência e erros

**História:** Como integrador, quero repetir requisições com segurança e receber erros explícitos.

**Acceptance Criteria:**

1. WHEN a mutable operation repeats the same unexpired `Idempotency-Key` and request fingerprint THEN the system SHALL return the original result without a new effect.
2. IF an unexpired `Idempotency-Key` is reused with a different operation, normalized target, or payload hash THEN the system SHALL return `409`.
3. WHEN a response is produced THEN the system SHALL include a correlation identifier.
4. IF an unexpected error occurs THEN the system SHALL return `500` without exposing internal details.
5. WHEN a command accepts an `Idempotency-Key` THEN the system SHALL persist the key, operation, normalized target, payload hash, and final response for a 24-hour window measured by the PostgreSQL clock; at or after `expires_at`, the next command SHALL atomically reclaim the key as a new request, including under concurrent retries; the worker SHALL remove only expired records in bounded batches without extending the logical window.
6. IF a mutable endpoint does not receive an `Idempotency-Key` THEN the system SHALL return `400` as `application/problem+json` without executing the command.

**Teste independente:** Repetir requisições iguais e conflitantes antes do vencimento, reutilizar uma chave vencida em paralelo e provar que a limpeza limitada preserva registros ativos.

### P1: Notificar a reserva

**História:** Como cliente, quero receber por e-mail os dados da reserva para conseguir consultá-la antes da expiração.

**Acceptance Criteria:**

1. WHEN a reservation is confirmed as `PENDING` THEN the system SHALL write `ReservationCreated` to the outbox in the same transaction.
2. WHEN the worker processes `ReservationCreated` THEN the system SHALL send the reservation identifier, event, quantity, and `expiresAt` to the customer's email.
3. The email SHALL state that it is a temporary reservation and does not confirm a purchase or payment.
4. IF SES is unavailable THEN the system SHALL preserve the reservation, retry within a bound, and route an exhausted message to the notification DLQ.
5. WHEN the database, queue, worker, and SES are healthy THEN the system SHALL request SES delivery within 30 seconds after the reservation commits.
6. WHEN a worker processes a notification THEN it SHALL claim a bounded lease in a short database transaction and SHALL call the email provider outside a database transaction.

**Teste independente:** Criar uma reserva no Docker Compose, localizar o e-mail no Mailpit e conferir dados e aviso de reserva temporária.

### P1: Proteger a API e limitar abuso

**História:** Como operador, quero que apenas identidades autorizadas usem a API e que rajadas sejam bloqueadas antes dos containers.

**Acceptance Criteria:**

1. WHEN an AWS request lacks a valid SigV4 signature and `execute-api:Invoke` permission THEN API Gateway SHALL return `403` without reaching VPC Link.
2. WHEN an origin is outside the allowed CIDRs THEN the resource policy or WAF SHALL block the call before the ALB.
3. WHEN the GET routes are deployed in the demo THEN API Gateway SHALL configure a throttle target of 20 requests per second and burst 40. Validation SHALL record the effective stage setting and the response distribution of a signed burst. A specific `429` response is not a deterministic condition because API Gateway throttling is best effort.
4. WHEN the POST or DELETE routes are deployed in the demo THEN API Gateway SHALL configure a throttle target of 5 requests per second and burst 10. Validation SHALL record the effective stage setting for both methods and the response distribution of a signed burst on a safe command route. A specific `429` response is not a deterministic condition because API Gateway throttling is best effort.
5. API Gateway REST SHALL be the only public resource; ALB, ECS, PostgreSQL, Valkey, and SQS SHALL remain private.
6. WHEN spending reaches 50%, 80%, or 100% of the US$5 budget THEN AWS Budget SHALL issue an alert; the operational document SHALL state that the alert does not guarantee an immediate billing stop.

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
7. WHEN a push or pull request runs THEN CI SHALL execute the Java verification and Terraform format, validation, and module-test gates without applying AWS resources.

**Teste independente:** Executar os gates locais, o cenário multiprocesso e gerar um `terraform plan` completo.

### P1: Operar a demo por um painel único

**História:** Como operador, quero diagnosticar a saúde da demo em um painel CloudWatch único sem precisar conhecer previamente cada recurso AWS.

**Acceptance Criteria:**

1. WHEN o dashboard `flash-booking-demo-demo` for aberto THEN o sistema SHALL exibir volume, erros 4xx/5xx e latências p50/p95/p99 do API Gateway em gráficos distintos.
2. WHEN a saúde do runtime for analisada THEN o dashboard SHALL comparar tasks desejadas e em execução e SHALL exibir CPU e memória separadas para `query-api`, `command-api` e `worker`.
3. WHEN a rota interna for analisada THEN o dashboard SHALL exibir targets saudáveis e não saudáveis e latências por target group de consulta e comando com as dimensões publicadas pelo ALB.
4. WHEN dependências de dados e processamento assíncrono forem analisadas THEN o dashboard SHALL exibir CPU, conexões e capacidade livre do PostgreSQL; CPU, memória, conexões, hits e misses do Valkey; profundidade, idade e DLQs das filas SQS.
5. WHEN houver falha recente THEN o dashboard SHALL permitir investigar respostas 4xx/5xx do API Gateway e mensagens de erro dos três serviços ECS usando widgets de Logs Insights sobre os log groups existentes.
6. The dashboard SHALL use only existing AWS metrics and log groups, SHALL keep a 60-second metric period, and SHALL NOT create custom metrics, new alarms, or change log retention.

**Teste independente:** Executar o teste Terraform do módulo, inspecionar o JSON do dashboard e confirmar em `terraform plan` que a mudança remota atualiza somente o dashboard CloudWatch.

### P1: Acompanhar a jornada de negócio em português

**História:** Como cliente de negócio, quero entender o interesse e a conversão da demo sem precisar interpretar termos de infraestrutura.

**Acceptance Criteria:**

1. WHEN o dashboard `flash-booking-demo-negocio` for aberto THEN o sistema SHALL apresentar todos os títulos, textos explicativos e legendas visíveis em pt-BR.
2. WHEN o período do dashboard for selecionado THEN o sistema SHALL resumir eventos publicados, consultas de evento concluídas, respostas de reserva aceitas e cancelamentos concluídos nesse período.
3. WHEN a jornada for analisada THEN o sistema SHALL comparar consultas de evento, tentativas de reserva, consultas de reserva e solicitações de cancelamento ao longo do tempo.
4. WHEN os resultados de reserva forem analisados THEN o sistema SHALL separar respostas aceitas, respostas não concluídas por regra ou entrada e falhas técnicas.
5. WHEN existirem tentativas de reserva THEN o sistema SHALL calcular a taxa de aceite como `100 * respostas aceitas / tentativas`; WHEN não existirem tentativas THEN o sistema SHALL exibir zero.
6. WHEN a experiência percebida for analisada THEN o sistema SHALL exibir latências p50 e p95 separadas para consulta de evento e tentativa de reserva.
7. The dashboard SHALL state that its numbers count API interactions and responses, not unique customers, unique reservations, sales, or revenue.
8. The dashboard SHALL use only the existing detailed API Gateway metrics, SHALL use five-minute periods, and SHALL NOT create custom metrics, alarms, log queries, or retention changes.

**Teste independente:** Executar o teste Terraform do módulo, conferir que todo texto e legenda visível pertence ao catálogo pt-BR e validar remotamente a estrutura do dashboard publicado.

### P1: Interromper a demo por limite de custo

**História:** Como responsável pela conta, quero que a demo interrompa automaticamente os componentes pausáveis quando o gasto mensal real chegar a US$50 para limitar a continuidade do custo.

**Acceptance Criteria:**

1. WHEN o gasto mensal real da demo atingir US$50 THEN AWS Budgets SHALL publicar uma notificação em um tópico SNS dedicado e invocar o mecanismo de corte automático.
2. WHEN o mecanismo receber uma notificação do Budget THEN o sistema SHALL suspender o autoscaling e definir capacidade mínima e máxima zero para `query-api` e `command-api`, e SHALL definir `desiredCount` zero para `query-api`, `command-api` e `worker`.
3. WHEN o mecanismo receber uma notificação do Budget THEN o sistema SHALL solicitar a parada temporária da instância RDS PostgreSQL após solicitar a redução dos serviços ECS.
4. WHILE a notificação for entregue novamente ou o RDS já estiver parado THEN o mecanismo SHALL concluir sem criar recursos, apagar dados ou reativar serviços.
5. The mecanismo SHALL ter somente permissões para escrever seus logs, ajustar os três serviços ECS e seus dois alvos de autoscaling, e parar a instância RDS da demo.
6. The sistema SHALL manter ALB, Valkey, VPC, armazenamento, WAF, API Gateway e dados provisionados; esses recursos podem gerar custo residual e não fazem parte da interrupção automática.
7. The documentação SHALL declarar que AWS Budgets processa custo periodicamente; por isso o corte é iniciado após o alerta e não garante um teto financeiro exato em US$50.

**Teste independente:** Aplicar o plano Terraform, publicar uma mensagem SNS de teste autorizada e conferir no CloudWatch Logs que os três serviços receberam `desiredCount=0`, os dois alvos ECS ficaram suspensos em zero e a parada do RDS foi solicitada.

## Edge Cases

- SE a quantidade for zero ou negativa, ENTÃO o sistema DEVE retornar `400`.
- SE uma reserva referenciar evento inexistente, ENTÃO o sistema DEVE retornar `404` sem efeito parcial.
- SE `DELETE` e o worker de expiração concorrerem antes do prazo, ENTÃO somente a primeira transição DEVE liberar capacidade; SE o lock for obtido em `expiresAt` ou depois, ENTÃO o estado terminal DEVE ser `EXPIRED`, independentemente de qual caminho materializar o encerramento.
- SE a publicação no SQS atrasar, ENTÃO o reconciliador DEVE expirar a reserva pelo horário persistido.
- SE o banco estiver indisponível, ENTÃO o sistema DEVE falhar sem confirmar reserva.
- SE a hora PostgreSQL for igual a `startsAt`, ENTÃO uma reserva com capacidade DEVE ser aceita; SE for igual a `endsAt`, ENTÃO a reserva DEVE retornar `409` e preservar disponibilidade.
- SE o cache de evento falhar, ENTÃO `GET /events/{id}` DEVE consultar PostgreSQL com timeout de cache de 100 ms, no máximo 5 fallbacks simultâneos por task e circuito aberto após 5 falhas em 10 segundos.
- SE o SQS entregar uma mensagem duplicada, ENTÃO o consumidor DEVE produzir o mesmo estado final.
- SE o envio de e-mail for duplicado após resposta ambígua do provedor, ENTÃO a reserva DEVE permanecer inalterada e a ocorrência DEVE ser observável.
- SE `DELETE` ou o worker materializar primeiro o estado terminal correto para o instante decidido pelo PostgreSQL, ENTÃO a operação concorrente DEVE afetar zero linhas e não incrementar capacidade novamente.

## Requirement Traceability

| ID | História | Fase | Estado |
| --- | --- | --- | --- |
| DEMO-01 | Gerenciar eventos | Execute | Validated |
| DEMO-02 | Reservar sem oversell | Execute | Validated |
| DEMO-03 | Cancelar e expirar | Execute | Validated |
| DEMO-04 | Idempotência e erros | Execute | Validated |
| DEMO-05 | Executar e provisionar | Execute | Validated |
| DEMO-06 | Notificar a reserva | Execute | Validated |
| DEMO-07 | Proteger a API e limitar abuso | Execute | Validated |
| DEMO-08 | Janela comercial do evento | Execute | Verified |
| DEMO-09 | Operar a demo por um painel único | Execute | Validated |
| DEMO-10 | Acompanhar a jornada de negócio em português | Execute | Validated |
| DEMO-11 | Interromper a demo por limite de custo | Execute | Implemented |

**Cobertura:** 11 requisitos, 10 mapeados ao design, 1 em planejamento.

## Success Criteria

- [x] Os cinco endpoints passam nos testes de contrato.
- [x] Nenhum teste concorrente produz oversell.
- [x] Antes do prazo, `DELETE` resulta em CANCELLED; no prazo ou depois, resulta em EXPIRED; ambos devolvem capacidade uma vez sob concorrência.
- [x] Expiração saudável conclui devolução até expiresAt + 5 segundos, inclusive pelo reconciliador na ausência de mensagem.
- [x] CANCELLED e EXPIRED preservam código e descrição do motivo de encerramento.
- [x] `GET /events/{id}` usa cache Valkey com TTL máximo de um segundo e invalidação pós-commit; `GET /reservations/{id}` consulta PostgreSQL sem depender do cache.
- [x] Toda reserva possui cliente ligado por chave estrangeira e envia notificação assíncrona sem prometer compra.
- [x] Evento expõe janela opcional e o PostgreSQL bloqueia reservas antes da abertura ou no fim/depois do encerramento.
- [x] Consultas e comandos executam em serviços separados usando a mesma imagem e as mesmas regras de negócio.
- [x] Requisições anônimas não alcançam os containers e a borda mantém metas de throttling verificadas para cada método.
- [x] Docker Compose inicia a solução completa.
- [x] Terraform representa todos os recursos AWS da demo.
- [x] O dashboard CloudWatch reúne sinais de borda, runtime, dados, filas e investigação de logs sem criar telemetria adicional.
- [x] O dashboard de negócio apresenta jornada, aceite e experiência percebida em pt-BR sem confundir respostas HTTP com clientes únicos ou vendas.
- [ ] Ao atingir US$50 de gasto mensal real, a demo solicita a interrupção de ECS e RDS sem apagar dados ou infraestrutura.
