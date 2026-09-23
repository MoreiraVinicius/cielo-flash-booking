# Flash Booking Demo Tasks

## Execution Protocol

Execute estas tarefas com a skill `tlc-spec-driven`. Uma tarefa termina somente após seus testes e gate passarem. Atualize este arquivo antes de criar um commit Conventional Commit atômico.

**Design:** `.specs/features/flash-booking-demo/design.md`
**Status:** Complete
**Task count:** 35

## Test Coverage Matrix

> Nenhum projeto ou padrão de testes existia. Foram aplicados defaults fortes derivados da especificação.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Domínio e casos de uso | unit | Todos os ramos, critérios e transições | `src/test/java/**/*Test.java` | `./mvnw test` |
| Persistência, cache e mensageria | integration | Queries, transações, cache hit/miss/invalidação, duplicidade e falhas | `src/test/java/**/*IT.java` | `./mvnw verify -Pintegration` |
| Controllers HTTP | integration | Cinco rotas, sucesso, validações e erros | `src/test/java/**/*ControllerIT.java` | `./mvnw verify -Pintegration` |
| Cliente e notificação | unit + integration | Vínculo Customer-Reservation, mascaramento, outbox, e-mail, retry e DLQ | `src/test/java/**/*Customer*Test.java`, `src/test/java/**/*Notification*IT.java` | `./mvnw verify -Pintegration` |
| Segurança e abuso | integration + static | IAM na borda, backend privado, throttling, WAF e payload máximo | `infra/**/*.tftest.hcl`, `src/test/java/**/*Security*IT.java` | `./mvnw verify -Pintegration` e `terraform test` |
| Concorrência | integration | Oversell, cancelamento e expiração concorrentes | `src/test/java/**/*ConcurrencyIT.java` | `./mvnw verify -Pintegration` |
| Terraform | static | Formatação, validade e segurança | `infra/**/*.tf` | `terraform fmt -check -recursive && terraform validate` |
| Configuração e documentação | none | Build e inspeção | - | gate de build |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Domínio puro | `./mvnw test` |
| Full | Banco, REST ou SQS | `./mvnw verify -Pintegration` |
| Build | Fim de fase | `./mvnw clean verify -Pintegration` |
| Infra | Terraform | `terraform fmt -check -recursive && terraform validate` |

## Execution Plan

```text
Phase 1
T01 -> T02
T01 -> T03
T02 -> T04
T03 -> T04
T01 -> T05
T05 -> T04

Phase 2
T06 -> T07
T07 -> T08
T09 -> T10
T08 -> T10
T10 -> T11
T10 -> T12

Phase 3
T13 -> T14
T14 -> T15
T15 -> T16
T16 -> T17
T15 -> T18

Phase 4
T17 -> T19
T18 -> T19
T19 -> T20
T21 -> T22
T22 -> T23
T22 -> T24
T23 -> T24
T24 -> T25

Phase 5
T25 -> T26
T26 -> T27
T27 -> T28
T28 -> T29
T29 -> T30
T30 -> T31
T31 -> T32
T32 -> T33
T33 -> T34
T34 -> T35

```

## Task Breakdown

## Phase 1: Foundation

### T01: Criar o projeto Maven

**Status:** Complete
**What:** Configurar Java 21, Spring Boot, Maven Wrapper, dependências para PostgreSQL, Valkey e mensageria, além dos gates de build.
**Where:** `pom.xml`
**Depends on:** None
**Requirement:** DEMO-05
**Done when:** O projeto compila, inicia e o build vazio passa.
**Tests:** none, build gate only
**Gate:** Build
**Commit:** `build: initialize spring boot project`

### T02: Modelar Event

**Status:** Complete
**What:** Criar o agregado de evento com invariantes de nome e capacidade.
**Where:** `src/main/java/com/cielo/flashbooking/domain/event/`
**Depends on:** T01
**Requirement:** DEMO-01
**Done when:** Capacidade inválida é rejeitada e disponibilidade inicia igual à capacidade.
**Tests:** unit, incluídos na tarefa
**Gate:** Quick
**Commit:** `feat(event): add event aggregate`

### T03: Modelar Customer e Reservation

**Status:** Complete
**What:** Criar o agregado de reserva, a entidade Customer, o vínculo obrigatório entre ambos e as transições `PENDING`, `CANCELLED` e `EXPIRED`.
**Where:** `src/main/java/com/cielo/flashbooking/domain/reservation/`
**Depends on:** T01
**Requirement:** DEMO-03
**Done when:** Toda reserva pertence a um cliente e um evento; o Java trata o e-mail com uma regra única antes de buscar ou persistir; cancelamento antes de `expiresAt` resulta em CANCELLED e no prazo/depois resulta em EXPIRED; somente transições válidas alteram o estado e indicam devolução única; estados terminais usam o catálogo imutável definido na especificação.
**Tests:** unit, incluídos na tarefa
**Gate:** Quick
**Commit:** `feat(reservation): add reservation state model`

### T04: Criar o schema relacional

**Status:** Complete
**What:** Criar migrations de clientes, eventos, reservas, idempotência, outbox e entrega de notificação com os índices e constraints definidos no design da demo.
**Where:** `src/main/resources/db/migration/`
**Depends on:** T02, T03, T05
**Requirement:** DEMO-01, DEMO-02, DEMO-03, DEMO-04
**Done when:** O schema sobe do zero, possui somente a coluna `customer.email` com unicidade e rejeita cliente ausente, e-mail duplicado, estados, quantidades, disponibilidade, chaves de idempotência e motivos terminais inválidos ou ausentes.
**Tests:** integration, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(database): add initial schema`

### T05: Criar infraestrutura de testes

**Status:** Complete
**What:** Configurar Testcontainers para PostgreSQL, Valkey, mensageria e Mailpit locais.
**Where:** `src/test/java/com/cielo/flashbooking/support/`
**Depends on:** T01
**Requirement:** DEMO-05
**Done when:** Testes de integração iniciam PostgreSQL, Valkey, mensageria e Mailpit isolados, limpam estado e permitem simular indisponibilidade de cache, fila e e-mail.
**Tests:** integration, self-test incluído
**Gate:** Full
**Commit:** `test: add integration test infrastructure`

## Phase 2: Functional API

### T06: Padronizar erros e correlation ID

**Status:** Complete
**What:** Implementar `application/problem+json`, mapeamento de exceções e correlation ID.
**Where:** `src/main/java/com/cielo/flashbooking/controller/error/`
**Depends on:** T01
**Requirement:** DEMO-04
**Done when:** Erros 400, 404, 409 e 500 têm contrato estável e não vazam detalhes.
**Tests:** integration, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(api): add problem details and correlation id`

### T07: Implementar criação de evento

**Status:** Complete
**What:** Implementar `POST /events` com validação e persistência.
**Where:** `src/main/java/com/cielo/flashbooking/event/controller/`
**Depends on:** T04, T05, T06
**Requirement:** DEMO-01
**Done when:** Evento válido retorna 201 e entradas inválidas retornam 400.
**Tests:** unit e integration, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(event): create event endpoint`

### T08: Implementar consulta de evento

**Status:** Complete
**What:** Implementar `GET /events/{id}` com cache-aside Valkey, TTL máximo de um segundo e fallback protegido ao PostgreSQL.
**Where:** `src/main/java/com/cielo/flashbooking/event/controller/`
**Depends on:** T07
**Requirement:** DEMO-01
**Done when:** Evento existente retorna 200 e inexistente 404; hit evita PostgreSQL, miss o preenche e falha de cache respeita timeout de 100 ms, circuito e até 5 fallbacks simultâneos por task.
**Tests:** unit e integration, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(event): get event availability endpoint`

### T09: Implementar inventário atômico

**Status:** Complete
**What:** Criar operações condicionais de decremento e incremento de capacidade.
**Where:** `src/main/java/com/cielo/flashbooking/adapter/out/persistence/inventory/`
**Depends on:** T04, T05
**Requirement:** DEMO-02
**Done when:** Decremento insuficiente afeta zero linhas e disponibilidade nunca fica negativa.
**Tests:** integration e concurrency, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(inventory): add atomic capacity operations`

### T10: Implementar criação de reserva

**Status:** Complete
**What:** Implementar `POST /events/{id}/reservations` com cliente, inventário, reserva e outbox na mesma transação.
**Where:** `src/main/java/com/cielo/flashbooking/reservation/controller/`
**Depends on:** T03, T08, T09
**Requirement:** DEMO-02
**Done when:** Reserva válida faz upsert atômico de Customer, liga-o à reserva e retorna 201; cliente inválido retorna 400; evento inexistente retorna 404; falta de capacidade retorna 409; nenhum erro deixa efeito parcial; após commit, a chave de disponibilidade do evento é invalidada.
**Tests:** unit, integration e concurrency, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(reservation): create reservation endpoint`

### T11: Implementar consulta de reserva

**Status:** Complete
**What:** Implementar `GET /reservations/{id}` como leitura direta do PostgreSQL, sem cache, retornando o evento somente como `{id, name}`.
**Where:** `src/main/java/com/cielo/flashbooking/reservation/controller/`
**Depends on:** T10
**Requirement:** DEMO-02
**Done when:** Reserva existente retorna referência estável do evento, cliente, estado, quantidade, expiração e motivo; inexistente retorna 404; a rota permanece disponível quando Valkey falha e não expõe capacidade ou disponibilidade do evento.
**Tests:** unit e integration, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(reservation): get reservation endpoint`

### T12: Implementar cancelamento

**Status:** Complete
**What:** Implementar `DELETE /reservations/{id}` com decisão terminal pelo relógio PostgreSQL após o lock e devolução condicional.
**Where:** `src/main/java/com/cielo/flashbooking/domain/reservation/`, `src/main/java/com/cielo/flashbooking/reservation/`, `src/main/java/com/cielo/flashbooking/adapter/out/persistence/reservation/`, `src/test/java/com/cielo/flashbooking/reservation/`
**Depends on:** T10
**Requirement:** DEMO-03
**Done when:** `DELETE` retorna 200 e CANCELLED antes de `expiresAt`, ou 200 e EXPIRED no prazo/depois; uma espera por lock que atravessa o prazo resulta em EXPIRED; repetição, expiração concorrente e cancelamento concorrente devolvem capacidade uma vez; estado e motivo definidos na especificação são persistidos na mesma transação, preservando um encerramento já efetivado e invalidando somente a chave do evento após o commit.
**Tests:** unit, integration e concurrency, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(reservation): cancel reservation endpoint`

## Phase 3: Distributed Behavior

### T13: Implementar idempotência

**Status:** Complete
**What:** Persistir chave, operação, alvo normalizado, hash do payload e resposta dos comandos mutáveis em uma janela de 24 horas decidida pelo PostgreSQL; permitir reivindicação atômica após o vencimento e limpar registros vencidos em lotes no worker.
**Where:** `src/main/java/com/cielo/flashbooking/application/idempotency/`, `src/main/java/com/cielo/flashbooking/adapter/out/persistence/idempotency/` e `src/main/resources/application.yml`
**Depends on:** T07, T10, T12
**Requirement:** DEMO-04
**Done when:** Antes do vencimento, repetição com operação, alvo e payload iguais retorna a resposta final persistida e reutilização incompatível retorna 409; respostas finais de domínio, inclusive 409 por capacidade, são preservadas; 5xx não é preservado; depois do vencimento, uma nova impressão digital pode reivindicar a chave, inclusive com concorrência gerando um único novo efeito; a limpeza limitada remove apenas registros vencidos e não define a validade. Seguir a decisão de idempotência em STATE.md.
**Tests:** unit para binding/defaults, overrides de ambiente e rejeição de lotes/durações inválidos; integration e concurrency para replay, reclaim e limpeza, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(api): add persistent idempotency`

### T14: Implementar transactional outbox

**Status:** Complete
**What:** Gravar `ReservationCreated` e agendamento de expiração na mesma transação da reserva.
**Where:** `src/main/java/com/cielo/flashbooking/application/outbox/`
**Depends on:** T10, T13
**Requirement:** DEMO-03
**Done when:** Commit grava reserva e os dois eventos de outbox; rollback não grava nenhum deles.
**Tests:** integration, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(outbox): persist reservation events atomically`

### T15: Implementar publisher SQS

**Status:** Complete
**What:** Publicar eventos do outbox nas filas separadas de expiração e notificação, calculando delay quando aplicável.
**Where:** `src/main/java/com/cielo/flashbooking/adapter/out/messaging/publisher/`
**Depends on:** T14
**Requirement:** DEMO-03
**Done when:** Cada tipo chega à fila correta; tentativas não perdem evento e publicação duplicada permanece segura.
**Tests:** integration, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(messaging): publish expiration events to sqs`

### T16: Implementar consumidor de expiração

**Status:** Complete
**What:** Consumir SQS e executar `PENDING -> EXPIRED` com devolução única.
**Where:** `src/main/java/com/cielo/flashbooking/feature/reservation/expire/`
**Depends on:** T12, T15
**Requirement:** DEMO-03
**Done when:** Mensagem válida expira com commit até expiresAt + 5 segundos em operação saudável; o relógio PostgreSQL decide a elegibilidade; mensagem antecipada não expira a reserva; duplicidade não repete efeito. EXPIRED persiste código e descrição do motivo junto ao estado, devolução e invalidação das chaves afetadas. Testes medem o prazo definido na especificação.
**Tests:** unit, integration e concurrency, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(reservation): expire reservations from sqs`

### T17: Implementar reconciliador

**Status:** Complete
**What:** Buscar ao menos a cada um segundo reservas vencidas ainda pendentes e reaplicar expiração idempotente.
**Where:** `src/main/java/com/cielo/flashbooking/application/reconciliation/`
**Depends on:** T16
**Requirement:** DEMO-03
**Done when:** Reserva sem mensagem tem devolução concluída até expiresAt + 5 segundos com banco e reconciliador saudáveis; múltiplos workers não duplicam devolução nem sobrescrevem motivo terminal. A busca usa o relógio PostgreSQL; testes medem o prazo definido na especificação.
**Tests:** integration e concurrency, incluídos na tarefa
**Gate:** Full
**Commit:** `feat(reservation): reconcile expired reservations`

### T18: Implementar notificação de reserva por e-mail

**Status:** Complete
**What:** Consumir `ReservationCreated`, controlar tentativas conhecidas e enviar pelo SES a confirmação de reserva temporária.
**Where:** `src/main/java/com/cielo/flashbooking/notification/email/`
**Depends on:** T15
**Requirement:** DEMO-06
**Done when:** O e-mail contém reserva, evento, quantidade, `expiresAt` e aviso de que não confirma compra; envio saudável é solicitado em até 30 segundos; falha não reverte a reserva, usa retry, DLQ e alarme; o destinatário aparece mascarado em logs; consumidor de e-mail usa timeout e pool separados da expiração.
**Tests:** unit e integration com Mailpit, incluídos
**Gate:** Full
**Commit:** `feat(notification): email temporary reservation details`

## Phase 4: Packaging and AWS

### T19: Criar imagem da aplicação

**Status:** Complete
**What:** Criar Dockerfile multi-stage com usuário sem privilégios e health check.
**Where:** `Dockerfile`
**Depends on:** T17, T18
**Requirement:** DEMO-05
**Done when:** A mesma imagem inicia como `query-api`, `command-api` ou `worker`, ativa apenas seus controllers/consumidores e passa no health check.
**Tests:** none, build gate only
**Gate:** Build
**Commit:** `build: add production container image`

### T20: Criar ambiente Docker Compose

**Status:** Complete
**What:** Orquestrar query-api, command-api, worker, PostgreSQL, Valkey, mensageria e Mailpit locais.
**Where:** `compose.yaml`
**Depends on:** T19
**Requirement:** DEMO-05
**Done when:** `docker compose up` disponibiliza os cinco endpoints, processa expiração e exibe o e-mail no Mailpit; o perfil de concorrência inicia ao menos duas instâncias de command-api.
**Tests:** e2e smoke, incluído na tarefa
**Gate:** Full
**Commit:** `build: add local compose environment`

### T21: Criar bootstrap Terraform

**Status:** Complete
**What:** Provisionar bucket S3 criptografado, versionado e bloqueado para state remoto.
**Where:** `infra/bootstrap/`
**Depends on:** None
**Requirement:** DEMO-05
**Done when:** O módulo valida e não expõe state publicamente.
**Tests:** static
**Gate:** Infra
**Commit:** `infra: add terraform state bootstrap`

### T22: Criar rede AWS demo

**Status:** Complete
**What:** Provisionar VPC, duas AZs, sub-redes públicas/privadas/isoladas, rotas, NAT e security groups.
**Where:** `infra/modules/network/`
**Depends on:** T21
**Requirement:** DEMO-05
**Done when:** Apenas API Gateway/ALB alcançam a API e apenas ECS alcança o banco.
**Tests:** static e terraform test, incluídos na tarefa
**Gate:** Infra
**Commit:** `infra: add demo network module`

### T23: Criar dados, cache, e-mail e mensageria AWS

**Status:** Complete
**What:** Provisionar RDS, ElastiCache for Valkey econômico, Secrets Manager, filas/DLQs separadas de expiração e notificação, identidade SES e políticas de redrive.
**Where:** `infra/modules/data-plane/`
**Depends on:** T22
**Requirement:** DEMO-03, DEMO-05, DEMO-06
**Done when:** Recursos validam, usam criptografia e não têm acesso público; Valkey aceita somente ECS, filas isolam expiração/notificação e SES aceita somente o worker autorizado e identidades verificadas na demo.
**Tests:** static e terraform test, incluídos na tarefa
**Gate:** Infra
**Commit:** `infra: add database and messaging resources`

### T24: Criar compute AWS

**Status:** Complete
**What:** Provisionar ECR e serviços ECS separados de query-api, command-api e worker apontando para a mesma imagem, com task definitions e IAM mínimo.
**Where:** `infra/modules/compute/`
**Depends on:** T22, T23
**Requirement:** DEMO-05, DEMO-06
**Done when:** Os três serviços recebem modos e configuração sem segredos em texto claro; somente worker acessa SQS/SES, e todos usam `awsvpc`, limites fixos da demo e circuit breaker de deployment.
**Tests:** static e terraform test, incluídos na tarefa
**Gate:** Infra
**Commit:** `infra: add ecs fargate services`

### T25: Criar entrada, autenticação e observabilidade AWS

**Status:** Complete
**What:** Provisionar API Gateway REST com IAM/SigV4, `ApiInvokerRole`, principals confiáveis parametrizados, resource policy, WAF, throttling por método, VPC Link V2, ALB interno, target groups separados, logs, dashboard, Budget e alarmes essenciais.
**Where:** `infra/modules/edge-observability/`
**Depends on:** T24
**Requirement:** DEMO-04, DEMO-05, DEMO-07
**Done when:** GET roteia somente para query-api e POST/DELETE para command-api; a role de invocação possui apenas `execute-api:Invoke` e não provisiona recursos; chamada sem role recebe 403 antes do VPC Link; cada método tem sua meta de throttling configurada e verificável; API Gateway é o único recurso público; Budget alerta em 50%, 80% e 100%.
**Tests:** static e terraform test, incluídos na tarefa
**Gate:** Infra
**Commit:** `infra: expose and monitor demo api`

## Phase 5: Quality and Handoff

### T26: Aplicar hardening de segurança

**Status:** Complete
**What:** Restringir actuator, sanitizar erros, mascarar logs e revisar dependências.
**Where:** `src/main/java/com/cielo/flashbooking/config/security/`
**Depends on:** T17, T18, T25
**Requirement:** DEMO-04, DEMO-06, DEMO-07
**Done when:** Actuator não é roteado publicamente, payloads têm limite, PII e credenciais não aparecem nos logs e scans não apontam segredo ou vulnerabilidade crítica conhecida.
**Tests:** unit e integration, incluídos na tarefa
**Gate:** Build
**Commit:** `fix(security): harden application boundaries`

### T27: Executar benchmark da demo

**Status:** Complete
**What:** Criar cenários separados de consultas, reservas e carga mista, incluindo ao menos dois processos de command-api e registrando TPS, p95, p99, hit rate, conexões e lock waits.
**Where:** `performance/demo/`
**Depends on:** T20, T26
**Requirement:** DEMO-02
**Done when:** O envelope sustentável e o primeiro gargalo estão documentados e oversell permanece zero.
**Tests:** performance
**Gate:** Full
**Commit:** `test(performance): establish demo capacity baseline`

### T28: Documentar execução e uso de IA

**What:** Criar README, roteiro de Case Review, comandos locais/AWS, SigV4, credenciais temporárias, apply/destroy, trade-offs e registro de IA.
**Where:** `README.md`
**Depends on:** T20, T25, T27
**Requirement:** DEMO-05
**Done when:** Uma pessoa externa reproduz build, testes, plan Terraform e explica as decisões.
**Tests:** none, review gate only
**Gate:** Build
**Commit:** `docs: document demo execution and decisions`

**Status:** Complete

### T29: Verificar a demo contra a especificação e o case

**Status:** Complete
**What:** Executar todos os gates, revisar cada AC e realizar discrimination sensor.
**Where:** `.specs/features/flash-booking-demo/validation.md`
**Depends on:** T28
**Requirement:** DEMO-01, DEMO-02, DEMO-03, DEMO-04, DEMO-05, DEMO-06, DEMO-07
**Done when:** Validation registra PASS com evidência `file:line` para todos os critérios locais e estáticos, e com evidência remota datada para comportamentos AWS. Para throttling de API Gateway, a validação confere a configuração efetiva e registra a distribuição observada do burst, sem alegar `429` determinístico. Descrição sem teste ou observação remota não conta como evidência.
**Tests:** unit, integration, concurrency, e2e, performance e infra
**Gate:** Build + Infra
**Commit:** `test: validate flash booking demo`

### T30: Implementar janela comercial do evento

**Status:** Complete
**What:** Atualizar a demo com a janela opcional de flash sale e validar sua criação, leitura em cache, bloqueio autoritativo de reserva e documentação operacional.
**Where:** `.specs/features/flash-sale-window/`, `src/main/`, `src/test/`, `docs/`, `postman/` e scripts de execução relevantes.
**Depends on:** T29
**Requirement:** DEMO-08
**Done when:** `startsAt` e `endsAt` opcionais respeitam os limites decididos pelo PostgreSQL; reservas fora da janela não causam efeito parcial; cache, contrato HTTP, documentação e coleção Postman expõem a janela; todos os gates da feature passam.
**Tests:** unit, integration, cache, HTTP e migration
**Gate:** Build
**Commit:** `feat(event): add flash sale window`

### T31: Tornar o dashboard CloudWatch operacional

**Status:** Complete
**What:** Reorganizar o dashboard da demo em sinais de borda, runtime, dados/filas e investigação, usando métricas e log groups existentes com dimensões AWS válidas.
**Where:** `infra/modules/edge-observability/`, `.specs/features/flash-booking-demo/` e `.specs/STATE.md`
**Depends on:** T30
**Requirement:** DEMO-09
**Done when:** O dashboard possui seções e gráficos para API Gateway, ALB, ECS, RDS, Valkey e SQS; duas consultas de logs investigam falhas recentes; testes Terraform inspecionam sua estrutura; o plano remoto não cria nem destrói recursos e limita a alteração ao dashboard.
**Tests:** static, terraform test, terraform validate e terraform plan
**Gate:** Infra
**Commit:** `feat(observability): expand demo dashboard`

### T32: Criar o dashboard de negócio em pt-BR

**Status:** Complete
**What:** Criar uma visão CloudWatch separada e compreensível para público de negócio, com jornada, respostas aceitas, taxa de aceite e latência percebida em pt-BR.
**Where:** `infra/modules/edge-observability/`, `.specs/features/flash-booking-demo/` e `.specs/STATE.md`
**Depends on:** T31
**Requirement:** DEMO-10
**Done when:** `flash-booking-demo-negocio` usa apenas métricas detalhadas existentes do API Gateway; todo texto e legenda visível está em pt-BR; a limitação entre interações HTTP e entidades/vendas únicas está explícita; testes estruturais e observação remota comprovam os oito critérios.
**Tests:** static, terraform test, terraform validate e leitura remota do dashboard
**Gate:** Infra
**Commit:** `feat(observability): add business dashboard in pt-br`

### T33: Encaminhar o alerta de US$50 ao corte automático

**Status:** Complete
**What:** Alterar o Budget mensal para US$50, encaminhar a notificação de gasto real de 100% por um SNS dedicado e permitir somente AWS Budgets publicar nesse tópico.
**Where:** `infra/modules/edge-observability/` e `infra/environments/demo/`
**Depends on:** T32
**Requirement:** DEMO-11
**Done when:** O Budget tem limite mensal `50`, preserva os avisos em 50%, 80% e 100%, envia o aviso de 100% ao tópico SNS dedicado e o tópico permite publicação somente por `budgets.amazonaws.com` da conta atual.
**Tests:** terraform test e terraform validate, incluídos na tarefa
**Gate:** Infra
**Commit:** `feat(cost): route budget limit alert to sns`

### T34: Criar o mecanismo recuperável de corte de custo

**Status:** Complete
**What:** Criar Lambda, função de parada idempotente, role de mínimo privilégio, assinatura SNS, permissões e testes que congelam os dois alvos de autoscaling, param os três serviços ECS e solicitam a parada do RDS.
**Where:** composição Terraform da demo e módulo `edge-observability`
**Depends on:** T33
**Requirement:** DEMO-11
**Done when:** A Lambda só é invocável pelo SNS operacional, possui somente permissões de logs, `RegisterScalableTarget`, `UpdateService` e `StopDBInstance` nos recursos da demo, e seu handler trata reentrega SNS/RDS já parado sem reativar ou apagar recursos.
**Tests:** unit do handler, terraform test e terraform validate, incluídos na tarefa
**Gate:** Infra
**Commit:** `feat(cost): stop recoverable demo resources at budget limit`

### T35: Corrigir o identificador do alvo de autoscaling

**Status:** Complete
**What:** Separar o ARN do cluster ECS, usado para atualizar serviços, do nome do cluster, usado no identificador `service/<cluster>/<serviço>` do Application Auto Scaling.
**Where:** composição Terraform da demo e módulo `edge-observability`
**Depends on:** T34
**Requirement:** DEMO-11
**Done when:** A Lambda envia o ARN ao `UpdateService` e usa somente o nome do cluster para congelar `query-api` e `command-api` no Application Auto Scaling; o teste unitário distingue os dois valores.
**Tests:** unit do handler e terraform test, incluídos na tarefa
**Gate:** Infra
**Commit:** `fix(cost): use cluster name for autoscaling target`

### T36: Habilitar administração temporária do RDS na demo

**Status:** In Progress
**What:** Implementar a exceção exclusiva da demo para DataGrip: endpoint público RDS, TLS obrigatório, uma única regra `/32` e rota IGW temporária nos subnets de dados existentes, sem mover o DB subnet group.
**Where:** `infra/environments/demo/`, `infra/modules/network/`, `infra/modules/data-plane/` e documentação operacional.
**Depends on:** T22, T23
**Requirement:** DEMO-12
**Done when:** Com a flag desligada, RDS e subnets de dados continuam privados; com a flag ligada e um `/32`, RDS é público, TLS é obrigatório, a única entrada adicional é `5432` desse `/32`, Valkey não recebe entrada pública e o banco permanece no subnet group existente. Specs afirmam que a exceção dura somente a demo e não é arquitetura high-load.
**Tests:** `terraform test` nos módulos `network` e `data-plane`, `terraform validate` da demo e observação remota do RDS, rota e security group.
**Gate:** Infra

## Dependency Cross-Check

| Phase | Tasks | Dependency status |
| --- | --- | --- |
| Foundation | T01-T05 | T02/T03/T05 dependem de T01; T04 depende dos modelos e da infraestrutura de integração. Match. |
| Functional API | T06-T12 | Banco, suporte e erros precedem endpoints; reserva depende de inventário. Match. |
| Distributed | T13-T18 | Idempotência precede outbox; publisher precede expiração e notificação; consumer precede reconcile. Match. |
| AWS | T19-T25 | Imagem precede Compose; rede precede dados/compute; compute precede entrada. Match. |
| Quality | T26-T36 | Hardening precede benchmark; benchmark precede docs e validação; a janela comercial sucede a baseline validada; os painéis operacional e de negócio sucedem a infraestrutura implantada; o roteamento do Budget precede a Lambda de corte, T35 corrige o identificador do alvo escalável e T36 depende da rede e do plano de dados existentes. Match. |

## Test Co-location Validation

| Task group | Layer | Matrix requires | Plan | Status |
| --- | --- | --- | --- | --- |
| T02-T03 | Domain | unit | Tests in same task | OK |
| T04-T18 | Persistence/controllers/messaging | integration | Tests in same task | OK |
| T19-T20 | Packaging | smoke/build | Tests in same task | OK |
| T21-T25 | Terraform | static/terraform test | Tests in same task | OK |
| T26-T27 | Security/performance | integration/performance | Tests in same task | OK |
| T28 | Docs | build/review | No deferred production tests | OK |
| T29 | Verification | all | Fresh verifier | OK |
| T30 | Event window | unit/integration/cache/HTTP/migration | Feature plan mantém os testes junto da implementação | OK |
| T31 | CloudWatch dashboard | static/terraform test/plan | Testes estruturais e plano permanecem junto da alteração Terraform | OK |
| T32 | Business dashboard | static/terraform test/remote read | Catálogo pt-BR e semântica das métricas são verificados junto da alteração Terraform | OK |
| T33 | Budget/SNS | terraform test | O roteamento e a política SNS são verificados junto da alteração Terraform | OK |
| T34 | Lambda de corte | unit/terraform test | O comportamento idempotente e as permissões são verificados junto da Lambda | OK |
| T35 | Lambda de corte | unit/terraform test | ARN e nome do cluster são distinguidos no teste da integração de autoscaling | OK |
| T36 | Administração RDS temporária | terraform test/validate/remote read | Rota, endpoint, TLS e `/32` são verificados junto da alteração Terraform | OK |
